# Configuration

Configuration options for `libs-api-watchdog`.

## Apply the Gradle plugin

```kotlin
plugins {
    kotlin("libs.api.watchdog") version "0.1.0"
}
```

libs-api-watchdog is a Kotlin compiler plugin. It needs a Gradle
project that applies the Kotlin plugin and turns on
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode):

```kotlin
kotlin {
    explicitApi()
}
```

The `-Xexplicit-api` compiler flag, and the `warning` variant of either form, also count. Without
explicit API mode enabled, `libs-api-watchdog` registers no checks at all: there is no public API contract
to watch. The Gradle plugin prints a build warning when explicit API mode is not enabled.

## What applying does

Applying the plugin:

- Registers the compiler plugin for every compilation in the project except test compilations:
  test sources are not published, so they carry no API contract to watch.
- Adds a dependency on `org.jetbrains.kotlin:libs-api-watchdog-plugin-annotations`, a runtime library with the `@Intentionally*` exemption annotations.
- Warns when explicit API mode is not enabled.
- Checks whether binary compatibility validation is enabled alongside it, printing a
  build warning with a setup snippet for either one that is missing. See below.

## Errors by default

`libs-api-watchdog` is intentionally restrictive by default: every check reports a compilation error until
configured otherwise. See [The apiWatchdog extension](./configuration.md#the-apiwatchdog-extension)
for demoting individual checks to warnings or disabling them, and
[Exemptions and internal API](./exemptions.md) for exempting a single declaration in place instead of
changing severity project-wide.

## Adding the plugin to existing libraries

When added to an exiting library, chances are that the whole codebase will turn red,
and most of the declarations reported can't be changes easily without breaking users.

Use `updateBackwardsCompatibilityExempts` task to mark existing APIs with `@Intentionally*`
annotations and `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

See [Adding the plugin to existing libraries](./existing-libs.md) for more details.

## Without Gradle

When invoking the compiler directly, configure severities with the repeatable plugin option:

```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=<NAME>:<severity>
```

Parameters:
- `<NAME>` is a diagnostic name. Any value from the [Property reference](./configuration.md#property-reference) is valid.
- `<severity>` is `error`, `warning`, or `none`.

The `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` check is unavailable without Gradle because the
compiler alone cannot distinguish dependencies declared with `api` from those declared with
`implementation`.

For example, to demote undocumented public API to a warning use the following argument form:

```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
```


## The apiWatchdog extension

Every property has a default value. For configurable checks it is `WatchdogSeverity.ERROR`,
for enabled/disabled switches is it `true` by default.

See the list of configurable properties:

```kotlin
apiWatchdog {
    suggestAbiValidation = true
    publicTypesMustBeTransitiveDependencies = true

    openApiWithoutSubclassOptIn = WatchdogSeverity.ERROR
    subclassOptInWithoutMarkers = WatchdogSeverity.ERROR
    exhaustivePublicApi = WatchdogSeverity.ERROR
    undocumentedPublicApi = WatchdogSeverity.ERROR
    functionTypeAliasPublicApi = WatchdogSeverity.ERROR
    dataClassPublicApi = WatchdogSeverity.ERROR
    statefulClassWithoutEquals = WatchdogSeverity.ERROR
    statefulClassWithoutHashCode = WatchdogSeverity.ERROR
    statefulClassWithoutToString = WatchdogSeverity.ERROR
    mutableCollectionPublicApi = WatchdogSeverity.ERROR
    pairOrTriplePublicApi = WatchdogSeverity.ERROR
    booleanParameterPublicApi = WatchdogSeverity.ERROR
    nullableBooleanPublicApi = WatchdogSeverity.ERROR
    requiredParameterAfterOptional = WatchdogSeverity.ERROR
    inconsistentParameterOrderInOverloads = WatchdogSeverity.ERROR
    inlineFunctionWithLogic = WatchdogSeverity.ERROR
    dslMarkerNoopTarget = WatchdogSeverity.ERROR
    dslMarkerWithoutExplicitTargets = WatchdogSeverity.ERROR
    dslMarkerNoopTypePosition = WatchdogSeverity.ERROR
    publicTypeWithInternalApi = WatchdogSeverity.ERROR

    javaInterop {
        // One switch for the whole Java interop group,
        // it overrides the severities below.
        enabled = true

        mangledJvmNamePublicApi = WatchdogSeverity.ERROR
        kotlinOnlyApiWithoutJvmSynthetic = WatchdogSeverity.ERROR
        companionApiWithoutJvmStatic = WatchdogSeverity.ERROR
        companionConstantWithoutJvmField = WatchdogSeverity.ERROR
        topLevelApiWithoutJvmName = WatchdogSeverity.ERROR
        defaultParametersWithoutJvmOverloads = WatchdogSeverity.ERROR
    }
}
```

## Severity semantics

Each check's severity is a `WatchdogSeverity`:

| Value     | Effect                                                                        |
|-----------|-------------------------------------------------------------------------------|
| `ERROR`   | Fails the compilation. This is the default for every configurable diagnostic. |
| `WARNING` | Reported as a compiler warning, the build still succeeds.                     |
| `NONE`    | The check is disabled entirely.                                               |

`EXEMPTION_WITHOUT_EXPLANATION` has no matching property and is always an error. The dependency
exposure diagnostic is also always an error when enabled; its Gradle property is a Boolean
whole-check switch rather than a severity. See [Exemptions and internal API](./exemptions.md).

## Property reference

| Property                                           | Check                                                                                                                   | Diagnostic                                   |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `publicTypesMustBeTransitiveDependencies`          | [Public types from non-transitive dependencies](checks/special/public-type-from-non-transitive-dependency.md)           | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` |
| `openApiWithoutSubclassOptIn`                      | [Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md)                                        | `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`           |
| `subclassOptInWithoutMarkers`                      | [Subclass opt-in without markers](./checks/subclass-opt-in-without-markers.md)                                          | `SUBCLASS_OPT_IN_WITHOUT_MARKERS`            |
| `exhaustivePublicApi`                              | [Exhaustive public API](./checks/exhaustive-public-api.md)                                                              | `EXHAUSTIVE_PUBLIC_API`                      |
| `undocumentedPublicApi`                            | [Undocumented public API](./checks/undocumented-public-api.md)                                                          | `UNDOCUMENTED_PUBLIC_API`                    |
| `functionTypeAliasPublicApi`                       | [Function type aliases in public API](./checks/function-type-alias-public-api.md)                                       | `FUNCTION_TYPE_ALIAS_PUBLIC_API`             |
| `dataClassPublicApi`                               | [Data classes in public API](./checks/data-class-public-api.md)                                                         | `DATA_CLASS_PUBLIC_API`                      |
| `statefulClassWithoutEquals`                       | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `STATEFUL_CLASS_WITHOUT_EQUALS`              |
| `statefulClassWithoutHashCode`                     | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `STATEFUL_CLASS_WITHOUT_HASH_CODE`           |
| `statefulClassWithoutToString`                     | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `STATEFUL_CLASS_WITHOUT_TO_STRING`           |
| `mutableCollectionPublicApi`                       | [Mutable collections in public API](./checks/mutable-collection-public-api.md)                                          | `MUTABLE_COLLECTION_PUBLIC_API`              |
| `pairOrTriplePublicApi`                            | [Pair and Triple in public API](./checks/pair-or-triple-public-api.md)                                                  | `PAIR_OR_TRIPLE_PUBLIC_API`                  |
| `booleanParameterPublicApi`                        | [Boolean parameters in public API](./checks/boolean-parameter-public-api.md)                                            | `BOOLEAN_PARAMETER_PUBLIC_API`               |
| `nullableBooleanPublicApi`                         | [Nullable Booleans in public API](./checks/nullable-boolean-public-api.md)                                              | `NULLABLE_BOOLEAN_PUBLIC_API`                |
| `requiredParameterAfterOptional`                   | [Required parameters after optional ones](./checks/required-parameter-after-optional.md)                                | `REQUIRED_PARAMETER_AFTER_OPTIONAL`          |
| `inconsistentParameterOrderInOverloads`            | [Inconsistent parameter order in overloads](./checks/inconsistent-parameter-order-in-overloads.md)                      | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`  |
| `inlineFunctionWithLogic`                          | [Inline functions with logic](./checks/inline-function-with-logic.md)                                                   | `INLINE_FUNCTION_WITH_LOGIC`                 |
| `dslMarkerNoopTarget`                              | [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md)                                            | `DSL_MARKER_NOOP_TARGET`                     |
| `dslMarkerWithoutExplicitTargets`                  | [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md)                         | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`        |
| `dslMarkerNoopTypePosition`                        | [DSL markers on no-op type positions](./checks/special/dsl-marker-noop-type-position.md)                                | `DSL_MARKER_NOOP_TYPE_POSITION`              |
| `publicTypeWithInternalApi`                        | [Public types marked as internal API](./checks/special/public-type-with-internal-api.md)                                | `PUBLIC_TYPE_WITH_INTERNAL_API`              |
| `javaInterop.mangledJvmNamePublicApi`              | [Mangled JVM names in public API](./checks/java-interop/mangled-jvm-name-public-api.md)                                 | `MANGLED_JVM_NAME_PUBLIC_API`                |
| `javaInterop.kotlinOnlyApiWithoutJvmSynthetic`     | [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md)                  | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`      |
| `javaInterop.companionApiWithoutJvmStatic`         | [Companion API without JvmStatic](./checks/java-interop/companion-api-without-jvm-static.md)                            | `COMPANION_API_WITHOUT_JVM_STATIC`           |
| `javaInterop.companionConstantWithoutJvmField`     | [Companion constants without JvmField](./checks/java-interop/companion-constant-without-jvm-field.md)                   | `COMPANION_CONSTANT_WITHOUT_JVM_FIELD`       |
| `javaInterop.topLevelApiWithoutJvmName`            | [Top-level API without JvmName](./checks/java-interop/top-level-api-without-jvm-name.md)                                | `TOP_LEVEL_API_WITHOUT_JVM_NAME`             |
| `javaInterop.defaultParametersWithoutJvmOverloads` | [Default parameters without JvmOverloads](./checks/java-interop/default-parameters-without-jvm-overloads.md)            | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`   |

The last six properties live inside the `javaInterop { }` block. They only run in JVM
compilations, and `javaInterop.enabled` (default `true`) is a single switch for all of them: set
it to `false` and every one of the six resolves to `NONE`, no matter what its own property says.
See [Java interop checks](./checks/java-interop/java-interop.md) for more details.

## Binary compatibility validation suggestion

`suggestAbiValidation` (default is `true`) controls a build warning unrelated to any
diagnostic. If neither the Kotlin Gradle plugin's built-in ABI validation nor the standalone
Binary Compatibility Validator plugin is enabled alongside the watchdog, the plugin warns that
incompatible changes to already-shipped API would go unnoticed. Set it to `false` to silence the
warning:

```kotlin
apiWatchdog {
    suggestAbiValidation = false
}
```

See [Binary compatibility validation suggestion](./abi-validation-suggestion.md) for more details.

## Next steps

- [Adding the plugin to existing libraries](./existing-libs.md)
- [Exemptions and internal API](./exemptions.md)
- [Binary compatibility validation suggestion](./abi-validation-suggestion.md)
- [API reference](https://mr3zee.github.io/libs-api-watchdog/api/)
