package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TermSearcher {

    private static final Logger logger = LoggerFactory.getLogger(TermSearcher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        // Get search category from system property, default to "diagnoses"
        String searchCategory = System.getProperty("search.category", "diagnoses");
        String outputFileName = System.getProperty("search.outputFile", searchCategory + ".txt");

        try {
            Properties config = ConfigLoader.loadConfig();
            String outputDir = config.getProperty("output.dir");
            if (outputDir == null || outputDir.isBlank()) {
                logger.error("Property 'output.dir' not found in config.properties.");
                return;
            }

            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                logger.error("Output directory '{}' does not exist.", outputDir);
                return;
            }

            logger.info("Searching for terms in category: '{}'", searchCategory);
            List<String> allTerms = findTerms(outputPath, searchCategory);

            if (allTerms.isEmpty()) {
                logger.info("No terms found for category '{}' in any JSON files.", searchCategory);
                return;
            }

            Path resultsFilePath = outputPath.resolve(outputFileName);
            Files.write(resultsFilePath, allTerms);
            logger.info("Successfully wrote {} terms to {}", allTerms.size(), resultsFilePath.toAbsolutePath());

        } catch (IOException e) {
            logger.error("An error occurred during the term search process.", e);
        }
    }

    private static List<String> findTerms(Path outputDir, String category) throws IOException {
        try (var pathStream = Files.walk(outputDir)) {
            List<Path> jsonFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith("_terms.json"))
                    .collect(Collectors.toList());

            logger.info("Found {} JSON files to search in '{}'.", jsonFiles.size(), outputDir.toAbsolutePath());

            return jsonFiles.parallelStream()
                    .flatMap(jsonFile -> extractTermsFromFile(jsonFile, category))
                    .collect(Collectors.toList());
        }
    }

    private static Stream<String> extractTermsFromFile(Path jsonFile, String category) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonFile.toFile());
            JsonNode categoryNode = rootNode.path(category);
            return StreamSupport.stream(categoryNode.spliterator(), false)
                    .map(node -> node.path("term_text").asText());
        } catch (IOException e) {
            logger.warn("Could not read or parse JSON file, skipping: {}", jsonFile, e);
            return Stream.empty();
        }
    }
}