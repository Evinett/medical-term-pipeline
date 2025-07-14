## Medical Term Extractor

This Java-based application leverages a local Large Language Model (LLM) via Ollama to perform sophisticated analysis of unstructured medical notes from both text and PDF files. It extracts, categorizes, and structures clinical information into a single, comprehensive JSON format.

## Features

- **Unified JSON Output**: For each input file (`.txt` or `.pdf`), the application generates a single, comprehensive JSON file in the `output/` directory.
- **Data Quality Assurance**: Ensures the reliability of extracted data by validating all LLM output against a strict JSON schema before saving.
- **Intelligent Post-Processing**:
    - **Resilient Parsing**: Intelligelligently handles inconsistent or malformed text from the LLM, recovering data where possible to ensure high processing success rates.
    - **Classification Refinement**: Applies a layer of business logic to correct common LLM misclassifications, such as ensuring symptoms like "back pain" are always categorized as conditions.
- **Rich Data Structure**: The output JSON categorizes information into diagnoses, procedures, medications, etc. Each extracted concept is enriched with:
    - The original text (`term_text`).
    - The corresponding OMOP CDM domain (`omop_domain`).
    - An `OMOP_ID` for standardized concept mapping (added via the `OmopIdUpdater` utility).
- **Deep Contextual Understanding**: Goes beyond simple keyword extraction to understand relationships, such as linking a treatment (`Aspirin`) to a clinical intent (`DVT prophylaxis`).
- **Robust and Performant**:
    - Processes multiple files in parallel using a configurable thread pool.
    - **Incremental Processing**: Avoids re-processing unchanged files, dramatically speeding up subsequent runs.
    - Includes a configurable retry mechanism for API calls to handle transient network issues.
    - Recursively finds all `.txt` and `.pdf` files within the input directory.
- **Clean Logging**: Uses a logging bridge to centralize all application and third-party library logs, providing a clean console experience during runs.

## Project Structure

```plaintext
.
├── input/
│   ├── conditions-map.txt
│   └── note1.txt
├── output/
│   └── note1_terms.json
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           ├── ClinicalNoteProcessor.java
│       │           ├── OllamaClient.java
│       │           ├── ConditionSearcher.java
│       │           └── OmopIdUpdater.java
│       └── resources/
│           ├── config.properties
│           ├── logback.xml
│           └── output_schema.json
├── pom.xml
└── README.md
```

## Prerequisites

- Java JDK 15 or higher
- Ollama installed and running.
- A downloaded Ollama model (the code is configured for `llama3`, but can be changed).

To pull the model:
```sh
ollama pull llama3
```

## How to Run

1.  **Place Files**: Add your medical notes as `.txt` or `.pdf` files inside the `input` directory.
2.  **Configure**: Edit `src/main/resources/config.properties` to set your desired model and performance settings.
3.  **Compile and Run**: Use your IDE or Maven to run the `ClinicalNoteProcessor` class.

 Using Maven:
 ```sh
 mvn compile exec:java
 ```
4.  **Check Output**: The processed JSON files will be created in the `output` directory.

## Configuration

Configuration is managed in the `src/main/resources/config.properties` file. You can modify the following properties:

-   `input.dir`: The directory containing input `.txt` and `.pdf` files.
-   `output.dir`: The directory where JSON output will be saved.
-   `ollama.model`: The name of the Ollama model to use (e.g., "llama3").
-   `ollama.api.url`: The full URL for the Ollama generate endpoint.
-   `ollama.client.maxRetries`: The number of times to retry a failed API call.
-   `ollama.client.retryDelayMs`: The delay in milliseconds between retries.
-   `ollama.client.requestTimeoutMinutes`: The timeout for API requests to Ollama.
-   `processing.numThreads`: The number of concurrent files to process.



## How to Run

1.  **Place Files**: Add your medical notes as `.txt` or `.pdf` files inside the `input` directory.
2.  **Configure**: Edit `src/main/resources/config.properties` to set your desired model and performance settings.
3.  **Compile and Run**: Use your IDE or Maven to run the `ClinicalNoteProcessor` class.

 Using Maven:
 ```sh
 mvn compile exec:java
