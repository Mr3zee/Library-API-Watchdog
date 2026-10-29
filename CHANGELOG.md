# Changelog

## Unreleased

### Added

- Public API checks for Kotlin libraries, with configurable severity and exemption annotations.
- Automatic exemptions for existing API with the `exemptExistingApi` Gradle task.
- HTML and SARIF reports, with a Gradle plugin to combine reports from several modules.
- Documentation for each check and an API reference for the annotations and Gradle plugins.
- `REDUNDANT_EXEMPTION`: a warning for an `@Intentionally*` annotation is not needed. The
  report tags the annotation as redundant, and `exemptExistingApi` removes it.
