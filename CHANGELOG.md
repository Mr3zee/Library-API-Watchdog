# Changelog

## Unreleased

### Added

- Public API checks for Kotlin libraries, with configurable severity and exemption annotations.
- Automatic exemptions for existing API with the `exemptExistingApi` Gradle task.
- HTML and SARIF reports, with a Gradle plugin to combine reports from several modules.
  `exemptExistingApi` writes the combined reports too.
- Documentation for each check and an API reference for the annotations and Gradle plugins.
- `REDUNDANT_EXEMPTION`: a warning on an `@Intentionally*` annotation that its check doesn't need.
  The report tags the annotation as redundant, and `exemptExistingApi` removes it.
