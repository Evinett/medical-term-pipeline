package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OmopIdUpdater {

    private static final Logger logger = LoggerFactory.getLogger(OmopIdUpdater.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DEFAULT_OMOP_ID = "0";

    public static void main(String[] args) {
        try {
            Properties config = ConfigLoader.loadConfig();
            String inputDir = config.getProperty("input.dir");
            String outputDir = config.getProperty("output.dir");

            if (inputDir == null || outputDir == null) {
                logger.error("Properties 'input.dir' and/or 'output.dir' not found in config.properties.");
                return;
            }

            // Use paths from config file
            Path conditionMapPath = Paths.get(inputDir, "conditions-map.txt");
            Path medicationMapPath = Paths.get(inputDir, "medications-map.txt");
            Path observationMapPath = Paths.get(inputDir, "observations-map.txt");
            Path outputDirPath = Paths.get(outputDir);

            Map<String, String> conditionMap = loadTermMap(conditionMapPath);
            Map<String, String> medicationMap = loadTermMap(medicationMapPath);
            Map<String, String> observationMap = loadTermMap(observationMapPath);

            List<Path> jsonFiles = findJsonFiles(outputDirPath);

            logger.info("Starting to update {} JSON files with OMOP IDs...", jsonFiles.size());

            // --- PERFORMANCE IMPROVEMENT: Process files in parallel ---
            long updatedCount = jsonFiles.parallelStream()
                    .filter(jsonFile -> updateJsonFile(jsonFile, conditionMap, medicationMap, observationMap))
                    .count();

            logger.info("Finished. Successfully updated {} of {} JSON files.", updatedCount, jsonFiles.size());
        } catch (IOException e) {
            logger.error("An error occurred during the OMOP ID update process.", e);
        }
    }

    private static Map<String, String> loadTermMap(Path mapFilePath) throws IOException {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(mapFilePath)) {
            logger.warn("Mapping file not found, skipping: {}", mapFilePath);
            return map; // Return an empty map if the file doesn't exist
        }
        try (Stream<String> lines = Files.lines(mapFilePath)) {
            lines.filter(line -> !line.isBlank() && line.contains("\t"))
                 .forEach(line -> {
                     String[] parts = line.split("\t", 2); // Always convert map keys to lowercase for consistent matching
                     String term = parts[0].trim().replace("\"", "").toLowerCase();
                     String id = parts[1].trim();
                     if (!term.isEmpty() && !id.isEmpty()) map.put(term, id);
                 });
        }
        logger.info("Loaded {} terms from {}", map.size(), mapFilePath.getFileName());
        return map;
    }

    private static List<Path> findJsonFiles(Path outputDir) throws IOException {
        try (Stream<Path> pathStream = Files.walk(outputDir)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith("_terms.json"))
                    .collect(Collectors.toList());
        }
    }

    private static boolean updateJsonFile(Path jsonFile, Map<String, String> conditionMap, Map<String, String> medicationMap, Map<String, String> observationMap) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonFile.toFile());
            JsonNode diagnosesNode = rootNode.path("diagnoses");

            if (diagnosesNode.isArray()) {
                diagnosesNode.forEach(node -> {
                    if (node.isObject()) {
                        ObjectNode diagnosisObject = (ObjectNode) node;
                        String termText = diagnosisObject.path("term_text").asText("").trim().toLowerCase();
                        String omopId = conditionMap.getOrDefault(termText, DEFAULT_OMOP_ID);
                        diagnosisObject.put("OMOP_ID", omopId);
                    }
                });
            }

            JsonNode medicationsNode = rootNode.path("medications_and_treatments");
            if (medicationsNode.isArray()) {
                medicationsNode.forEach(node -> {
                    if (node.isObject()) {
                        ObjectNode medObject = (ObjectNode) node;
                        // Prioritize looking up the specific drug_name, but fall back to the full term_text. Convert to lowercase for matching.
                        String termToLookUp = medObject.path("details").path("drug_name").asText(medObject.path("term_text").asText("")).trim().toLowerCase();
                        if (!termToLookUp.isEmpty()) {
                            String omopId = medicationMap.getOrDefault(termToLookUp, DEFAULT_OMOP_ID);
                            medObject.put("OMOP_ID", omopId);
                        }
                    }
                });
            }

            // Add OMOP IDs to tests (measurements and observations)
            JsonNode testsNode = rootNode.path("tests");
            if (testsNode.isArray()) {
                testsNode.forEach(node -> {
                    if (node.isObject()) {
                        ObjectNode testObject = (ObjectNode) node;
                        String termText = testObject.path("term_text").asText("").trim().toLowerCase();
                        String omopId = observationMap.getOrDefault(termText, DEFAULT_OMOP_ID);
                        testObject.put("OMOP_ID", omopId);
                    }
                });
            }

            // Add OMOP IDs to clinical narrative terms (observations and measurements)
            JsonNode narrativeNode = rootNode.path("clinical_narrative");
            if (narrativeNode.isArray()) {
                narrativeNode.forEach(node -> {
                    if (node.isObject()) {
                        ObjectNode narrativeObject = (ObjectNode) node;
                        String termText = narrativeObject.path("term_text").asText("").trim().toLowerCase();
                        String omopId = observationMap.getOrDefault(termText, DEFAULT_OMOP_ID);
                        narrativeObject.put("OMOP_ID", omopId);
                    }
                });
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), rootNode);
            logger.debug("Successfully updated {}", jsonFile.getFileName());
            return true;

        } catch (IOException e) {
            logger.warn("Failed to process file {}: {}", jsonFile.getFileName(), e.getMessage());
            return false;
        }
    }
}