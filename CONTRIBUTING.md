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
`plugin-annotations`. The root `publishAllPublicationsToSpaceRepository` and `publishPlugins` tasks also run the
publication tasks of the included builds. The root `publishAllPublicationsToBuildRepository`, `dumpArtifacts`, and
`checkArtifacts` tasks also run in `plugin-annotations`, but not in `gradle-plugin`.

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

The build also writes the Markdown form of the site for LLM readers: `llms.txt` (the page index),
`llms-full.txt` (every page), and a `.md` file next to each page. `docs/plugins/llms-txt.mjs`
renders them from the page sources.

Only a manual run of `docs.yml` on `main` deploys the site. The run needs the `version` input, a release version with
a tag, and the code samples show that version. The Post-publish workflow starts this run after each release.

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
Local builds and the development builds of `main` use it. The release commit of the Pre-release workflow sets it.
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

## Watchdog bot

The Dev kit update, Pre-release, and Post-publish workflows push their branches and open their pull requests as the
Watchdog bot, a
GitHub App. A pull request of `GITHUB_TOKEN` starts `pull_request` runs that wait for an approval
([Events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request)).
GitHub signs the commits of the bot, because the workflows make them through the GraphQL API.

Register the app in **Settings > Developer settings > GitHub Apps** of the repository owner:

| Setting | Value |
|---|---|
| Webhook | Off |
| Repository permissions | Contents: read and write. Issues: read and write (the `release` label). Pull requests: read and write. |
| Where can this GitHub App be installed? | Only on this account |

Then generate a private key, install the app on this repository, and add the repository settings:

| Name | Kind | Value |
|---|---|---|
| `WATCHDOG_BOT_CLIENT_ID` | Variable | The client ID of the app |
| `WATCHDOG_BOT_PRIVATE_KEY` | Secret | The private key, the content of the `.pem` file |
| `WATCHDOG_BOT_LOGIN` | Variable | The user of the app, `<app slug>[bot]`, for example `library-api-watchdog[bot]` |

The Dev kit update and the Pre-release workflows check the commit of their pull request themselves. So the compiler plugin
tests, the Gradle tests, the docs checks, the version alignment check, the artifact list check, and the benchmark
check skip a `pull_request` run of `WATCHDOG_BOT_LOGIN`. A push of another user to the branch runs them.

## Dependency updates

