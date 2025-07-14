# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog,
and this project adheres to Semantic Versioning.

## [0.6.0] - 2025-07-13

### Added
- **OMOP ID Enrichment**: The `OmopIdUpdater` utility now enriches `observation` and `measurement` terms with OMOP Concept IDs, using a new `input/observations-map.txt` file.

### Changed
- **Classification Logic**: Significantly improved the accuracy of data classification with several enhancements:
    - **Measurement vs. Observation**: A term is now only classified as a `measurement` if it contains a numeric value. Qualitative descriptions like "elevated" are now correctly classified as `observation`.
    - **Symptom Re-classification**: Added robust post-processing logic to automatically re-classify common symptoms and clinical findings (e.g., "back pain", "murmur") as `diagnoses`, correcting for potential LLM inconsistencies.
    - **Prompt Improvement**: Replaced the ambiguous `NARRATIVE` category in the LLM prompt with the more concrete and standard `FINDING` category to improve extraction reliability.
- **Performance**:
    - The `OmopIdUpdater` now processes files in parallel, significantly speeding up the enrichment process.
    - The main `ClinicalNoteProcessor` now performs incremental processing, skipping files that have already been processed and are up-to-date.

### Fixed
- Corrected a recurring issue where clinical findings like "systolic ejection murmur" were being dropped from the output due to ambiguous prompt instructions.

## [0.5.0] - 2025-07-07

### Changed
- **Prompt Simplification**: Simplified the LLM prompt to request a flat list of terms, offloading the complex task of structuring the final JSON to more reliable Java code.

## [0.4.0] - 2025-07-06

### Added
- **JSON Schema Validation**: Implemented robust validation of the LLM's JSON output against a strict schema to ensure data quality and consistency.
- **Logging Bridge**: Added `jul-to-slf4j` to bridge logs from third-party libraries like PDFBox, allowing for centralized logging control and a cleaner console output.

### Changed
- **JSON Library Standardization**: Migrated the entire project from `org.json` to the Jackson `databind` library for all JSON processing.
- **JSON Schema Validator**: Replaced the `everit-org` schema validator with the `networknt` validator to resolve dependency issues and align with the Jackson library.
- **Code Structure**: Refactored `ClinicalNoteProcessor` from a static-based class to an instance-based class to improve testability and robustness.
- **Prompt Hardening**: Significantly improved the LLM prompt with more explicit constraints, negative examples, and specific rules to increase the reliability and consistency of the JSON output.
- **Configuration**: Externalized more settings, like the Ollama request timeout, to `config.properties`.

## [0.3.0] - 2025-06-30

### Removed
- **Standard Code Lookups**: Removed the `standard_code` field from the JSON output and prompts. The LLM is no longer tasked with mapping terms to standard terminologies (SNOMED-CT, RxNorm, etc.) to improve accuracy and performance.

### Changed
- Simplified LLM prompt to focus solely on term extraction and OMOP domain mapping.

## [0.2.0] - 2025-06-30

### Added
- **Unified Output Strategy**: Replaced the dual-file output with a single, comprehensive JSON file. This significantly improved performance by halving the number of API calls.
- **Retry Logic**: Added a retry mechanism to the API call to handle transient network errors.
- **Progress Bar**: Implemented a console progress bar for better user feedback during long-running jobs.

### Changed
- **Prompt Engineering**: Iteratively improved the LLM prompt to increase extraction accuracy.

## [0.1.0] - 2025-06-30

### Added
- Initial version with dual JSON output (`detailed` and `OMOP_Ready`).
- Parallel file processing using a thread pool.

### Changed
- Migrated from a simple text file output to structured JSON.