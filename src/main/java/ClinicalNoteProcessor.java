package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClinicalNoteProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ClinicalNoteProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String inputDir;
    private final String outputDir;
    private final OllamaClient ollamaClient;
    private final JsonSchema schema;
    private final int numThreads;

    public ClinicalNoteProcessor() throws IOException {
        Properties config = ConfigLoader.loadConfig();

        this.inputDir = config.getProperty("input.dir");
        this.outputDir = config.getProperty("output.dir");
        String ollamaModel = config.getProperty("ollama.model");

        // Load Ollama client settings from properties
        String ollamaApiUrl = config.getProperty("ollama.api.url");
        int maxRetries = Integer.parseInt(config.getProperty("ollama.client.maxRetries"));
        long retryDelayMs = Long.parseLong(config.getProperty("ollama.client.retryDelayMs"));
        long requestTimeoutMinutes = Long.parseLong(config.getProperty("ollama.client.requestTimeoutMinutes"));
        this.numThreads = Integer.parseInt(config.getProperty("processing.numThreads", "10"));

        this.ollamaClient = new OllamaClient(ollamaModel, ollamaApiUrl, maxRetries, retryDelayMs, requestTimeoutMinutes);

        // Load the JSON schema for validation
        try (InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("output_schema.json")) {
            if (schemaStream == null) throw new IOException("Cannot find 'output_schema.json' in resources.");
            JsonNode schemaNode = objectMapper.readTree(schemaStream);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            this.schema = factory.getSchema(schemaNode);
        }
    }

    public static void main(String[] args) {
        // Bridge java.util.logging (used by PDFBox) to SLF4J
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            ClinicalNoteProcessor processor = new ClinicalNoteProcessor();
            processor.run();
        } catch (Exception e) {
            logger.error("Application failed to start or run.", e);
            System.exit(1);
        }
    }

    public void run() throws IOException, InterruptedException {
        Path inputPath = Paths.get(this.inputDir);
        Path outputPath = Paths.get(this.outputDir);

        if (!Files.exists(inputPath)) {
            logger.error("Input directory '{}' not found. Please create it and add your .txt or .pdf files.", this.inputDir);
            Files.createDirectories(inputPath); // Create it so the user can add files
            return;
        }
        Files.createDirectories(outputPath);

        logger.info("Searching for .txt and .pdf files in '{}'...", inputPath.toAbsolutePath());
        List<Path> allFiles;
        try (var pathStream = Files.walk(inputPath)) {
            allFiles = pathStream.filter(Files::isRegularFile)
                                 .filter(path -> path.toString().toLowerCase().endsWith(".txt") || path.toString().toLowerCase().endsWith(".pdf"))
                                 .collect(Collectors.toList());
        }

        if (allFiles.isEmpty()) {
            logger.warn("No .txt or .pdf files found in the '{}' directory.", this.inputDir);
            return;
        }

        // --- Concurrency Improvement ---
        logger.info("Initializing thread pool with {} threads to process {} files...", this.numThreads, allFiles.size());
        final AtomicInteger processedCount = new AtomicInteger(0);
        final int totalFiles = allFiles.size();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (Path filePath : allFiles) {
            executor.submit(() -> processFile(filePath, outputPath, processedCount, totalFiles));
        }

        // --- Graceful Shutdown ---
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                logger.error("Processing timed out. Forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Processing was interrupted. Forcing shutdown.", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("All files have been processed.");
    }

    private void processFile(Path inputFilePath, Path outputDir, AtomicInteger processedCount, int totalFiles) {
        final String fileName = inputFilePath.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String outputFileName = baseName + "_terms.json";
        Path outputFilePath = outputDir.resolve(outputFileName);

        try {
            // --- PERFORMANCE IMPROVEMENT: Skip already processed files ---
            if (Files.exists(outputFilePath)) {
                long inputFileModifiedTime = Files.getLastModifiedTime(inputFilePath).toMillis();
                long outputFileModifiedTime = Files.getLastModifiedTime(outputFilePath).toMillis();
                if (inputFileModifiedTime < outputFileModifiedTime) {
                    logger.debug("Skipping '{}' as an up-to-date output file already exists.", fileName);
                    return; // Skip processing, finally block will still run to update progress.
                }
            }

            String content;
            if (fileName.toLowerCase().endsWith(".txt")) {
                content = Files.readString(inputFilePath);
            } else if (fileName.toLowerCase().endsWith(".pdf")) {
                content = extractTextFromPdf(inputFilePath);
            } else {
                logger.warn("Unsupported file type, skipping: {}", fileName);
                content = null;
            }

            if (content != null && !content.isBlank()) {
                String systemPrompt = createSystemPrompt();
                // The LLM now returns a raw string, which we must parse.
                String llmResponse = ollamaClient.extractTerms(systemPrompt, content);
                JsonNode flatTermArray = parseTextResponse(llmResponse);
                // We transform this flat array into the required structured JSON
                JsonNode structuredJson = buildStructuredJson(flatTermArray);
                if (validateJsonOutput(structuredJson, fileName)) {
                    saveOutput(structuredJson, outputFilePath);
                }
            } else if (content != null) {
                logger.warn("File is empty, skipping: {}", fileName);
            }

        } catch (IOException e) {
            logger.error("Error processing file {}: {}", fileName, e.getMessage(), e);
        } finally {
            int count = processedCount.incrementAndGet();
            printProgressBar(count, totalFiles);
        }
    }

    private String extractTextFromPdf(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                logger.warn("PDF file is encrypted and cannot be processed: {}", pdfPath);
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private synchronized void printProgressBar(int current, int total) {
        // This is fine to keep as System.out for a dynamic progress bar
        int percent = (int) (((double) current / total) * 100);
        int width = 50;
        int progress = (int) (((double) current / total) * width);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < progress) {
                bar.append("=");
            } else {
                bar.append(" ");
            }
        }
        bar.append("] " + percent + "% (" + current + "/" + total + ")");
        System.out.print("\r" + bar.toString());
    }

    /**
     * Parses the simple, line-by-line text response from the LLM into a flat JSON array.
     * @param textResponse The raw string from the LLM.
     * @return A JsonNode representing the flat array of terms.
     */
    private JsonNode parseTextResponse(String textResponse) {
        ArrayNode flatTermArray = objectMapper.createArrayNode();
        if (textResponse == null || textResponse.isBlank()) {
            logger.warn("Received empty or null response from LLM.");
            return flatTermArray;
        }

        // Split by any kind of newline to handle different OS conventions
        String[] lines = textResponse.split("\\R");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int separatorIndex = line.indexOf(':');
            if (separatorIndex == -1) {
                logger.warn("Could not parse line from LLM (no ':' separator): '{}'", line);
                continue;
            }

            String categoryKey = line.substring(0, separatorIndex).trim().toUpperCase();

            // Silently ignore conversational notes from the LLM.
            if ("NOTE".equals(categoryKey)) {
                continue;
            }

            String termText = line.substring(separatorIndex + 1).trim();

            if (termText.isEmpty()) continue;

            String jsonCategory = mapLlmCategoryToJsonCategory(categoryKey);
            if (jsonCategory == null) {
                logger.warn("Unknown category key from LLM: '{}' in line: '{}'", categoryKey, line);
                continue;
            }

            ObjectNode termNode = objectMapper.createObjectNode();
            termNode.put("category", jsonCategory);
            termNode.put("term_text", termText);
            // Medication detail parsing could be added here in the future if needed

            flatTermArray.add(termNode);
        }
        return flatTermArray;
    }

    private boolean termLooksLikeDiagnosis(String termText) {
        String lowerTerm = termText.toLowerCase();
        // A simple heuristic: if a term describes a common symptom or condition,
        // it should be treated as a diagnosis even if the LLM classifies it as a general finding.
        // This makes the system more robust to LLM inconsistencies.
        return lowerTerm.contains("pain") ||
               lowerTerm.contains("strain") ||
               lowerTerm.contains("sprain") ||
               lowerTerm.contains("fracture") ||
               lowerTerm.contains("headache") ||
               lowerTerm.contains("nausea") ||
               lowerTerm.contains("dizziness") ||
               lowerTerm.contains("dyspnea") || // medical term for shortness of breath
               lowerTerm.contains("shortness of breath") ||
               lowerTerm.contains("fatigue") ||
               lowerTerm.contains("edema") ||
               lowerTerm.contains("murmur") || // A heart murmur is a clinical finding but often treated as a condition
               lowerTerm.contains("hypertension"); // High blood pressure
    }

    /**
     * Transforms the flat array of terms from the LLM into the final structured JSON.
     * This method ensures the final output matches the required schema, while allowing
     * the LLM to perform a much simpler and faster task.
     *
     * @param flatTermArray The JsonNode representing the array of terms from the LLM.
     * @return A JsonNode representing the final, structured JSON object.
     */
    private JsonNode buildStructuredJson(JsonNode flatTermArray) {
        Set<String> seenTerms = new HashSet<>();

        ObjectNode root = objectMapper.createObjectNode();
        // Initialize all required categories with empty arrays to ensure they always exist
        ArrayNode diagnoses = root.putArray("diagnoses");
        ArrayNode procedures = root.putArray("procedures");
        ArrayNode medications = root.putArray("medications_and_treatments");
        ArrayNode tests = root.putArray("tests");
        ArrayNode narrative = root.putArray("clinical_narrative");

        if (flatTermArray == null || !flatTermArray.isArray()) {
            logger.warn("Parser did not produce a valid JSON array. Returning empty structure.");
            return root;
        }

        for (JsonNode termNode : flatTermArray) {
            String category = termNode.path("category").asText("");
            String termText = termNode.path("term_text").asText("").trim();

            // Skip empty, placeholder, or already seen terms
            if (termText.isBlank() || "none".equalsIgnoreCase(termText) || "none mentioned".equalsIgnoreCase(termText)) {
                continue;
            }
            String uniqueKey = category + ":" + termText.toLowerCase();
            if (!seenTerms.add(uniqueKey)) {
                logger.debug("Skipping duplicate term: '{}' in category '{}'", termText, category);
                continue;
            }

            // --- NEW LOGIC: Re-categorize common conditions that the LLM might label as a 'FINDING' ---
            if ("clinical_narrative".equals(category) && termLooksLikeDiagnosis(termText)) {
                logger.debug("Re-categorizing term '{}' from clinical_narrative to diagnoses.", termText);
                category = "diagnoses"; // Re-assign the category to ensure it's treated as a condition.
            }

            ObjectNode structuredTerm = objectMapper.createObjectNode();
            structuredTerm.put("term_text", termText);
            structuredTerm.put("omop_domain", mapCategoryToOmopDomain(category, termText));

            // If the term is a medication and has details, copy them over
            if ("medications_and_treatments".equals(category) && termNode.has("details")) {
                structuredTerm.set("details", termNode.get("details"));
            }

            switch (category) {
                case "diagnoses": diagnoses.add(structuredTerm); break;
                case "procedures": procedures.add(structuredTerm); break;
                case "medications_and_treatments": medications.add(structuredTerm); break;
                case "tests": tests.add(structuredTerm); break;
                case "clinical_narrative": narrative.add(structuredTerm); break;
                default:
                    logger.warn("Unknown category '{}' found for term: {}", category, termText);
                    break;
            }
        }
        return root;
    }

    private String mapLlmCategoryToJsonCategory(String llmCategory) {
        // Maps the simple category key from the LLM prompt to the key used in our JSON structure.
        // This is made more flexible to handle common variations from the LLM.
        switch (llmCategory) {
            // Standard Categories
            case "DIAGNOSIS":
            case "ASSESSMENT": // Assessments often list diagnoses
            case "ALLERGY":
            case "ALLERGIES":
                return "diagnoses";

            case "PROCEDURE":
            case "PLAN": // Plans often contain procedures
            case "SPECIALIST REFERRALS": // From A&P section
                return "procedures";

            case "MEDICATION":
            case "MEDICATIONS":
            case "MEDICAL TREATMENT": // From A&P section
                return "medications_and_treatments";

            case "TEST":
            case "RESULTS": // Results sections contain tests
            case "VITALS": // Vitals are a type of test/measurement
            case "ADDITIONAL TESTING": // From A&P section
                return "tests";

            case "NARRATIVE":
            case "FINDING": // A more concrete term for the LLM than "NARRATIVE"
            case "HISTORY OF PRESENT ILLNESS":
            case "REVIEW OF SYSTEMS":
            case "PHYSICAL EXAM":
            case "EXAM": // Short version of PHYSICAL EXAM
            case "FAMILY HISTORY":
            // Add sub-headers from physical exams and review of systems
            case "CONSTITUTIONAL":
            case "CARDIOVASCULAR":
            case "MUSCULOSKELETAL":
            case "MEDICAL REASONING": // From A&P section
                return "clinical_narrative";

            default:
                return null;
        }
    }

    private boolean termIsMeasurement(String termText) {
        // A term is considered a measurement if it contains a numeric value.
        // Qualitative descriptions like "elevated" or "normal" are observations.
        // This regex checks for the presence of any digit.
        return termText.matches(".*\\d.*");
    }

    private String mapCategoryToOmopDomain(String category, String termText) {
        // This helper method translates the simple category from the LLM to the specific OMOP domain.
        String lowerTerm = termText.toLowerCase();
        switch (category) {
            case "diagnoses": return "condition_occurrence";
            case "procedures": return "procedure_occurrence";
            case "medications_and_treatments":
                // A simple heuristic to distinguish drugs from devices. This can be expanded.
                if (lowerTerm.contains("splint") || lowerTerm.contains("cream") || lowerTerm.contains("gel")) {
                    return "device_exposure";
                }
                return "drug_exposure";
            case "tests": // Both tests and narrative can contain measurements or observations
            case "clinical_narrative":
                return termIsMeasurement(lowerTerm) ? "measurement" : "observation";
            default:
                return "observation";
        }
    }

    private boolean validateJsonOutput(JsonNode jsonNode, String fileName) {
        if (jsonNode == null) {
            logger.warn("No JSON output returned for {}, skipping validation.", fileName);
            return false; // No data to validate
        }
        Set<ValidationMessage> errors = this.schema.validate(jsonNode);
        if (errors.isEmpty()) {
            return true;
        } else {
            logger.error("JSON validation failed for {}. Reasons:", fileName);
            errors.forEach(error -> logger.error("  - {}", error.getMessage()));
            try {
                logger.error("Invalid JSON content:\n{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
            } catch (IOException e) {
                logger.error("Could not serialize invalid JSON for logging.");
            }
            return false;
        }
    }

    private void saveOutput(JsonNode jsonOutput, Path outputFilePath) {
        try {
            // Use Jackson's pretty printer to write the file
            Files.writeString(outputFilePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput));
            logger.debug("✅ Successfully processed and saved {}", outputFilePath.getFileName());
        } catch (IOException e) {
            logger.error("Error writing file to {}", outputFilePath, e);
        }
    }

    private String createSystemPrompt() {
        return """
        You are an expert medical data extractor. Your task is to read the following medical note and extract every clinical term.
        For each term, write it on a new line, prefixed with one of the following categories: DIAGNOSIS, PROCEDURE, MEDICATION, TEST, FINDING.
        Your entire output must be only these lines. Do not add any other explanation or text.

        **EXAMPLE**

        *Input Text:*
        "HPI: 57-year-old male with congestive heart failure and type 2 diabetes, presents with back pain after lifting a heavy box. He reports taking some Tylenol and ibuprofen.
        EXAM: Grade 2/6 systolic ejection murmur.
        RESULTS: X-ray lumbar spine is unremarkable. Hemoglobin A1c is elevated at 8.
        ASSESSMENT: Acute lumbar strain.
        PLAN: Initiate meloxicam 15 mg once daily. Refer to physical therapy."

        *Correct Output:*
        DIAGNOSIS: congestive heart failure
        DIAGNOSIS: type 2 diabetes
        DIAGNOSIS: back pain
        FINDING: lifting a heavy box
        MEDICATION: Tylenol
        MEDICATION: ibuprofen
        FINDING: Grade 2/6 systolic ejection murmur
        TEST: X-ray lumbar spine is unremarkable
        TEST: Hemoglobin A1c is elevated at 8
        DIAGNOSIS: Acute lumbar strain
        MEDICATION: meloxicam 15 mg once daily
        PROCEDURE: Refer to physical therapy

        **Final Instruction**: Now, apply these exact rules to the user-provided note.
        """;
    }
}