Renovate reads [.github/renovate.json](.github/renovate.json).
To activate it, install the [Renovate GitHub App](https://github.com/apps/renovate) for this repository and merge the configuration into `main`.

Renovate opens pull requests for Gradle, npm, and GitHub Actions updates. Automatic merging is disabled.
Renovate groups Kotlin updates for compatibility review.
Docusaurus packages also share one pull request.

Renovate skips the compiler-plugin-dev-kit. [Dev kit update](.github/workflows/devkit-update.yml) updates it every
day at 6:00 Berlin time, or when you run the workflow:

1. The workflow compares the `devkit` entry of the version catalog on `main` with the newest version in the Space
   repository. When they differ, it commits the newest version to the `devkit-update` branch and opens a pull request
   as the [Watchdog bot](#watchdog-bot).
2. The workflow runs the compiler plugin tests, the Gradle tests, the artifact list check, and the benchmark check on
   that commit.
3. When all of them pass, the workflow squash-merges the pull request. When one fails, the pull request stays open.

Each run makes the branch again from `main`. When the branch of the open pull request has a commit of another author,
the run doesn't change the branch. To fix a failed update, push the fix to the branch and merge the pull request
yourself.

A custom manager tracks the Android Gradle plugin test versions in the version catalog.
The `agp8` entry stays on version 8, and `agp9` stays on version 9.
The minimum supported Kotlin and IntelliJ IDEA versions remain manual compatibility decisions.

To validate the configuration:

```bash
npm exec --yes --package=node@24 --package=renovate@44.164.6 -- renovate-config-validator --strict --no-global .github/renovate.json
```

## Changelog

The Pre-release workflow writes the section of each release in [CHANGELOG.md](CHANGELOG.md), so a pull request doesn't
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

A release has three parts, in this order:

1. The [Pre-release](#pre-release) workflow makes the `release/<version>` branch and checks it.
2. The [publication](#release-publication) runs outside GitHub Actions, from that branch.
3. The [Post-publish](#post-publish) workflow creates the tag and the GitHub release, and merges the branch.

### Pre-release

Run [Pre-release](.github/workflows/pre-release.yml) with the `version` input, for example `0.2.0`:

- From `main` for a version later than every release.
- From `release/<MAJOR>.<MINOR>.x` for a backport. Create the branch from the latest release tag of the line, and
  cherry-pick the fixes. The generated notes list pull requests only, so merge the cherry-picked fixes through a pull
  request into the branch, or describe them in `## Unreleased`. The run uses the workflow files of that branch.

The workflow does these steps:

1. Checks the version against the release tags and the version alignment. It fails when the tag `v<version>` exists.
2. Pushes the `release/<version>` branch and opens a pull request into the branch of the run, as the
   [Watchdog bot](#watchdog-bot). The commit adds the changelog section of the release. It also sets the next development version: the next minor version from
   `main`, the next patch version from a maintenance branch.
3. Runs every check workflow on the commit of the pull request and waits for all of them:
   [compiler plugin tests](.github/workflows/compiler-plugin-tests.yml),
   [Gradle tests](.github/workflows/gradle-tests.yml), [docs checks](.github/workflows/docs.yml),
   [version alignment](.github/workflows/version-alignment.yml),
   [artifact lists](.github/workflows/artifact-lists.yml), and the
   [benchmark baseline](.github/workflows/benchmark-baseline.yml).
4. Builds all publications. The workflow doesn't upload them. The summary of the run has the commit of the branch.

The release is the state of the branch when the run starts. The workflow doesn't handle a change of the target
branch during the run.

A new run makes the release branch again when the branch of the run has a new commit. After a failed test or a failed
build of the publications, push the fix to the branch of the release and run the workflow again. Don't start a new
run after the publication of `release/<version>` started, because the published commit leaves the branch.

### Post-publish

Run [Post-publish](.github/workflows/post-publish.yml) after the publication. The publication build can start it. The
run can start from any branch:

```bash
gh workflow run post-publish.yml --repo Kotlin/Library-API-Watchdog --ref main \
  -f version=<version> -f sha=<published commit>
```

| Input | Value |
|---|---|
| `version` | The published version. |
| `sha` | The published commit. The run fails when `release/<version>` is at another commit. An empty value skips the check. |

The workflow does these steps:

1. Finds the pull request of `release/<version>` and the release commit, the head of the branch.
2. Creates the tag `v<version>` on the release commit.
3. Creates the GitHub release with the changelog section as its notes. A backport isn't the latest GitHub release.
4. Squash-merges the pull request and deletes `release/<version>`. The tag isn't in the history of the target branch.
5. For a release from a maintenance branch, adds its changelog section to `main` through a second pull request.
6. Runs [docs.yml](.github/workflows/docs.yml) on `main` with the highest release version and waits for the deploy.

The merges use `GITHUB_TOKEN`, and a merge with `GITHUB_TOKEN` doesn't start other workflows, so the workflow runs the
docs deploy itself.

Each job finds its finished work and skips it. **Re-run failed jobs** and a new run with the same version both
continue a failed run. After the tag, every run uses the tagged commit.

## Release publication

GitHub Actions doesn't publish a release. The Maven Central and Gradle Plugin Portal publications run outside this
repository, from the `release/<version>` branch of the [Pre-release](#pre-release) workflow. Run them with
`LIBRARY_VERSION=<version>`, because `library.properties` of the branch has the next development version. Space has
development versions only.

### Maven Central

Maven Central gets the compiler plugin, the exemption fixer, and the annotations. The Gradle plugin and the report
aggregation plugin go to the Gradle Plugin Portal only. The upload takes the modules from `build/repo`, the `Build`
repository. The steps before the upload:

1. Publish the Maven Central modules to `build/repo` with the release version and the signing key:

   ```bash
   LIBRARY_VERSION=<version> ./gradlew publishAllPublicationsToBuildRepository \
     -Plibs.sign.key.id=<key ID> -Plibs.sign.key.private=<key> -Plibs.sign.passphrase=<password>
   ```

2. Compare `build/repo` with the artifact lists, and check the signatures and the checksums:

   ```bash
   ./gradlew validateLocalMavenRepo --artifacts-dir=build/repo \
     --artifacts-list=artifacts:<version> \
     --artifacts-list=plugin-annotations/artifacts:<version> \
     --require-signatures --require-checksums=MD5,SHA1
   ```

The signing properties are Gradle properties:

| Gradle property | Value |
|---|---|
| `libs.sign.key.id` | The ID of the PGP key |
| `libs.sign.key.private` | ASCII-armored private PGP key |
| `libs.sign.passphrase` | Private key password |

A build without `libs.sign.key.private` doesn't sign its publications.

The [artifacts validator plugin](https://github.com/Kotlin/artifacts-validator-plugin) keeps the list of the published
files of each Maven Central module in the `artifacts` directory of its build: the root build and
`plugin-annotations`. `./gradlew checkArtifacts` fails when a publication doesn't match its list. The
[artifact lists](.github/workflows/artifact-lists.yml) workflow runs it. After a change of a publication, run
`./gradlew dumpArtifacts` and commit the lists. The lists of the compiler plugin and the fixer have the oldest
supported Kotlin version in an artifact name, for example `-k240`. So a change of `kotlinCliMin` in the version
catalog is such a change.

### Gradle Plugin Portal

Publish the plugins after the modules are on Maven Central. Set `LIBRARY_VERSION`, `GRADLE_PUBLISH_KEY`, and
`GRADLE_PUBLISH_SECRET`, then run `./gradlew publishPlugins`. The task fails when a credential is absent or blank.

The task publishes the watchdog and report aggregation plugins. Plugin Portal doesn't have a draft deployment mode. A
successful run submits both plugin versions for publication.

### Space

[Publish development build](.github/workflows/publish-snapshot.yml) uploads all modules to the Space EAP repository
on each push to `main`. The version is `<version>-dev-<run number>`, with the version of `library.properties`. No
workflow uploads a release version to Space.

The workflow needs these repository secrets:

| Secret | Value |
|---|---|
| `KOTLIN_LIBRARY_SPACE_USERNAME` | Space username |
| `KOTLIN_LIBRARY_SPACE_PASSWORD` | Space token |
