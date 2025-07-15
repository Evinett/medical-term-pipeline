# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Created `CHANGELOG.md` to track project changes.
- Integrated OMOP ID mapping directly into `ClinicalNoteProcessor` to streamline the data processing pipeline.
- Implemented a flexible JSON schema (`output_schema.json`) that allows `clinical_narrative` terms to exist without an `omop_domain`.

### Changed
- The `omop_domain` field is no longer added to terms in the `clinical_narrative` category, aligning with the updated schema.

### Removed
- Deleted the redundant `OmopIdUpdater.java` file, as its functionality is now part of `ClinicalNoteProcessor`.

### Fixed
- Resolved JSON validation failures by modifying the schema instead of using placeholder values in the Java code.
- Corrected a Java compilation error caused by a misplaced method outside the main class definition.