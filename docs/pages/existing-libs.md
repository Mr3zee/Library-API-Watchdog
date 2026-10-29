# Adding the plugin to existing libraries

A change to the public API of a published library breaks its users. So the first run of the
Watchdog reports many diagnostics that the author can't act on. The `exemptExistingApi` task
acknowledges every error in one run:

```bash
./gradlew exemptExistingApi
```

Run it on a clean working tree. The task edits the source files.

## What the task changes

The task compiles every compilation except tests and edits every declaration with a diagnostic
whose configured severity is `ERROR`. Each declaration gets the `@Intentionally*` annotation of its
diagnostic with `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`. The annotation goes below the KDoc
and above the other annotations of the declaration. The task adds the imports.

A diagnostic with the `WARNING` severity stays in the sources. The console prints the count, and
the [report](./report.md) lists each one. To exempt a check that is a warning, set it to
`WatchdogSeverity.ERROR` in `apiWatchdog` for the run. [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md)
is a warning by default, so the task adds no placeholder KDoc without this setting.

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
/** TODO: Document this declaration */
public class Config
```

## Review the result

The task writes the [API Watchdog report](./report.md). Its not-applied section lists the
diagnostics that need manual attention. The console prints the same list as warnings.

Review the diff and commit it. The checks then report only the API that you add later. For a new
declaration, fix the shape or pick the exemption reason by hand.

A second run adds nothing, because an exempted declaration reports no diagnostic.

## Collection mode

`exemptExistingApi` and `generateApiWatchdogReport` compile every compilation except tests in
collection mode. [The report page](./report.md) lists the compilations.
When neither task is in the task graph, the compilations run as configured. Collection mode changes
three things:

- A module without Explicit API mode on compiles with additional `-Xexplicit-api=warning`.
- Every enabled configurable check reports a warning, so the compilation passes. The configured
  severity still decides the fix: `exemptExistingApi` exempts a check set to `ERROR` and leaves a
  check set to `WARNING`. A check set to `NONE` in `apiWatchdog` records nothing.
- The three always-error checks are off, because `FOR_BACKWARDS_COMPATIBILITY` isn't a reason for
  them:
  - [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md): only the
    author of the exemption can write its reason.
  - [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md): the
    internal type has to leave the signature.
  - [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md):
    declare the dependency with `api` in the build script. This breaks no consumer.

`exemptExistingApi` removes the [redundant annotations](./checks/special/redundant-exemption.md), and `generateApiWatchdogReport` tags them.

Any other compilation error still fails the task. Fix unresolved references and syntax errors first.
The compilations outside collection mode still report the three always-error checks, so fix them
after you commit the generated exemptions.
