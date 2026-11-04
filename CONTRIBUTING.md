# Contributing

## Build

The build gets the compiler-plugin-dev-kit from its
[Space EAP repository](https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap).
The `devkit` entry of [gradle/libs.versions.toml](gradle/libs.versions.toml) sets its version.
The dev kit settings plugin gives the Kotlin development repositories, the plugin coordinates,
and the functional-test publishing.

The repository is a composite build:

| Build | Holds |
|---|---|
| The root build | The compiler plugin, the exemption fixer, and the Gradle plugin functional tests (`:gradle-plugin-test`). |
| [gradle-plugin](gradle-plugin) | The Gradle plugin and the report aggregation plugin. |
| [plugin-annotations](plugin-annotations) | The annotations. |
| [build-logic-settings](build-logic-settings) | The settings plugins: `devkit` for the root build and `devkit-included` for the other builds. Both apply the dev kit settings plugin. |
| [build-logic](build-logic) | The project plugins: `devkit.compiler`, `devkit.compiler-library`, `devkit.gradle`, and the `library-api-watchdog.*` conventions. |

A task name on the command line runs the task in the root build only. To run a task of an included build, put the
build name first: `./gradlew :gradle-plugin:test`. The root `check` task also runs `check` in `gradle-plugin` and
`plugin-annotations`. The root `publishToMavenCentral`, `publishAllPublicationsToSpaceRepository`, and
`publishPlugins` tasks also run the publication tasks of the included builds.

## Tests

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:allTests           # diagnostics tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-compiler-plugin:latestKotlinTest   # diagnostics tests against the newest Kotlin dev build
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate the JUnit classes from the test data
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit # check that the corpus triggers every benchmark-eligible checker
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkBaseline   # compare checker allocation with the CI baseline
./gradlew :kotlin-library-api-watchdog-exempts-fixer:allTests             # exemption fixer tests against every supported Kotlin version
./gradlew :gradle-plugin-test:allVersionedFunctionalTests                 # Gradle TestKit tests against every supported Kotlin version
./gradlew :gradle-plugin:test                                             # Gradle plugin unit tests
./gradlew :gradle-plugin:kotlin-library-api-watchdog-report-aggregation:test # report aggregation tests
./gradlew :build-logic:test                                               # build logic tests
```

CI runs one functional test task for each supported Kotlin version.
`:gradle-plugin-test:printCiFunctionalTestMatrix` lists those tasks.
The compiler plugin tests also run every day at 9:00 Berlin time, against the newest Kotlin dev build only
(`latestKotlinTest`).

The report aggregation tests run the report scripts in the Chrome for Testing headless shell. The build downloads the
headless shell and its chromedriver of the `chromeForTesting` version in `gradle/libs.versions.toml` into the Gradle
user home. Chrome for Testing has no build for Linux on ARM, so these tests skip themselves there.

The benchmarks and the baseline check are in
[compiler-plugin/src/benchmark/README.md](compiler-plugin/src/benchmark/README.md).

## Modules

- [`:kotlin-library-api-watchdog-compiler-plugin`](compiler-plugin/src): the compiler plugin, FIR checkers only. The
  test data is in [compiler-plugin/src/commonTest/data/diagnostics](compiler-plugin/src/commonTest/data/diagnostics).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin), the `kotlin-library-api-watchdog-plugin-annotations`
  artifact: the `@Intentionally*` exemption annotations, `@InternalAnnotationMarker`, and the `ExemptionReason` enum.
- [`:kotlin-library-api-watchdog-exempts-fixer`](exempts-fixer/src): reads the compiler diagnostic reports and applies
  the exemption annotations to Kotlin sources.
- [`:gradle-plugin`](gradle-plugin/src), the `kotlin-library-api-watchdog-gradle-plugin` artifact: applies the
  compiler plugin and the annotations dependency, and registers the `exemptExistingApi` and
  `generateApiWatchdogReport` tasks. Plugin id `org.jetbrains.kotlin.library.api-watchdog`.
- [`:gradle-plugin:kotlin-library-api-watchdog-report-aggregation`](gradle-plugin/report-aggregation/src): combines
  the reports of several projects. Plugin id `org.jetbrains.kotlin.library.api-watchdog-report-aggregation`.

## Documentation site

The site is a [Docusaurus](https://docusaurus.io/) project in [docs](docs), built by
[docs.yml](.github/workflows/docs.yml). Run its checks in `docs/` after `npm ci`:

```bash
npm test                            # the remark plugins
npm run check:diagnostic-exemptions # the exemption wording in the check pages matches diagnostics.json
npm run build                       # the site build, fails on a broken link or anchor
```

Only a manual run of `docs.yml` on `main` deploys the site. The run needs the `version` input, a release version with
a tag, and the code samples show that version. The Release workflow starts this run after each release.

## Build scans and cache

The root build and its included builds use [JetBrains Develocity](https://ge.jetbrains.com/) for build scans and the remote build cache.
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

`library.properties` sets the next development version, for example `0.3.0` after the `0.2.0` release.
Local builds and the development builds of `main` use it. The Release workflow sets it after each release.
To change the build and website package versions by hand, run:

```bash
./scripts/update-version.sh 0.3.0
./scripts/verify-version-alignment.sh
```

`LIBRARY_VERSION` overrides that version in every Gradle project, generated plugin coordinates, reports, and website code samples.
A release and a docs deploy set it to the release version:

```bash
LIBRARY_VERSION=0.2.0-dev-1 ./gradlew assemble
```

The override doesn't change any source file. The build ignores `-Pversion`.
An empty or invalid value fails the build.

## Dependency updates

Renovate reads [.github/renovate.json](.github/renovate.json).
To activate it, install the [Renovate GitHub App](https://github.com/apps/renovate) for this repository and merge the configuration into `main`.

Renovate opens pull requests for Gradle, npm, and GitHub Actions updates. Automatic merging is disabled.
Renovate groups Kotlin and compiler-plugin-dev-kit updates for compatibility review.
Docusaurus packages also share one pull request.

The version catalog tracks the compiler-plugin-dev-kit. A package rule gives its Space registry.
A custom manager tracks the Android Gradle plugin test versions in the version catalog.
The `agp8` entry stays on version 8, and `agp9` stays on version 9.
The minimum supported Kotlin and IntelliJ IDEA versions remain manual compatibility decisions.

To validate the configuration:

```bash
npm exec --yes --package=node@24 --package=renovate@44.164.6 -- renovate-config-validator --strict --no-global .github/renovate.json
```

## Changelog

The Release workflow writes the section of each release in [CHANGELOG.md](CHANGELOG.md), so a pull request doesn't
edit the changelog. The section holds the GitHub generated release notes: the pull requests since the previous release
tag. The title of a pull request is its changelog entry. The notes skip the pull requests with the `release` label of
[.github/release.yml](.github/release.yml).

For text above the generated notes of the next release, add a `## Unreleased` section at the top of CHANGELOG.md. The
release moves that text into its section and removes the `## Unreleased` section.
The release sections are in version order, the latest first.

