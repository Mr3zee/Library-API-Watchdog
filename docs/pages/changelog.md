---
title: Changelog
slug: /changelog
description: The changes of each release.
custom_edit_url: https://github.com/Mr3zee/Library-API-Watchdog/edit/main/CHANGELOG.md
mdx:
  format: md
---

<!-- Generated from CHANGELOG.md. Run npm run sync:changelog from docs/. -->

# Changelog

## Unreleased

### Added

- [API filters](exemptions.md#api-filters): the watchdog applies the
  filters of the binary compatibility validation of the project.

### Changed

- The filters of an existing validation setup can add errors of
  [Internal API types in public API](checks/special/public-type-with-internal-api.md)
  and warnings of [Redundant exemptions](checks/special/redundant-exemption.md).
  `apiWatchdog { useAbiValidationFilters = false }` turns the filters off.
- The check "Public types marked as internal API" has the new title "Internal API types in public API".

## 0.2.0 - 2026-11-06

<!-- Release notes generated using configuration in .github/release.yml at 20a11d69284725b68705ed22ee65497b1f61bdd9 -->

### What's Changed
* Update the dev kit to 0.0.4-dev-356 by @github-actions[bot] in https://github.com/Mr3zee/Library-API-Watchdog/pull/27
* Open the bot pull requests with a GitHub App token by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/28
* Update Gradle plugins, AGP, and the docs tooltip, and ignore Kotlin in Renovate by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/30
* Run the benchmark baseline check in one JVM by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/29
* Move the changelog after the checks in the docs sidebar by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/32
* Write llms.txt and a Markdown copy of each docs page by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/33
* Add the backwards compatibility exemption of [PUBLIC_TYPE_WITH_INTERNAL_API](checks/special/public-type-with-internal-api.md) by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/31
* Link diagnostic names in the website changelog by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/35


**Full Changelog**: https://github.com/Mr3zee/Library-API-Watchdog/compare/v0.1.1...v0.2.0

## 0.1.1 - 2026-11-05

### Changed

- [Inconsistent parameter order in overloads](checks/inconsistent-parameter-order-in-overloads.md):
  a last parameter that accepts a trailing lambda can stay last in each overload. Such a parameter has a
  function type, a `fun interface` type, or a Java SAM interface type.
- [Required parameters after optional ones](checks/required-parameter-after-optional.md):
  the trailing-lambda exception also applies to a Java SAM interface in the last position.

<!-- Release notes generated using configuration in .github/release.yml at 2a8e491056a220fcc662ac583fd94b32f526911d -->

### What's Changed
* Sign the commits of the Release and Dev kit update workflows by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/25

### New Contributors
* @github-actions[bot] made their first contribution in https://github.com/Mr3zee/Library-API-Watchdog/pull/24

**Full Changelog**: https://github.com/Mr3zee/Library-API-Watchdog/compare/v0.1.0...v0.1.1

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
