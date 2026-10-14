# The API Watchdog report

The report lists the Watchdog diagnostics of a project and the exemptions that cover them. Two
tasks write it:

- `generateApiWatchdogReport` changes no source.
- [`exemptExistingApi`](./existing-libs.md) writes it after the source update, so the applied section
  lists the new exemptions and the not-applied section lists the diagnostics that need manual attention.

```bash
./gradlew generateApiWatchdogReport
```

Both tasks compile the main sources in collection mode. [Details worth knowing](./existing-libs.md#details-worth-knowing)
lists what collection mode changes.

## Formats

Each task writes the report in two formats. Both are on by default:

| Format | Default file                            | Reader                                      |
|--------|-----------------------------------------|---------------------------------------------|
| HTML   | `build/reports/api-watchdog/report.html` | A person.                                   |
| SARIF  | `build/reports/api-watchdog/report.sarif` | A code scanning service or an IDE plugin.  |

The HTML page has two sections:

- **Applied annotations**: every Watchdog `@Intentionally*` annotation in the main Kotlin sources,
  grouped by annotation, with any `ExemptionReason`.
- **Not applied**: the diagnostics that have no annotation, grouped by the annotation they need, or
  under **No automatic annotation** when no such annotation exists.

The SARIF file follows [SARIF 2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html).
It holds the **Not applied** diagnostics only:

- One rule for each diagnostic name, with the check title and a link to its page.
- One result for each diagnostic. The level is the configured severity, `error` or `warning`. The
  message names the check, the exemption annotation when one exists, and the reason the task didn't
  apply it.
- A location is the source path relative to the root directory of the build, with the line. A code
  scanning service resolves it against the repository checkout.

Upload the file to GitHub code scanning with the `github/codeql-action/upload-sarif` action, or to any
service that accepts SARIF.

## Selecting the formats

The `reports` block of the `apiWatchdog` extension turns each format on or off and sets its file.
The file is a Gradle `RegularFile` provider:

```kotlin build.gradle.kts
apiWatchdog {
    reports {
        html = false
        sarif = true
        sarifFile = layout.buildDirectory.file("scan/watchdog.sarif")
    }
}
```

Both tasks read the block. A format that is off writes no file and no console line.

## Multi-project builds

Apply the report aggregation plugin to the root project and declare every subproject. A module
without the Watchdog adds nothing to the report:

```kotlin build.gradle.kts
plugins {
    kotlin("library.api-watchdog-report-aggregation") version "{{libraryApiWatchdogVersion}}"
}

dependencies {
    subprojects.forEach { apiWatchdogReports(project(it.path)) }
}
```

This works with the configuration cache and with project isolation. 

To report on some modules only, declare each one:

```kotlin build.gradle.kts
dependencies {
    apiWatchdogReports(project(":library-core"))
    apiWatchdogReports(project(":library-client"))
}
```

Then run:

```bash
./gradlew aggregateApiWatchdogReports
```

The task runs `generateApiWatchdogReport` in each declared module with the Watchdog and combines
the results into `build/reports/api-watchdog/aggregate-report.html` and `aggregate-report.sarif`
in the root project. The header of the HTML report counts the modules. Each row names its module.
A module that isn't declared is missing from the aggregate reports.

The `reports` block of the task selects the aggregate formats:

```kotlin build.gradle.kts
tasks.aggregateApiWatchdogReports {
    reports {
        html = false
        sarifFile = layout.buildDirectory.file("scan/watchdog.sarif")
    }
}
```
