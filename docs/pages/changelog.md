---
title: Changelog
slug: /changelog
custom_edit_url: https://github.com/Mr3zee/Library-API-Watchdog/edit/main/CHANGELOG.md
mdx:
  format: md
---

<!-- Generated from CHANGELOG.md. Run npm run sync:changelog from docs/. -->

# Changelog

## Unreleased

### Changed

- [Inconsistent parameter order in overloads](checks/inconsistent-parameter-order-in-overloads.md):
  a last parameter that accepts a trailing lambda can stay last in each overload. Such a parameter has a
  function type, a `fun interface` type, or a Java SAM interface type.
- [Required parameters after optional ones](checks/required-parameter-after-optional.md):
  the trailing-lambda exception also applies to a Java SAM interface in the last position.

## 0.1.0 - 2026-11-05

### Added

- Public API checks for Kotlin libraries, with configurable severity and exemption annotations.
- Automatic exemptions for existing API with the `exemptExistingApi` Gradle task.
- HTML and SARIF reports, with a Gradle plugin to combine reports from several modules.
  `exemptExistingApi` writes the combined reports too.
- Documentation for each check and an API reference for the annotations and Gradle plugins.
- [`REDUNDANT_EXEMPTION`](checks/special/redundant-exemption.md): a warning on an `@Intentionally*` annotation that its check doesn't need.
  The report tags the annotation as redundant, and `exemptExistingApi` removes it.

<!-- Release notes generated using configuration in .github/release.yml at 661e3e033d2b89ca40a585ee471f7dac4cd15703 -->

### What's Changed
* Clean up by @kyay10 in https://github.com/Mr3zee/Library-API-Watchdog/pull/2
* Versioned dumps by @kyay10 in https://github.com/Mr3zee/Library-API-Watchdog/pull/3
* Restore Kotlin group by @kyay10 in https://github.com/Mr3zee/Library-API-Watchdog/pull/4
* Update dev kit and adopt kotlinx library API packages by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/5
* Re-enable 2.5.20 tests using newer devkit by @kyay10 in https://github.com/Mr3zee/Library-API-Watchdog/pull/10
* Remove `gradle-public-api` exclusion by @kyay10 in https://github.com/Mr3zee/Library-API-Watchdog/pull/9
* Move to dev kit 0.0.4-dev-334 and the template's composite build, and make Kotlin 2.4.0 the minimum by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/23

### New Contributors
* @kyay10 made their first contribution in https://github.com/Mr3zee/Library-API-Watchdog/pull/2

**Full Changelog**: https://github.com/Mr3zee/Library-API-Watchdog/commits/v0.1.0
