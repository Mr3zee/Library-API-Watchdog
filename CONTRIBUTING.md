# Contributing

## Build

The build gets the compiler-plugin-dev-kit from its
[Space EAP repository](https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap).
The dev kit settings plugin gives the Kotlin development repositories, the plugin coordinates,
and the functional-test publishing.

## Tests

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:allTests           # diagnostics tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate the JUnit classes from the test data
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit # check that the corpus triggers every benchmark-eligible checker
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkBaseline   # compare checker time and allocation with the CI baseline
./gradlew :kotlin-library-api-watchdog-exempts-fixer:allTests             # exemption fixer tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-gradle-plugin:functionalTest       # Gradle TestKit tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-report-aggregation:test            # report aggregation tests
```

CI runs one functional test task for each supported Kotlin version.
`:kotlin-library-api-watchdog-gradle-plugin:printCiFunctionalTestMatrix` lists those tasks.

The benchmarks and the baseline check are in
[compiler-plugin/src/benchmark/README.md](compiler-plugin/src/benchmark/README.md).

## Modules

- [`:kotlin-library-api-watchdog-compiler-plugin`](compiler-plugin/src): the compiler plugin, FIR checkers only. The
  test data is in [compiler-plugin/src/test/data/diagnostics](compiler-plugin/src/test/data/diagnostics).
- [`:kotlin-library-api-watchdog-plugin-annotations`](plugin-annotations/src/commonMain/kotlin): the `@Intentionally*`
  exemption annotations, `@InternalAnnotationMarker`, and the `ExemptionReason` enum.
- [`:kotlin-library-api-watchdog-exempts-fixer`](exempts-fixer/src): reads the compiler diagnostic reports and applies
  the exemption annotations to Kotlin sources.
- [`:kotlin-library-api-watchdog-gradle-plugin`](gradle-plugin/src): applies the compiler plugin and the annotations
  dependency, and registers the `exemptExistingApi` and `generateApiWatchdogReport` tasks. Plugin id
  `org.jetbrains.kotlin.library.api-watchdog`.
- [`:kotlin-library-api-watchdog-report-aggregation`](report-aggregation/src): combines the reports of several
  projects. Plugin id `org.jetbrains.kotlin.library.api-watchdog-report-aggregation`.

## Documentation site

The site is a [Docusaurus](https://docusaurus.io/) project in [docs](docs), built by
[docs.yml](.github/workflows/docs.yml). Run its checks in `docs/` after `npm ci`:

```bash
npm test                            # the remark plugins
npm run check:diagnostic-exemptions # the exemption wording in the check pages matches diagnostics.json
npm run build                       # the site build, fails on a broken link or anchor
```

## Build scans and cache

The root build and its `gradle-conventions` included build use [JetBrains Develocity](https://ge.jetbrains.com/) for build scans and the remote build cache.
CI publishes scans for authenticated builds. Local scans are disabled by default.

To enable local scans, copy `local.properties.template` to `local.properties` and set `develocity.buildScan.enabled=true`.

Use `--scan` or `--no-scan` to override this setting for one build.
CI waits for scan uploads before each Gradle command exits.

To authenticate locally, run:

```bash
./gradlew provisionDevelocityAccessKey
```

Gradle stores the key in `~/.gradle/develocity/keys.properties`.
The same key authenticates scan uploads and remote cache access.

For GitHub Actions, add the repository secret `DEVELOCITY_ACCESS_KEY` with the value `ge.jetbrains.com=<access-key>`.
The key needs permission to publish scans and read and write the build cache.

Local builds and pull requests only read the remote cache.
CI on `main` sets `DEVELOCITY_CACHE_PUSH=true` to write cache entries.

## Version

`gradle.properties` sets the library version. To change the build, README examples, and website package versions, run:

```bash
./scripts/update-version.sh 0.2.0
./scripts/verify-version-alignment.sh
```

The website code samples read the library version from `gradle.properties`.
`LIBRARY_VERSION` overrides that version in every Gradle project, generated plugin coordinates, reports, and website code samples:

```bash
LIBRARY_VERSION=0.2.0-dev-1 ./gradlew assemble
```

The override takes precedence over `-Pversion` and doesn't change any source file.
An empty or invalid value fails the build.

## Dependency updates

Renovate reads [.github/renovate.json](.github/renovate.json).
To activate it, install the [Renovate GitHub App](https://github.com/apps/renovate) for this repository and merge the configuration into `main`.

Renovate opens pull requests for Gradle, npm, and GitHub Actions updates. Automatic merging is disabled.
Renovate groups Kotlin and compiler-plugin-dev-kit updates for compatibility review.
Docusaurus packages also share one pull request.

Custom managers track `systemProp.devkitVersion` and the Android Gradle plugin test versions in the version catalog.
The `agp8` entry stays on version 8, and `agp9` stays on version 9.
The minimum supported Kotlin and IntelliJ IDEA versions remain manual compatibility decisions.

To validate the configuration:

```bash
npm exec --yes --package=node@24 --package=renovate@44.164.6 -- renovate-config-validator --strict --no-global .github/renovate.json
```

## Changelog

Edit [CHANGELOG.md](CHANGELOG.md), then generate the website page:

```bash
cd docs
npm run sync:changelog
```

CI checks that the website changelog matches the root file.
The version script leaves historical changelog entries and dependency versions unchanged.

## Release publication

Run [Publish to Maven Central](.github/workflows/publish-maven-central.yml) or
[Publish to Gradle Plugin Portal](.github/workflows/publish-gradle-plugin-portal.yml) with the GitHub Actions **Run workflow** button.
The optional `library_version` input overrides the source version for that run.
Both workflows fail when a credential is absent or blank.

Configure these repository secrets:

| Target | Secret | Value |
|---|---|---|
| Maven Central | `MAVEN_CENTRAL_USERNAME` | Central Portal token username |
| Maven Central | `MAVEN_CENTRAL_PASSWORD` | Central Portal token password |
| Maven Central | `MAVEN_CENTRAL_SIGNING_KEY` | ASCII-armored private PGP key |
| Maven Central | `MAVEN_CENTRAL_SIGNING_PASSWORD` | Private key password |
| Gradle Plugin Portal | `GRADLE_PUBLISH_KEY` | Portal API key |
| Gradle Plugin Portal | `GRADLE_PUBLISH_SECRET` | Portal API secret |

The Maven Central workflow uploads all modules, including the annotation platform variants, as a draft deployment.
Approve it in [Central Portal](https://central.sonatype.com/publishing/deployments) before the Plugin Portal run.
The Plugin Portal workflow publishes the watchdog and report aggregation plugins.
Plugin Portal doesn't have a draft deployment mode. A successful run submits both plugin versions for publication.

For a local Maven Central upload, set the environment variables below, then run `./gradlew publishToMavenCentral`:

| Environment variable | Repository secret |
|---|---|
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | `MAVEN_CENTRAL_USERNAME` |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | `MAVEN_CENTRAL_PASSWORD` |
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | `MAVEN_CENTRAL_SIGNING_KEY` |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | `MAVEN_CENTRAL_SIGNING_PASSWORD` |

For a local Plugin Portal upload, set `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`, then run `./gradlew publishPlugins`.
