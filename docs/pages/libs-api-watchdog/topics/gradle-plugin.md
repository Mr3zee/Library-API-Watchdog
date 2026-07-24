# Gradle plugin reference

Applying the Gradle plugin (plugin id `org.jetbrains.kotlinx.libs.api.watchdog`) adds the
`apiWatchdog` extension and the `updateBackwardsCompatibilityExempts` task. The extension
configures the severity of every check and two setup suggestions the plugin makes; the task
acknowledges the diagnostics of an already-shipped API in one sweep. See [Setup](setup.md) for
applying the plugin and
[Exemptions and internal API](exemptions.md) for silencing a single declaration instead of
changing severity project-wide.

## The apiWatchdog extension

Every property has a default, so a full sample only needs to set the ones you want to change.
This one sets all of them, for reference:

```kotlin
import org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity

apiWatchdog {
    openApiWithoutSubclassOptIn = WatchdogSeverity.WARNING
    subclassOptInWithoutMarkers = WatchdogSeverity.WARNING
    exhaustivePublicApi = WatchdogSeverity.WARNING
    undocumentedPublicApi = WatchdogSeverity.NONE
    functionTypeAliasPublicApi = WatchdogSeverity.WARNING
    dataClassPublicApi = WatchdogSeverity.WARNING
    statefulClassWithoutToString = WatchdogSeverity.WARNING
    mutableCollectionPublicApi = WatchdogSeverity.WARNING
    pairOrTriplePublicApi = WatchdogSeverity.WARNING
    booleanParameterPublicApi = WatchdogSeverity.WARNING
    nullableBooleanPublicApi = WatchdogSeverity.WARNING
    requiredParameterAfterOptional = WatchdogSeverity.WARNING
    inconsistentParameterOrderInOverloads = WatchdogSeverity.WARNING
    inlineFunctionWithLogic = WatchdogSeverity.WARNING
    dslMarkerNoopTarget = WatchdogSeverity.WARNING
    dslMarkerWithoutExplicitTargets = WatchdogSeverity.WARNING
    dslMarkerNoopTypePosition = WatchdogSeverity.WARNING
    suggestTapmoc = true
    suggestAbiValidation = true

    javaInterop {
        // One switch for the whole Java interop group; it overrides the severities below.
        enabled = true
        mangledJvmNamePublicApi = WatchdogSeverity.WARNING
        kotlinOnlyApiWithoutJvmSynthetic = WatchdogSeverity.WARNING
        companionApiWithoutJvmStatic = WatchdogSeverity.WARNING
        companionConstantWithoutJvmField = WatchdogSeverity.WARNING
        topLevelApiWithoutJvmName = WatchdogSeverity.WARNING
        defaultParametersWithoutJvmOverloads = WatchdogSeverity.WARNING
    }
}
```

## Severity semantics

Each check's severity is a `WatchdogSeverity`:

| Value | Effect |
|---|---|
| `ERROR` | Fails the compilation. This is the default for every configurable diagnostic. |
| `WARNING` | Reported as a compiler warning; the build still succeeds. |
| `NONE` | The check is disabled entirely: the plugin does not run it, so it costs nothing. |

`EXEMPTION_WITHOUT_EXPLANATION` has no matching property and is always an error: an exemption
annotation that explains nothing is a mistake worth failing the build over, not a matter of
project taste. See [Exemptions and internal API](exemptions.md).

The Kotlin compiler hides regular warnings once a compilation fails with errors. If a project
still has other checks at `ERROR`, a diagnostic demoted to `WARNING` only becomes visible in a
build that already fails, and only when that build also passes `-Xreport-all-warnings`.

## Property reference

