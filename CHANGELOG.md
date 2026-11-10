# Changelog

## Unreleased

### Added

- [API filters](https://mr3zee.github.io/Library-API-Watchdog/exemptions#api-filters): the watchdog doesn't
  check a declaration that the binary compatibility validation leaves out of the ABI dump. It copies the
  filters of the ABI validation of the Kotlin Gradle plugin or of the Binary Compatibility Validator plugin
  while the tool validates the project. When both tools validate the project, the watchdog uses the filters
  of the Kotlin Gradle plugin only.

### Changed

- The filters of an existing validation setup apply after the upgrade, so a build that passed can fail:
  - [Internal API types in public API](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-with-internal-api)
    reports a checked declaration that exposes a type of an exclusion filter, such as a type of
    `ignoredPackages` or a type with a `nonPublicMarkers` annotation.
  - [Redundant exemptions](https://mr3zee.github.io/Library-API-Watchdog/checks/special/redundant-exemption)
    reports an `@Intentionally*` annotation on a declaration that the filters remove from the checks.
    `exemptExistingApi` removes such an annotation.

  To keep the previous behavior, set `apiWatchdog { useAbiValidationFilters = false }`.
- The check "Public types marked as internal API" has the new title "Internal API types in public API". The
  diagnostic name and the page address don't change.

## 0.2.0 - 2026-11-06

<!-- Release notes generated using configuration in .github/release.yml at 20a11d69284725b68705ed22ee65497b1f61bdd9 -->

### What's Changed
* Update the dev kit to 0.0.4-dev-356 by @github-actions[bot] in https://github.com/Mr3zee/Library-API-Watchdog/pull/27
* Open the bot pull requests with a GitHub App token by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/28
* Update Gradle plugins, AGP, and the docs tooltip, and ignore Kotlin in Renovate by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/30
* Run the benchmark baseline check in one JVM by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/29
* Move the changelog after the checks in the docs sidebar by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/32
* Write llms.txt and a Markdown copy of each docs page by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/33
* Add the backwards compatibility exemption of PUBLIC_TYPE_WITH_INTERNAL_API by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/31
* Link diagnostic names in the website changelog by @Mr3zee in https://github.com/Mr3zee/Library-API-Watchdog/pull/35


**Full Changelog**: https://github.com/Mr3zee/Library-API-Watchdog/compare/v0.1.1...v0.2.0

## 0.1.1 - 2026-11-05

### Changed

- [Inconsistent parameter order in overloads](https://mr3zee.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads):
  a last parameter that accepts a trailing lambda can stay last in each overload. Such a parameter has a
  function type, a `fun interface` type, or a Java SAM interface type.
- [Required parameters after optional ones](https://mr3zee.github.io/Library-API-Watchdog/checks/required-parameter-after-optional):
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
- [`REDUNDANT_EXEMPTION`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/redundant-exemption): a warning on an `@Intentionally*` annotation that its check doesn't need.
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
