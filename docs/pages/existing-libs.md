# Adding the plugin to existing libraries

A library that has already shipped most probably can't change the shape of its public API without breaking
users, so the Watchdog's first run typically floods it with diagnostics that are not actionable.
`exemptExistingApi` acknowledges all of them at once by applying respectful
`@Intentionally*` annotation:

```bash
./gradlew exemptExistingApi
```

The task depends on every regular main Kotlin compilation exposed by KGP. Each JVM, JS, Native,
Wasm, and metadata compilation records the diagnostics it sees together with their exact source
positions. The task merges those reports, removes exact duplicates from common sources compiled
for several targets, and runs a fixer in a separate process.

Each diagnostic gets the matching `@Intentionally*` annotation with
`ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, placed under the declaration's KDoc and
above its other annotations, with imports added as needed. An undocumented declaration gets a
placeholder KDoc instead of an exemption:

```kotlin
/** TODO: Provide kDocs for this declaration */
public class Config
```

## The report

The task also writes the [API Watchdog report](./report.md) to `build/reports/api-watchdog/`, as
an HTML page and a SARIF file. It lists the applied annotations and the diagnostics that need manual
attention. `generateApiWatchdogReport` writes the same report without a source change.

## Details worth knowing

- **Undocumented APIs are not exempt.** Documentation can be added without breaking anyone, so
  [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md) gets no `@IntentionallyUndocumented`.
  The task inserts the placeholder KDoc `TODO: Provide kDocs for this declaration` instead,
  which satisfies the check until the real documentation replaces it.
- **Run it on a clean working tree and review the diff.** The task edits sources in place. 
  Review the diff, commit, and let the checks guard only the API added afterwards. 
  New code deserves a thought-through decision instead - fix the shape or pick the exemption reason by hand.
- **Collection mode is task-scoped.** `exemptExistingApi` and
  `generateApiWatchdogReport` make
  regular main compilations write reports, force explicit API warning mode, and temporarily
  demote every enabled configurable watchdog diagnostic to a warning so the fixer can run.
  Ordinary compilation tasks are unchanged when neither task is in the task graph.
- **Always-error checks are skipped during collection.** The task temporarily disables
  [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md),
  [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md),
  and [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md) because it
  can't demote them, and acknowledging them for backwards compatibility would be wrong: an
  unexplained exemption needs its author, an internal type has to leave the signature, and a
  non-transitive dependency is fixed in the build script by declaring it with `api`, which breaks
  no consumer. `@IntentionallyNonTransitiveDependency` is reserved for dependencies the runtime
  environment supplies, which only the author knows. Ordinary compilations still enforce all
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
