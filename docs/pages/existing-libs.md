# Adding the plugin to existing libraries

A change to the public API of a published library breaks its users. So the first run of the
Watchdog reports many diagnostics that the author can't act on. The `exemptExistingApi` task
acknowledges all of them in one run:

```bash
./gradlew exemptExistingApi
```

Run it on a clean working tree. The task edits the source files.

## What the task changes

The task compiles the main sources of every target and edits every reported declaration. Each
declaration gets the `@Intentionally*` annotation of its diagnostic with
`ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`. The annotation goes below the KDoc and above the
other annotations of the declaration. The task adds the imports.

Three diagnostics get a different fix:

- [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md): a placeholder KDoc instead of
  `@IntentionallyUndocumented`, because documentation breaks no user.
- [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](./checks/subclass-opt-in-without-markers.md): a markerless
  `@SubclassOptInRequired` restricts no subclass, so the task replaces it with
  [`@IntentionallyOpen`](./checks/open-api-without-subclass-opt-in.md). The class stays open to everyone.
- [`DSL_MARKER_NOOP_TYPE_POSITION`](./checks/special/dsl-marker-noop-type-position.md): no exemption
  annotation exists. The task reports the diagnostic as a warning. Move or remove the marker by hand.

The placeholder KDoc satisfies the check until the real documentation replaces it:

```kotlin
/** TODO: Provide kDocs for this declaration */
public class Config
```

## Review the result

The task writes the [API Watchdog report](./report.md). Its not-applied section lists the
diagnostics that need manual attention. The console prints the same list as warnings.
`generateApiWatchdogReport` writes the same report and changes no source.

Review the diff and commit it. The checks then report only the API that you add later. For a new
declaration, fix the shape or pick the exemption reason by hand.

A second run adds nothing, because an exempted declaration reports no diagnostic.

## Collection mode

`exemptExistingApi` and `generateApiWatchdogReport` compile the main sources in collection mode.
When neither task is in the task graph, the compilations run as configured. Collection mode changes
three things:

- Explicit API mode is `warning`.
- Every enabled configurable check reports a warning. `ERROR` and `WARNING` give the same result.
  A check set to `NONE` in `apiWatchdog` records nothing and gets no exemption.
- The three always-error checks are off, because `FOR_BACKWARDS_COMPATIBILITY` isn't a reason for
  them:
  - [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md): only the
    author of the exemption can write its reason.
  - [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md): the
    internal type has to leave the signature.
  - [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md):
    declare the dependency with `api` in the build script. This breaks no consumer.

Any other compilation error still fails the task. Fix unresolved references and syntax errors first.
The compilations outside collection mode still report the three always-error checks, so fix them
after you commit the generated exemptions.
