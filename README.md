# Medical Term Extraction Pipeline

This project is a Java-based pipeline designed to extract structured clinical terms from unstructured medical notes. It leverages a Large Language Model (LLM) via Ollama to identify, categorize, and map medical concepts to the OMOP Common Data Model.

## Features

- **Intelligent Term Extraction**: Uses an LLM to find diagnoses, procedures, medications, and tests in raw text.
- **Multi-Format Support**: Processes both plain text (`.txt`) and PDF (`.pdf`) files.
- **Structured Output**: Converts extracted terms into a clean, validated JSON format.
- **OMOP Integration**: Automatically maps extracted terms to OMOP Concept IDs using provided mapping files.
- **Schema Validation**: Ensures all generated JSON files conform to a strict output schema.
- **Concurrent Processing**: Utilizes a thread pool to process multiple files in parallel for improved performance.

---

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java**: JDK 15 or higher
- **Maven**: Apache Maven 3.6+
- **Ollama**: A running instance of Ollama with a downloaded model (e.g., `llama3`, `mistral`).

---

## Setup

1.  **Clone the Repository**
    ```bash
    git clone <your-repository-url>
    cd medical-term-pipeline
    ```

2.  **Create Configuration File**
    Create a file named `config.properties` in the project's root directory and populate it with your settings.

    **`config.properties` Template:**
    ```properties
    # Directory for input clinical notes and mapping files
    input.dir=input

    # Directory for processed JSON output
    output.dir=output

    # Ollama model to use for term extraction
    ollama.model=llama3

    # Ollama API endpoint
    ollama.api.url=http://localhost:11434/api/chat

    # Ollama client settings
    ollama.client.maxRetries=3
    ollama.client.retryDelayMs=2000
    ollama.client.requestTimeoutMinutes=5

    # Number of concurrent threads for processing files
    processing.numThreads=10
    ```

3.  **Prepare Input Files**
    - Place your clinical notes (`.txt` or `.pdf`) inside the `input/` directory.
    - Ensure your OMOP mapping files (`conditions-map.txt`, `medications-map.txt`, `observations-map.txt`) are located in the `src/main/resources` directory of the project.  These files are accessed as classpath resources.



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
│       │           └── TermSearcher.java
│       └── resources/
│           ├── config.properties
│           ├── logback.xml
│           └── output_schema.json
├── pom.xml
└── README.md
```





---

## Usage

Run the pipeline from the project's root directory using the following Maven command:

```bash
mvn clean compile exec:java
```

The application will process all valid files in the `input/` directory and save the resulting `_terms.json` files to the `output/` directory. A progress bar will be displayed in the console.

## Next Steps
	•	The mapping files included in this repository are minimal examples, intended primarily for demonstration purposes. They contain only a small number of concepts.
	•	The clinical_narrative section is functional but requires further refinement to better distinguish between structured clinical concepts and unstructured narrative text.

    •	Integrate database connectors.
	•	Integrate Google Chat messaging functionality. 
        Example: 
Processing Processor.main()] INFO  com.example.ClinicalNoteProcessor - All 67 files have been processed.
[INFO] Total time:  08:42 min
[INFO] Finished at: 2025-07-15T22:52:29+10:00
[INFO] ------------------------------------------------------------------------