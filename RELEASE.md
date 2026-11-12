# Release

A release has three parts, in this order:

1. [Pre-release](#pre-release): a GitHub workflow makes the `release/<version>` branch and checks it.
2. [Publication](#publication): a build outside GitHub Actions publishes the branch to Maven Central and to the Gradle
   Plugin Portal.
3. [Post-publish](#post-publish): a GitHub workflow creates the tag and the GitHub release, and merges the branch.

Space gets [development versions](#development-versions) only.

## Pre-release

Run [Pre-release](.github/workflows/pre-release.yml) with the `version` input, for example `0.2.0`:

- From `main` for a version later than every release.
- From `release/<MAJOR>.<MINOR>.x` for a backport. Create the branch from the latest release tag of the line, and
  cherry-pick the fixes. The generated notes list pull requests only, so merge the cherry-picked fixes through a pull
  request into the branch, or describe them in `## Unreleased`. The run uses the workflow files of that branch.

The workflow does these steps:

1. Checks the version against the release tags and the version alignment. It fails when the tag `v<version>` exists.
2. Pushes the `release/<version>` branch and opens a pull request into the branch of the run, as the
   [Watchdog bot](CONTRIBUTING.md#watchdog-bot). The commit adds the changelog section of the release. It also sets
   the next development version: the next minor version from `main`, the next patch version from a maintenance branch.
3. Runs every check workflow on the commit of the pull request and waits for all of them:
   [compiler plugin tests](.github/workflows/compiler-plugin-tests.yml),
   [Gradle tests](.github/workflows/gradle-tests.yml), [docs checks](.github/workflows/docs.yml),
   [version alignment](.github/workflows/version-alignment.yml),
   [artifact lists](.github/workflows/artifact-lists.yml), and the
   [benchmark baseline](.github/workflows/benchmark-baseline.yml).
4. Builds all publications. It doesn't upload them.

The summary of the run gives the commit of `release/<version>`.

The release is the state of the branch when the run starts. The workflow doesn't handle a change of the target
branch during the run.

After a failed test or a failed build of the publications, push the fix to the branch of the run and run the workflow
again. A run from a new commit makes `release/<version>` again, without the commit of the earlier run. So don't start
such a run after the publication started.

## Publication

The publication build runs from `release/<version>`. `library.properties` of that branch has the next development
version, so each Gradle command of the build needs `LIBRARY_VERSION=<version>`.

| Target | Modules |
|---|---|
| Maven Central | The compiler plugin, the exemption fixer, and the annotations |
| Gradle Plugin Portal | The Gradle plugin and the report aggregation plugin |

### Maven Central

The upload takes every file of `build/repo`, the `Build` repository:

1. Publish the Maven Central modules to `build/repo` with the signing key:

   ```bash
   LIBRARY_VERSION=<version> ./gradlew publishAllPublicationsToBuildRepository \
     -Plibs.sign.key.id=<key ID> -Plibs.sign.key.private=<key> -Plibs.sign.passphrase=<password>
   ```

2. Compare `build/repo` with the [artifact lists](CONTRIBUTING.md#artifact-lists), and check the signatures and the
   checksums:

   ```bash
   ./gradlew validateLocalMavenRepo --artifacts-dir=build/repo \
     --artifacts-list=artifacts:<version> \
     --artifacts-list=plugin-annotations/artifacts:<version> \
     --require-signatures --require-checksums=MD5,SHA1
   ```

3. Upload `build/repo` to Maven Central.

The signing properties are Gradle properties:

| Gradle property | Value |
|---|---|
| `libs.sign.key.id` | The ID of the PGP key |
| `libs.sign.key.private` | ASCII-armored private PGP key |
| `libs.sign.passphrase` | Private key password |

A build without `libs.sign.key.private` doesn't sign its publications.

### Gradle Plugin Portal

Publish the plugins after the modules are on Maven Central:

```bash
LIBRARY_VERSION=<version> GRADLE_PUBLISH_KEY=<key> GRADLE_PUBLISH_SECRET=<secret> ./gradlew publishPlugins
```

The task fails when a credential is absent or blank. Plugin Portal doesn't have a draft deployment mode. A successful
run submits both plugin versions for publication.

## Post-publish

Run [Post-publish](.github/workflows/post-publish.yml) after the publication. The publication build can start it. The
run can start from any branch. `gh` needs a token with the `Actions: write` permission in `GH_TOKEN`:

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
continue a failed run.

## Development versions

[Publish development build](.github/workflows/publish-snapshot.yml) uploads all modules to the Space EAP repository
on each push to `main`. The version is `<version>-dev-<run number>`, with the version of `library.properties`. No
workflow uploads a release version to Space.

The workflow needs these repository secrets:

| Secret | Value |
|---|---|
| `KOTLIN_LIBRARY_SPACE_USERNAME` | Space username |
| `KOTLIN_LIBRARY_SPACE_PASSWORD` | Space token |