| Property | Diagnostic | Check |
|---|---|---|
| `openApiWithoutSubclassOptIn` | `OPEN_API_WITHOUT_SUBCLASS_OPT_IN` | [Open API without subclass opt-in](open-api-without-subclass-opt-in.md) |
| `subclassOptInWithoutMarkers` | `SUBCLASS_OPT_IN_WITHOUT_MARKERS` | [Subclass opt-in without markers](subclass-opt-in-without-markers.md) |
| `exhaustivePublicApi` | `EXHAUSTIVE_PUBLIC_API` | [Exhaustive public API](exhaustive-public-api.md) |
| `undocumentedPublicApi` | `UNDOCUMENTED_PUBLIC_API` | [Undocumented public API](undocumented-public-api.md) |
| `functionTypeAliasPublicApi` | `FUNCTION_TYPE_ALIAS_PUBLIC_API` | [Function type aliases in public API](function-type-alias-public-api.md) |
| `dataClassPublicApi` | `DATA_CLASS_PUBLIC_API` | [Data classes in public API](data-class-public-api.md) |
| `statefulClassWithoutToString` | `STATEFUL_CLASS_WITHOUT_TO_STRING` | [Stateful classes without toString](stateful-class-without-to-string.md) |
| `mutableCollectionPublicApi` | `MUTABLE_COLLECTION_PUBLIC_API` | [Mutable collections in public API](mutable-collection-public-api.md) |
| `pairOrTriplePublicApi` | `PAIR_OR_TRIPLE_PUBLIC_API` | [Pair and Triple in public API](pair-or-triple-public-api.md) |
| `booleanParameterPublicApi` | `BOOLEAN_PARAMETER_PUBLIC_API` | [Boolean parameters in public API](boolean-parameter-public-api.md) |
| `nullableBooleanPublicApi` | `NULLABLE_BOOLEAN_PUBLIC_API` | [Nullable Booleans in public API](nullable-boolean-public-api.md) |
| `requiredParameterAfterOptional` | `REQUIRED_PARAMETER_AFTER_OPTIONAL` | [Required parameters after optional ones](required-parameter-after-optional.md) |
| `inconsistentParameterOrderInOverloads` | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` | [Inconsistent parameter order in overloads](inconsistent-parameter-order-in-overloads.md) |
| `inlineFunctionWithLogic` | `INLINE_FUNCTION_WITH_LOGIC` | [Inline functions with logic](inline-function-with-logic.md) |
| `dslMarkerNoopTarget` | `DSL_MARKER_NOOP_TARGET` | [DSL markers with no-op targets](dsl-marker-noop-target.md) |
| `dslMarkerWithoutExplicitTargets` | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` | [DSL markers without explicit targets](dsl-marker-without-explicit-targets.md) |
| `dslMarkerNoopTypePosition` | `DSL_MARKER_NOOP_TYPE_POSITION` | [DSL markers on no-op type positions](dsl-marker-noop-type-position.md) |
| `javaInterop.mangledJvmNamePublicApi` | `MANGLED_JVM_NAME_PUBLIC_API` | [Mangled JVM names in public API](mangled-jvm-name-public-api.md) |
| `javaInterop.kotlinOnlyApiWithoutJvmSynthetic` | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` | [Kotlin-only API without JvmSynthetic](kotlin-only-api-without-jvm-synthetic.md) |
| `javaInterop.companionApiWithoutJvmStatic` | `COMPANION_API_WITHOUT_JVM_STATIC` | [Companion API without JvmStatic](companion-api-without-jvm-static.md) |
| `javaInterop.companionConstantWithoutJvmField` | `COMPANION_CONSTANT_WITHOUT_JVM_FIELD` | [Companion constants without JvmField](companion-constant-without-jvm-field.md) |
| `javaInterop.topLevelApiWithoutJvmName` | `TOP_LEVEL_API_WITHOUT_JVM_NAME` | [Top-level API without JvmName](top-level-api-without-jvm-name.md) |
| `javaInterop.defaultParametersWithoutJvmOverloads` | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS` | [Default parameters without JvmOverloads](default-parameters-without-jvm-overloads.md) |

The last six properties live inside the `javaInterop { }` block. They only run in JVM
compilations, and `javaInterop.enabled` (default `true`) is a single switch for all of them: set
it to `false` and every one of the six resolves to `NONE`, no matter what its own property says.
See [Java interop checks](java-interop.md) for the group as a whole.

## The updateBackwardsCompatibilityExempts task

A library that has already shipped cannot change the shape of its public API without breaking
clients, so the watchdog's first run typically floods it with diagnostics that are not actionable
anymore. `updateBackwardsCompatibilityExempts` acknowledges all of them at once:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

