# The API Watchdog report

The report is one HTML page with two sections:

- **Applied annotations**: every Watchdog `@Intentionally*` annotation in the main Kotlin sources,
  grouped by annotation, with any `ExemptionReason`.
- **Not applied**: the diagnostics that have no annotation, grouped by the annotation they need, or
  under **No automatic annotation** when no such annotation exists.

Two tasks write it to `build/reports/api-watchdog/report.html`:

- `generateApiWatchdogReport` changes no source.
- [`exemptExistingApi`](./existing-libs.md) writes it after the source update, so the applied section
  lists the new exemptions and the not-applied section lists the diagnostics that need manual attention.

```bash
./gradlew generateApiWatchdogReport
```

Both tasks compile the main sources in collection mode. [Details worth knowing](./existing-libs.md#details-worth-knowing)
lists what collection mode changes.

## Multi-project builds

Apply the report aggregation plugin to the root project and declare every module that applies the
Watchdog:

```kotlin build.gradle.kts
plugins {
    kotlin("library.api-watchdog-report-aggregation") version "{{libraryApiWatchdogVersion}}"
}

dependencies {
    apiWatchdogReports(project(":library-core"))
    apiWatchdogReports(project(":library-client"))
}
```

Then run:

```bash
./gradlew aggregateApiWatchdogReports
```

The task runs `generateApiWatchdogReport` in each declared module and combines the results into
`build/reports/api-watchdog/aggregate-report.html` in the root project. Each row of the aggregate
report names its module. A module that isn't declared is missing from the aggregate report.
