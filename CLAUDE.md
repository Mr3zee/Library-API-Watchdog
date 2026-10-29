# CLAUDE.md

Use the `/code-doc-writer:code-doc-writer` skill (https://mr3zee.github.io/code-doc-writer/) in every session.

A Kotlin K2 compiler plugin, a Gradle plugin, an exemption fixer, a report-aggregation plugin, and an
annotations library. The checkers warn library authors about public API declarations that are hard to
evolve. README.md lists the diagnostics. `DOCS_GUIDELINE.md` holds the rules for every text a person reads.

## Commands

The compiler plugin and the fixer have no plain `test` task, only one task for each supported Kotlin version
(`k2320Test`, `k2410Test`, ...). `./gradlew :<module>:tasks --all` lists them.

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:k2410Test          # diagnostics tests against Kotlin 2.4.10
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate JUnit classes from test data
./gradlew :kotlin-library-api-watchdog-exempts-fixer:k2410Test            # fixer tests plus the compiler-plugin integration test
./gradlew :kotlin-library-api-watchdog-gradle-plugin:functionalTest       # Gradle TestKit tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-gradle-plugin:k2410FunctionalTest  # the same against one Kotlin version
./gradlew :kotlin-library-api-watchdog-compiler-plugin:k2410Test --tests "*JvmDiagnosticTestGenerated*"   # filter generated tests
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit   # check that the corpus triggers every benchmark-eligible checker
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkBaseline -Pbenchmark.baseline.allowMissing=true  # the CI benchmark check, report only on a machine without a baseline
```

Each file in `compiler-plugin/src/test/data/diagnostics/` and `messages/` is one generated test method. Run
`generateTests` after you add, remove, or rename one. `messages/` checks the rendered text of every diagnostic:
inline markers plus a `.messages.txt` dump of the full messages (`JvmDiagnosticMessagesTestGenerated`). The first
run generates a missing dump. The dump replaces the macOS shortcut `⌘N` with `Alt+Insert`. Hand-written tests in
`compiler-plugin/src/test/kotlin` run in every `k*Test` task.

Docs checks run in `docs/` after `npm ci`. The `docs.yml` workflow runs all three:

```bash
npm test                            # the remark plugins: diagnostic annotations in code samples, check-page links, README order
npm run check:diagnostic-exemptions # the exemption wording in the check pages matches diagnostics.json
npm run build                       # the site build, fails on a broken link or anchor
```

## Constraints the code doesn't show

- `diagnostics.json` is the single source of truth for diagnostic texts. The build generates
  `WatchdogDiagnosticMessages` (compiler plugin), `WatchdogChecks` (Gradle plugin and fixer),
  `WatchdogCheckDescriptions` (report aggregation), and the `Diagnostic` enum of the Gradle functional tests from
  it. The docs build fails on a diagnostic name that it doesn't list or a range that matches no sample line.
  `DocsExamplesTest` compiles every docs sample and checks that the compiler reports exactly the annotated
  diagnostics, ranges, and parameters.
- The Gradle functional tests describe a build with the DSL in `WatchdogProject.kt` (`jvmProject`, `kmpProject`,
  `androidLibrary`, `kmpAndroidProject`). They check output with the `BuildResult` assertions in
  `BuildAssertions.kt`, and an assertion matches the first paragraph of a message. A new always-error diagnostic
  goes into `alwaysErrorDiagnostics`.
- The compiler plugin must not reference `com.intellij` types in bytecode (a field type, a method signature, or a
  class literal). The jar runs in the CLI and in the relocated `kotlin-compiler-embeddable` classpath, and a
  reference links in one of them only. `IntellijReferencesTest` fails on each one. A type argument is fine, because
  erasure removes it. So a custom positioning strategy is impossible: its `mark` override never binds in the
  embeddable compiler, and the default strategy marks the whole declaration. A checker reports on a child source
  element from `KtSourceElement.getChild` with a `Set` of token types instead.
- The fixer uses K2 PSI entry points only, with unrelocated `com.intellij` imports, and compile-only dependencies.
  The Gradle task runs it in a separate JVM with the `kotlin-compiler` artifact of the project's Kotlin version.
- The Gradle plugin checks the task graph in `gradle.taskGraph.whenReady`, never on task realization, because IDE
  sync realizes every task.
- Android compilations resolve dependencies through an `android-classes-jar` artifact view, because a project AAR
  has no artifact without it. The plugin recognizes them by the `android` JVM environment of the compile classpath,
  because the `com.android.kotlin.multiplatform.library` target of AGP 8 reports platform type `jvm`.
- `:kotlin-library-api-watchdog-report-aggregation` is a separate artifact, so a root project that applies it
  doesn't put the KGP-dependent support plugin on every project's plugin classpath.
- `exemptExistingApi` writes the aggregate report through two project-local hooks, because project isolation
  forbids a finalizer on another project's task. The support plugin picks the producer of the report artifact
  from `gradle.startParameter.taskNames`. The aggregation plugin finalizes the root's `exemptExistingApi` (its own
  lifecycle task when the root has no Watchdog) with `aggregateApiWatchdogReports`.
- The benchmark baseline (`compiler-plugin/src/benchmark/baseline/`) is valid for one machine profile. Record it
  with the `Benchmark baseline` workflow (`record` input), never on a developer machine. A regression shows up in
  `bytes` first, because the `ms` tolerance is wide.
- The Android functional tests skip themselves without an Android SDK. AGP 8 fixtures run with the Gradle version
  pinned in `AgpVersion.AGP8` of `WatchdogProject.kt`, because AGP 8 can't run on Gradle 9.6 or later. The dev kit
  skips every multiplatform fixture on Kotlin versions older than the build's own, because KLIBs aren't forward
  compatible.

## Adding a new diagnostic

1. The entry in `diagnostics.json`.
2. The factory in `WatchdogDiagnostics`, a checker in `fir/`, and its registration in `WatchdogFirCheckers`.
3. The severity property and name mapping in `WatchdogGradleExtension`, and a specimen in the `unacknowledgedFile`
   of `WatchdogProjectTest`.
4. The checker list and the severity sample in README.md.
5. Test data in `diagnostics/`, a `messages/` file that triggers every `parameterValues` variant, and the
   regenerated tests.
6. The fixer: `ExemptionRegistry`, the expected-names set in `FixerProtocolTest`, and the specimen file of
   `ExemptExistingApiTest`.
7. The docs: the check page at the `docs` path from `diagnostics.json`, `docs/sidebars.ts`, and the check list in
   `docs/pages/overview.md`.
8. The benchmarks in `compiler-plugin/src/benchmark/`, only when the checker can report a warning. Add a
   `CheckerSubject`, the `@Param` lists of both benchmark classes, and a triggering shape in `BenchmarkCorpus`. Then
   run `benchmarkCorpusAudit`.