The task depends on every regular main Kotlin compilation exposed by KGP. Each JVM, JS, Native,
Wasm, and metadata compilation records the diagnostics it sees together with their exact source
positions. The task merges those reports, removes exact duplicates from common sources compiled
for several targets, and runs only the PSI fixer in a separate process. It never invokes a Kotlin
compiler of its own. Each diagnostic gets the matching `@Intentionally*` annotation with
`reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, placed under the declaration's KDoc and
above its other annotations, with imports added as needed. `FOR_BACKWARDS_COMPATIBILITY` explains
itself, so the inserted exemptions satisfy the
[explanation requirement](exemption-without-explanation.md) as they are.

Details worth knowing:

- **Run it on a clean working tree and review the diff.** The task edits sources in place. It is
  meant for adoption: acknowledge the shipped API wholesale, commit, and let the checks guard
  only the API added afterwards. New code deserves a deliberate decision instead - fix the shape,
  or pick the honest reason by hand.
- **Collection mode is task-scoped.** Selecting `updateBackwardsCompatibilityExempts` internally
  makes regular main compilations write reports, forces explicit API warning mode, and temporarily
  demotes every enabled configurable watchdog diagnostic to a warning so the fixer can run.
  Ordinary compilation tasks are unchanged when the update task is not in the task graph.
- **Severity configuration is respected.** A check set to `NONE` in `apiWatchdog` records nothing
  and gets no exemptions; `ERROR` and `WARNING` are exempted alike.
- **Explicit API mode is not required yet.** The analysis forces `-Xexplicit-api=warning`, so the
  task also prepares a library that has not enabled explicit API mode - useful for adding the
  exemptions before flipping the switch.
- **Real compilation errors still stop the task.** Since these are the project's regular compiler
  tasks, unresolved references, syntax errors, and the always-error
  [`EXEMPTION_WITHOUT_EXPLANATION`](exemption-without-explanation.md) must be fixed before the PSI
  fixer can run.
- **Some diagnostics have no annotation to add.** [`EXEMPTION_WITHOUT_EXPLANATION`](exemption-without-explanation.md)
  needs a reason or description only its author can write and blocks compilation before the task;
  [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](subclass-opt-in-without-markers.md) is fixed by passing
  marker classes, [`DSL_MARKER_NOOP_TYPE_POSITION`](dsl-marker-noop-type-position.md) is fixed by
  moving or removing the marker, and `UNDOCUMENTED_PUBLIC_API` on an enum entry has no applicable
  annotation target. The latter cases are listed as build warnings for manual follow-up.
- **Running it twice is safe.** Exempted diagnostics are no longer reported, so a second run
  finds nothing left to do.

## Tapmoc suggestion

`suggestTapmoc` (default `true`) controls a build warning, unrelated to any diagnostic: if
[Tapmoc](https://github.com/GradleUp/Tapmoc) is not applied alongside the watchdog, the plugin
warns that the library's Java and Kotlin compatibility levels are not pinned. Set it to `false` to
silence the warning:

```kotlin
apiWatchdog {
    suggestTapmoc = false
}
```

See [Tapmoc suggestion](tapmoc-suggestion.md) for what the warning covers and why.

## Binary compatibility validation suggestion

`suggestAbiValidation` (default `true`) controls a second build warning, also unrelated to any
diagnostic: if neither the Kotlin Gradle plugin's built-in ABI validation nor the standalone
Binary Compatibility Validator plugin is enabled alongside the watchdog, the plugin warns that
incompatible changes to already-shipped API would go unnoticed. Set it to `false` to silence the
warning:

```kotlin
apiWatchdog {
    suggestAbiValidation = false
}
```

See [Binary compatibility validation suggestion](abi-validation-suggestion.md) for what the
warning covers and why.

## The compiler CLI equivalent

Without Gradle, the same severities are configured directly on the compiler command line with a
repeatable plugin option, `diagnosticSeverity`. Pass one instance per diagnostic you want to move
off its default:

```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=<DIAGNOSTIC_NAME>:error|warning|none
```

For example, to demote undocumented public API to a warning:

```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
```

`<DIAGNOSTIC_NAME>` is any name from the Property reference table above; the value is `error`,
`warning`, or `none`. This is exactly what the Gradle extension compiles down to: each property
becomes one such option. There is no CLI switch matching `javaInterop.enabled`; to turn the whole
group off from the command line, pass `:none` for each of its six diagnostics.
