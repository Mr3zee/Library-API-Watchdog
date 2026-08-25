# Adding the plugin to existing libraries

A library that has already shipped most probably can't change the shape of its public API without breaking
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

- **Undocumented APIs are not exempt**
  [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md) is not auto-fixed, but reported as a warning during the task run instead.
- **Run it on a clean working tree and review the diff.** The task edits sources in place. 
  Review the diff, commit, and let the checks guard only the API added afterwards. 
  New code deserves a thought-through decision instead - fix the shape or pick the exemption reason by hand.
- **Collection mode is task-scoped.** `updateBackwardsCompatibilityExempts` makes
  regular main compilations write reports, forces explicit API warning mode, and temporarily
  demotes every enabled configurable watchdog diagnostic to a warning so the fixer can run.
  Ordinary compilation tasks are unchanged when the update task is not in the task graph.
- **Unfixable always-error checks are skipped during collection.** The task temporarily disables
  [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md),
  [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md),
  and [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md) because it
  can neither demote nor acknowledge them automatically. Ordinary compilations still enforce all
  three, so resolve any violations separately after adopting the generated exemptions.
- **Real compilation errors still fail the task.** Since these are the project's regular compilation
  tasks, unresolved references, syntax errors, and other compiler errors must be fixed before the
  fixer can run.
- **Severity configuration is respected.** A check set to `NONE` in `apiWatchdog` records nothing
  and generates no exemptions. `ERROR` and `WARNING` are treated the same during the task run.
- **One diagnostic is fixed by a replacement.** A markerless `@SubclassOptInRequired`
  ([`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](./checks/subclass-opt-in-without-markers.md)) gates nothing, so the
  fixer drops it and puts [`@IntentionallyOpen`](./checks/open-api-without-subclass-opt-in.md) in its
  place: the class stays open to everyone, now stated outright.
- **Some diagnostics have no annotation to add.**
  [`DSL_MARKER_NOOP_TYPE_POSITION`](./checks/special/dsl-marker-noop-type-position.md) is fixed by moving or
  removing the marker. Such cases are listed as warnings during the task run for the later manual follow-up.
- **Running it twice is safe.** Exempted diagnostics are no longer reported, so a second run
  finds nothing left to do.
