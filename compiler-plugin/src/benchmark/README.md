# Watchdog performance benchmarks

JMH benchmarks measuring the time and memory allocation cost of watchdog checkers that can run
against a triggering corpus without failing compilation, both inside a complete compilation and
in isolation on resolved FIR. They are manual tooling: nothing here runs as part of `check` or
CI, and the results are reports for humans, not assertions.

## Running

```bash
# Everything (all checkers and both benchmarks, takes tens of minutes):
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmark

# Only the isolated per-checker benchmark:
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmark -Pbenchmark.include=IsolatedCheckerBenchmark

# A subset of checkers with custom JMH options:
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmark \
    -Pbenchmark.include=IsolatedCheckerBenchmark \
    "-Pbenchmark.args=-p checker=none,UndocumentedApiChecker -p corpusFiles=400"
```

`-Pbenchmark.include=<regex>` filters benchmarks. `-Pbenchmark.args='...'` passes raw JMH
options through (`-p` to pin parameters, `-wi`/`-i`/`-f` to change iteration counts, and so on).
Results are printed and saved as JSON to
`build/reports/benchmark/results-<yyyyMMdd-HHmmss-SSS>.json`.

Every run enables JMH's GC profiler: `gc.alloc.rate.norm` is the number of bytes allocated per
operation and is far more stable than wall-clock time, so prefer it for comparing allocation
behavior across changes.

## Profiling

The `profile` task runs one benchmark subject with Java Flight Recorder. By default it profiles
an `allCheckers` whole compilation:

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:profile

# Profile one checker over already-resolved FIR:
./gradlew :kotlin-library-api-watchdog-compiler-plugin:profile \
    -Pprofile.benchmark=isolated \
    -Pprofile.subject=UndocumentedApiChecker

# Profile the whole-compilation plugin baseline with a larger corpus and shorter run:
./gradlew :kotlin-library-api-watchdog-compiler-plugin:profile \
    -Pprofile.subject=pluginBaseline \
    -Pprofile.corpusFiles=400 \
    "-Pprofile.args=-wi 1 -i 2"
```

For `profile.benchmark=whole`, `profile.subject` accepts the modes listed below and defaults to
`allCheckers`. For `profile.benchmark=isolated`, it must name a checker from the isolated
benchmark (or `none` for the traversal baseline). `profile.corpusFiles` defaults to 200,
`profile.stackDepth` defaults to 256, and `profile.args` passes additional JMH options through.

JFR captures only the measurement iterations, after JMH warmup. Recordings are written under
`build/reports/profile/<benchmark-id>/profile-<yyyyMMdd-HHmmss-SSS>.jfr`. Open them in IntelliJ
IDEA or JDK Mission Control to inspect CPU samples, allocation samples, locks, and GC activity. The
task uses the JDK's built-in recorder and needs no platform-specific profiler installation.

## The two benchmarks

`WholeCompilationBenchmark` runs a full in-process `K2JVMCompiler` compilation of the synthetic
corpus per operation, with the plugin loaded through `-Xplugin` from the shadow jar exactly like
a real build. Its `mode` parameter selects what is enabled:

- `noPlugin` - the compiler alone.
- `pluginBaseline` - plugin applied, every configurable diagnostic set to `none`. The
  non-configurable `ExemptionExplanationChecker` still runs. This is the floor to subtract.
- `allCheckers` - everything enabled at `warning` severity.
- `<CheckerName>` - a single checker enabled. Its end-to-end cost is this mode minus
  `pluginBaseline`.

Checker costs are small relative to a whole compilation, so expect noise in this benchmark. It
answers "does the plugin visibly slow a build down", not "which checker allocates what".

`IsolatedCheckerBenchmark` compiles the corpus to resolved FIR once per trial (without the
watchdog), then each operation sweeps all FIR files with the compiler's checker-running
collector visitor wired to exactly one checker. The `checker` parameter names the checker.
`none` runs the traversal with zero checkers and is the baseline to subtract. This is the
low-noise, per-checker view: no plugin loading, no message rendering, no backend.

## The corpus

`BenchmarkCorpus` deterministically generates `corpusFiles` (default 200) explicit-API-style
files from ten rotating templates, so every warning-capable checker has real work: services, DSL
builders, companions, sealed hierarchies, value classes, type aliases, and so on. Most
declarations are clean. Each template carries a small fixed set of intended diagnostics so report
construction is also measured.

Three diagnostics never fire on purpose, because they are always errors and would fail the
measured compilation: `EXEMPTION_WITHOUT_EXPLANATION`,
`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`, and `PUBLIC_TYPE_WITH_INTERNAL_API`. Their checkers
still scan the whole corpus when the full plugin is loaded. `InternalApiTypeExposureChecker` is
also omitted from the per-checker benchmark parameters.

After changing the templates, verify the corpus still exercises every benchmark-eligible checker:

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit
```

It compiles the corpus with everything enabled and prints how often each diagnostic fired.

## Adding a checker

For a checker that can safely report warnings, add a `CheckerSubject` to `CheckerSubjects`, the
checker name to the `@Param` lists of both benchmarks, and a triggering (or at least scannable)
shape to a corpus template, then run `benchmarkCorpusAudit`. Always-error diagnostics must not
have a triggering corpus shape or a per-checker benchmark mode.

## Caveats

- One JMH fork per parameter value keeps runs affordable but leaves JIT-warmup noise in the
  whole-compilation numbers. Raise `-wi`/`-i`/`-f` through `-Pbenchmark.args` for finer runs.
- The GC profiler attributes allocation from all live threads. The compiler's own worker threads
  are included, which is what you want here.
- Absolute numbers are machine-specific. Compare runs from the same machine and JVM only.