After an edit of CHANGELOG.md, generate the website page:

```bash
cd docs
npm run sync:changelog
```

CI checks that the website changelog matches the root file.
The version script leaves historical changelog entries and dependency versions unchanged.

## Stable release

Run [Release](.github/workflows/release.yml) with the `version` input, for example `0.2.0`:

- From `main` for a version later than every release.
- From `release/<MAJOR>.<MINOR>.x` for a backport. Create the branch from the latest release tag of the line, and
  cherry-pick the fixes. The generated notes list pull requests only, so merge the cherry-picked fixes through a pull
  request into the branch, or describe them in `## Unreleased`. The run uses the workflow files of that branch.

The workflow does these steps:

1. Checks the version against the release tags, the version alignment, and the Space credentials.
2. Pushes the `prepare-release/<version>` branch and opens a pull request into the branch of the run. The commit
   adds the changelog section of the release. It also sets the next development version: the next minor version from
   `main`, the next patch version from a maintenance branch.
3. Runs the [compiler plugin tests](.github/workflows/compiler-plugin-tests.yml) and the
   [Gradle tests](.github/workflows/gradle-tests.yml) on the commit of the pull request.
4. Builds all publications on macOS and creates the tag `v<version>` on the commit of the pull request. Then uploads
   all modules to the Space EAP repository with the release version.
5. Creates the GitHub release with the changelog section as its notes. A backport isn't the latest GitHub release.
6. Squash-merges the pull request. The tag isn't in the history of the target branch.
7. For a release from a maintenance branch, adds its changelog section to `main` through a second pull request.
8. Runs [docs.yml](.github/workflows/docs.yml) on `main` with the highest release version and waits for the deploy.

The release is the state of the branch when the run starts. The workflow doesn't handle a change of the target
branch during the run.

The workflow uses `GITHUB_TOKEN`, so the repository setting **Allow GitHub Actions to create and approve pull
requests** must be on. A pull request or a merge with `GITHUB_TOKEN` doesn't start other workflows, so the release
runs the tests and the docs deploy itself.

### Recovery

Each job finds its finished work and skips it. **Re-run failed jobs** and a new run with the same version both
continue a failed release:

- Before the tag, a new run makes the release branch again when the branch of the run has a new commit. After a
  failed test or a failed build of the publications, push the fix to the branch of the release and run the workflow
  again.
- After the tag, every run uses the tagged commit and doesn't test it again. A run uploads to Space again until the
  GitHub release exists. Then it continues with the merges and the docs deploy.

## Release publication

Run [Publish to Maven Central](.github/workflows/publish-maven-central.yml) or
[Publish to Gradle Plugin Portal](.github/workflows/publish-gradle-plugin-portal.yml) with the GitHub Actions **Run workflow** button.
The `version` input is a release version with a tag. The workflow publishes the commit of the tag `v<version>`.
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
| Space | `KOTLIN_LIBRARY_SPACE_USERNAME` | Space username |
| Space | `KOTLIN_LIBRARY_SPACE_PASSWORD` | Space token |

The Maven Central workflow uploads all modules, including the annotation platform variants, as draft deployments.
Each build makes its own deployment: the root build, `gradle-plugin`, and `plugin-annotations`.
Approve all three in [Central Portal](https://central.sonatype.com/publishing/deployments) before the Plugin Portal run.
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
