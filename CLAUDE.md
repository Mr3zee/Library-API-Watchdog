# CLAUDE.md

Skill from https://mr3zee.github.io/code-doc-writer/ must be installed 
and used as `/code-doc-writer:code-doc-writer` in every session.

A Kotlin K2 compiler plugin, a Gradle plugin, and an annotations library. The checkers warn library authors about
public API declarations that are hard to evolve. README.md lists the diagnostics. `DOCS_GUIDELINE.md` holds the rules
for every text a person reads.

## Commands

The compiler plugin and the fixer have no plain `test` task. The dev kit registers one test task for each supported
Kotlin version (`k2320Test`, `k2410Test`, ...). `./gradlew :<module>:tasks --all` lists the current set.

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:k2410Test          # diagnostics tests against Kotlin 2.4.10
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate JUnit classes from test data
./gradlew :kotlin-library-api-watchdog-exempts-fixer:k2410Test            # fixer tests plus the compiler-plugin integration test
./gradlew :kotlin-library-api-watchdog-gradle-plugin:functionalTest       # Gradle TestKit tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-gradle-plugin:k2410FunctionalTest  # the same against one Kotlin version
./gradlew :kotlin-library-api-watchdog-compiler-plugin:k2410Test --tests "*JvmDiagnosticTestGenerated*"   # filter generated tests
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit   # check that the corpus triggers every benchmark-eligible checker
```

Each test-data file is one test method in the generated classes. After you add, remove, or rename a file in
`compiler-plugin/src/test/data/diagnostics/`, run `generateTests` again. Test results are in
`build/test-results/<taskName>/` of the module.

## Constraints the code doesn't show

- `diagnostics.json` is the single source of truth for diagnostic texts. The build generates `WatchdogDiagnosticMessages`
  (compiler plugin) and `WatchdogChecks` (Gradle plugin) from it. The docs build fails on a diagnostic name or range
  that the compiler doesn't report.
- `WatchdogDiagnosticsRecorder` must not reference `com.intellij` types in bytecode, because the jar runs both in the
  CLI and in the relocated `kotlin-compiler-embeddable` classpath.
- The fixer uses K2 PSI entry points only, with relocated `org.jetbrains.kotlin.com.intellij` imports. Its
  dependencies are compile-only. The Gradle task gives it the `kotlin-compiler-embeddable` of the project's Kotlin
  version at runtime.
- The Gradle plugin checks the task graph in `gradle.taskGraph.whenReady`, never on task realization, because IDE
  sync realizes every task.
- Android compilations resolve dependencies through an `android-classes-jar` artifact view. Without it a project AAR
  has no artifact. The plugin recognizes them by the `android` JVM environment of the compile classpath, because the
  `com.android.kotlin.multiplatform.library` target of AGP 8 reports platform type `jvm`.
- `:kotlin-library-api-watchdog-report-aggregation` is a separate artifact so a root project that applies it doesn't
  put the KGP-dependent support plugin on every project's plugin classpath.
- The Android functional tests skip themselves without an Android SDK. AGP 8 fixtures run with the pinned
  `agpCompatibleGradle`, because AGP 8 can't run on Gradle 9.6 or later. The dev kit skips every multiplatform fixture
  on Kotlin versions older than the build's own, because KLIBs aren't forward compatible.

## Adding a new diagnostic

1. The entry in `diagnostics.json`.
2. The factory in `WatchdogDiagnostics`, a checker in `fir/`, and its registration in `WatchdogFirCheckers`.
3. The severity property and name mapping in `WatchdogGradleExtension`, and the assertions in `WatchdogProjectTest`.
4. The checker list and the severity sample in README.md.
5. Test data and the regenerated tests.
6. The fixer: `ExemptionRegistry`, the expected-names set in `FixerProtocolTest`, and the specimen file of
   `UpdateBackwardsCompatibilityExemptsTest`.
7. The docs: the check page at the `docs` path from `diagnostics.json`, `docs/sidebars.ts`, the page map in
   `docs/authoring.md`, and the check list in `docs/docs/overview.md`.
8. The benchmarks in `compiler-plugin/src/benchmark/`, only when the checker can report a warning: a
   `CheckerSubject`, the `@Param` lists of both benchmark classes, and a triggering shape in `BenchmarkCorpus`. Then
   run `benchmarkCorpusAudit`.
