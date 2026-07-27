# Adding the plugin to existing libraries

A library that has already shipped can't change the shape of its public API without breaking
users, so the Watchdog's first run typically floods it with diagnostics that are not actionable.
`updateBackwardsCompatibilityExempts` acknowledges all of them at once by applying respectful 
`@Intentionally*` annotation:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

The task depends on every regular main Kotlin compilation exposed by KGP. Each JVM, JS, Native,
Wasm, and metadata compilation records the diagnostics it sees together with their exact source
positions. The task merges those reports, removes exact duplicates from common sources compiled
for several targets, and runs a fixer in a separate process. 

Each diagnostic gets the matching `@Intentionally*` annotation with
`ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, placed under the declaration's KDoc and
above its other annotations, with imports added as needed.

## Details worth knowing

[//]: # (TODO update how UNDOCUMENTED_PUBLIC_API is handled in the fixer)
- **Undocumented APIs are not exempt** 
  [`UNDOCUMENTED_PUBLIC_API`](undocumented-public-api.md) is not auto-fixed, but reported as a warning instead.
- **Run it on a clean working tree and review the diff.** The task edits sources in place. It is
  meant for adoption: acknowledge the shipped API wholesale, commit, and let the checks guard
  only the API added afterwards. New code deserves a deliberate decision instead - fix the shape,
  or pick the exemption reason by hand.
- **Collection mode is task-scoped.** Selecting `updateBackwardsCompatibilityExempts` internally
  makes regular main compilations write reports, forces explicit API warning mode, and temporarily
  demotes every enabled configurable watchdog diagnostic to a warning so the fixer can run.
  Ordinary compilation tasks are unchanged when the update task is not in the task graph.
- **Severity configuration is respected.** A check set to `NONE` in `apiWatchdog` records nothing
  and gets no exemptions; `ERROR` and `WARNING` are exempted alike.
- **Real compilation errors still stop the task.** Since these are the project's regular compiler
  tasks, unresolved references, syntax errors, and the always-error
  [`EXEMPTION_WITHOUT_EXPLANATION`](exemption-without-explanation.md) must be fixed before the
  fixer can run.
- **Some diagnostics have no annotation to add.**
  [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](subclass-opt-in-without-markers.md) is fixed by passing
  marker classes, [`DSL_MARKER_NOOP_TYPE_POSITION`](dsl-marker-noop-type-position.md) is fixed by
  moving or removing the marker. These cases are listed as build warnings for manual follow-up.
- **Running it twice is safe.** Exempted diagnostics are no longer reported, so a second run
  finds nothing left to do.
