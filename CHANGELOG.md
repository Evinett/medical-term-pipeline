# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Added MIT License information to `pom.xml`, `README.md`, and all Java source file headers for clarity.
- Externalized the LLM system prompt into `src/main/resources/system-prompt.txt` to allow for easier modification without recompiling the code.
- Added `medications-map.txt` and `procedures-map.txt` to provide OMOP concept IDs for common medications and procedures.
- Created `CHANGELOG.md` to track project changes.
- Integrated OMOP ID mapping directly into `ClinicalNoteProcessor` to streamline the data processing pipeline.
- Implemented a flexible JSON schema (`output_schema.json`) that allows `clinical_narrative` terms to exist without an `omop_domain`.
- Added `procedureMap` and logic to only include valid OMOP procedures in the `procedures` category, moving referrals to `clinical_narrative` and excluding non-clinical activities.
- Mapped the LLM category `HISTORY` to `clinical_narrative` to prevent warnings and ensure correct categorization.

### Changed
- The `omop_domain` field is no longer added to terms in the `clinical_narrative` category, aligning with the updated schema.
- Replaced `maven-assembly-plugin` with `maven-shade-plugin` to create the executable JAR, which resolves build warnings and is the modern standard for this task.
- The final executable JAR name is now simplified to `medical-term-extractor-1.0.jar` and the `SNAPSHOT` version has been removed.
- Improved concurrency and file skipping logic in `ClinicalNoteProcessor` for better performance.

### Removed
- Deleted the redundant `OmopIdUpdater.java` file, as its functionality is now part of `ClinicalNoteProcessor`.
- Removed the obsolete `update-omop-ids` profile from `pom.xml`.

### Fixed
- Resolved JSON validation failures by modifying the schema instead of using placeholder values in the Java code.
- Corrected a Java compilation error caused by a misplaced method outside the main class definition.
- Fixed warnings for unknown LLM categories by mapping `HISTORY` to `clinical_narrative`.
- Aligned copyright information in the `LICENSE` file with the source code headers for consistency.
- Corrected the copyright year from 2026 to 2025 in all relevant project files.
- Fully resolved all `maven-shade-plugin` warnings by adding and refining resource transformers and filters to correctly merge all dependency metadata, licenses, and service files.
- Ensured only valid OMOP procedures are included in output, with referrals and non-clinical activities handled appropriately.