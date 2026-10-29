# Development and releases

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
