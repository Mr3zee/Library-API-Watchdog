# Configuration

Configuration options for `library-api-watchdog`.

## Apply the Gradle plugin

Add the Space EAP repository to the plugin and dependency repositories:

```kotlin settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kt-lib/eap")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kt-lib/eap")
        mavenCentral()
    }
}
```

Then apply the plugin:

```kotlin build.gradle.kts
plugins {
    kotlin("library.api-watchdog") version "{{libraryApiWatchdogVersion}}"
}
```

`library-api-watchdog` is a Kotlin compiler plugin. It needs a Gradle
project that applies the Kotlin plugin and turns on
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode):

```kotlin build.gradle.kts
kotlin {
    explicitApi()
}
```

The `-Xexplicit-api` compiler flag, and the `warning` variant of either form, also count. Without
explicit API mode enabled, `library-api-watchdog` registers no checks at all: there is no public API contract
to watch. The Gradle plugin prints a build warning when explicit API mode is not enabled.

## What applying does

Applying the plugin:

- Registers the compiler plugin for every compilation in the project except test compilations:
  test sources are not published, so they carry no API contract to watch.
- Adds a dependency on `org.jetbrains.kotlin:kotlin-library-api-watchdog-plugin-annotations`,
  a runtime library with the `@Intentionally*` exemption annotations.
- Warns when explicit API mode is not enabled.
- Checks whether binary compatibility validation is enabled alongside it, printing a
  build warning with a setup snippet when missing. 
  See [Binary compatibility validation suggestion](./abi-validation-suggestion.md) for more details.

## Errors by default

`library-api-watchdog` is intentionally restrictive by default: every check is enabled and 
reports a compilation error until configured otherwise. See [The apiWatchdog extension](./configuration.md#the-apiwatchdog-extension)
for demoting individual checks to warnings or disabling them, and
[Exemptions and internal API](./exemptions.md) for exempting a single declaration in place instead of
changing severity project-wide.

## Adding the plugin to existing libraries

When added to an exiting library, chances are that the whole codebase will turn red,
and most of the declarations reported can't be changed easily without breaking users.

Use `updateBackwardsCompatibilityExempts` task to mark existing APIs with `@Intentionally*`
annotations and `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

See [Adding the plugin to existing libraries](./existing-libs.md) for more details.

## The `apiWatchdog` extension

Every property has a default value. For configurable checks it is `WatchdogSeverity.ERROR`,
for enabled/disabled switches is it `true` by default.

See the list of configurable properties:

```kotlin build.gradle.kts
apiWatchdog {
    // !link[/suggestAbiValidation/] /abi-validation-suggestion
    suggestAbiValidation = true
    // !link[/publicTypesMustBeTransitiveDependencies/] /checks/special/public-type-from-non-transitive-dependency
    publicTypesMustBeTransitiveDependencies = true
    // !link[/publicTypeWithInternalApi/] /checks/special/public-type-with-internal-api
    publicTypeWithInternalApi = true

    // !link[/openApiWithoutSubclassOptIn/] /checks/open-api-without-subclass-opt-in
    openApiWithoutSubclassOptIn = WatchdogSeverity.ERROR
    // !link[/subclassOptInWithoutMarkers/] /checks/subclass-opt-in-without-markers
    subclassOptInWithoutMarkers = WatchdogSeverity.ERROR
    // !link[/exhaustivePublicApi/] /checks/exhaustive-public-api
    exhaustivePublicApi = WatchdogSeverity.ERROR
    // !link[/undocumentedPublicApi/] /checks/undocumented-public-api
    undocumentedPublicApi = WatchdogSeverity.ERROR
    // !link[/functionTypeAliasPublicApi/] /checks/function-type-alias-public-api
    functionTypeAliasPublicApi = WatchdogSeverity.ERROR
    // !link[/dataClassPublicApi/] /checks/data-class-public-api
    dataClassPublicApi = WatchdogSeverity.ERROR
    // !link[/statefulClassWithoutEquals/] /checks/stateful-class-without-equals-hashcode-to-string
    statefulClassWithoutEquals = WatchdogSeverity.ERROR
    // !link[/statefulClassWithoutHashCode/] /checks/stateful-class-without-equals-hashcode-to-string
    statefulClassWithoutHashCode = WatchdogSeverity.ERROR
    // !link[/statefulClassWithoutToString/] /checks/stateful-class-without-equals-hashcode-to-string
    statefulClassWithoutToString = WatchdogSeverity.ERROR
    // !link[/mutableCollectionPublicApi/] /checks/mutable-collection-public-api
    mutableCollectionPublicApi = WatchdogSeverity.ERROR
    // !link[/pairOrTriplePublicApi/] /checks/pair-or-triple-public-api
    pairOrTriplePublicApi = WatchdogSeverity.ERROR
    // !link[/booleanParameterPublicApi/] /checks/boolean-parameter-public-api
    booleanParameterPublicApi = WatchdogSeverity.ERROR
    // !link[/nullableBooleanPublicApi/] /checks/nullable-boolean-public-api
    nullableBooleanPublicApi = WatchdogSeverity.ERROR
    // !link[/requiredParameterAfterOptional/] /checks/required-parameter-after-optional
    requiredParameterAfterOptional = WatchdogSeverity.ERROR
    // !link[/inconsistentParameterOrderInOverloads/] /checks/inconsistent-parameter-order-in-overloads
    inconsistentParameterOrderInOverloads = WatchdogSeverity.ERROR
    // !link[/inlineFunctionWithLogic/] /checks/inline-function-with-logic
    inlineFunctionWithLogic = WatchdogSeverity.ERROR
    // !link[/dslMarkerNoopTarget/] /checks/special/dsl-marker-noop-target
    dslMarkerNoopTarget = WatchdogSeverity.ERROR
    // !link[/dslMarkerWithoutExplicitTargets/] /checks/special/dsl-marker-without-explicit-targets
    dslMarkerWithoutExplicitTargets = WatchdogSeverity.ERROR
    // !link[/dslMarkerNoopTypePosition/] /checks/special/dsl-marker-noop-type-position
    dslMarkerNoopTypePosition = WatchdogSeverity.ERROR

    // !link[/javaInterop/] /checks/java-interop/
    javaInterop {
        // One switch for the whole Java interop group,
        // it overrides the severities below.
        // !link[/enabled/] /checks/java-interop/
        enabled = true

        // !link[/mangledJvmNamePublicApi/] /checks/java-interop/mangled-jvm-name-public-api
        mangledJvmNamePublicApi = WatchdogSeverity.ERROR
        // !link[/kotlinOnlyApiWithoutJvmSynthetic/] /checks/java-interop/kotlin-only-api-without-jvm-synthetic
        kotlinOnlyApiWithoutJvmSynthetic = WatchdogSeverity.ERROR
        // !link[/companionApiWithoutJvmStatic/] /checks/java-interop/companion-api-without-jvm-static
        companionApiWithoutJvmStatic = WatchdogSeverity.ERROR
        // !link[/companionPropertyWithoutStaticAccess/] /checks/java-interop/companion-property-without-static-access
        companionPropertyWithoutStaticAccess = WatchdogSeverity.ERROR
        // !link[/topLevelApiWithoutJvmName/] /checks/java-interop/top-level-api-without-jvm-name
        topLevelApiWithoutJvmName = WatchdogSeverity.ERROR
        // !link[/defaultParametersWithoutJvmOverloads/] /checks/java-interop/default-parameters-without-jvm-overloads
        defaultParametersWithoutJvmOverloads = WatchdogSeverity.ERROR
    }
}
```

## Annotation ignore rules

Use `ignore` when annotations already guarantee the condition a check is looking for. Name the
check by its diagnostic name and the annotations by their fully qualified class names:

```kotlin
apiWatchdog {
    ignore(
        "STATEFUL_CLASS_WITHOUT_EQUALS",
        whenAnnotatedWith = listOf(
            "com.example.GeneratedValueMembers",
            "com.example.GeneratedEntityMembers",
        ),
    )
}
```

The diagnostic is ignored only on declarations directly carrying one of those annotations. Repeat
the call to associate the annotations with several checks. These rules are available for the
configurable diagnostics in the [Property reference](#property-reference). Always-error safety
checks can't be ignored by annotation.

## Severity semantics

Each check's severity is a `WatchdogSeverity`:

| Value     | Effect                                                                        |
|-----------|-------------------------------------------------------------------------------|
| `ERROR`   | Fails the compilation. This is the default for every configurable diagnostic. |
| `WARNING` | Reported as a compiler warning, the build still succeeds.                     |
| `NONE`    | The check is disabled entirely.                                               |

[`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md) has no matching property and is always an error.
[`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md) and
[`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md) are always errors when
enabled. Their Gradle properties are `Boolean` whole-check switches rather than severities. See
[Exemptions and internal API](./exemptions.md). 

The [`updateBackwardsCompatibilityExempts` task](./existing-libs.md) temporarily disables all three
while collecting diagnostics it can acknowledge automatically.

## Property reference

| Check                                                                                                                   | Property                                                | Diagnostic                                   |
|-------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|----------------------------------------------|
| [Boolean parameters in public API](./checks/boolean-parameter-public-api.md)                                            | `booleanParameterPublicApi`                             | `BOOLEAN_PARAMETER_PUBLIC_API`               |
| [Data classes in public API](./checks/data-class-public-api.md)                                                         | `dataClassPublicApi`                                    | `DATA_CLASS_PUBLIC_API`                      |
| [DSL markers on no-op type positions](./checks/special/dsl-marker-noop-type-position.md)                                | `dslMarkerNoopTypePosition`                             | `DSL_MARKER_NOOP_TYPE_POSITION`              |
| [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md)                                            | `dslMarkerNoopTarget`                                   | `DSL_MARKER_NOOP_TARGET`                     |
| [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md)                         | `dslMarkerWithoutExplicitTargets`                       | `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`        |
| [Exhaustive public API](./checks/exhaustive-public-api.md)                                                              | `exhaustivePublicApi`                                   | `EXHAUSTIVE_PUBLIC_API`                      |
| [Function type aliases in public API](./checks/function-type-alias-public-api.md)                                       | `functionTypeAliasPublicApi`                            | `FUNCTION_TYPE_ALIAS_PUBLIC_API`             |
| [Inconsistent parameter order in overloads](./checks/inconsistent-parameter-order-in-overloads.md)                      | `inconsistentParameterOrderInOverloads`                 | `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`  |
| [Inline functions with logic](./checks/inline-function-with-logic.md)                                                   | `inlineFunctionWithLogic`                               | `INLINE_FUNCTION_WITH_LOGIC`                 |
| [Mutable collections in public API](./checks/mutable-collection-public-api.md)                                          | `mutableCollectionPublicApi`                            | `MUTABLE_COLLECTION_PUBLIC_API`              |
| [Nullable Booleans in public API](./checks/nullable-boolean-public-api.md)                                              | `nullableBooleanPublicApi`                              | `NULLABLE_BOOLEAN_PUBLIC_API`                |
| [Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md)                                        | `openApiWithoutSubclassOptIn`                           | `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`           |
| [Pair and Triple in public API](./checks/pair-or-triple-public-api.md)                                                  | `pairOrTriplePublicApi`                                 | `PAIR_OR_TRIPLE_PUBLIC_API`                  |
| [Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md)         | `publicTypesMustBeTransitiveDependencies`               | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` |
| [Public types marked as internal API](./checks/special/public-type-with-internal-api.md)                                | `publicTypeWithInternalApi`                             | `PUBLIC_TYPE_WITH_INTERNAL_API`              |
| [Required parameters after optional ones](./checks/required-parameter-after-optional.md)                                | `requiredParameterAfterOptional`                        | `REQUIRED_PARAMETER_AFTER_OPTIONAL`          |
| [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `statefulClassWithoutEquals`                            | `STATEFUL_CLASS_WITHOUT_EQUALS`              |
| [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `statefulClassWithoutHashCode`                          | `STATEFUL_CLASS_WITHOUT_HASH_CODE`           |
| [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md) | `statefulClassWithoutToString`                          | `STATEFUL_CLASS_WITHOUT_TO_STRING`           |
| [Subclass opt-in without markers](./checks/subclass-opt-in-without-markers.md)                                          | `subclassOptInWithoutMarkers`                           | `SUBCLASS_OPT_IN_WITHOUT_MARKERS`            |
| [Undocumented public API](./checks/undocumented-public-api.md)                                                          | `undocumentedPublicApi`                                 | `UNDOCUMENTED_PUBLIC_API`                    |

### Java interop

These properties live inside the `javaInterop { }` block. They only run in JVM
compilations, and `javaInterop.enabled` (default `true`) is a single switch for all of them: set
it to `false` and every one of the six resolves to `NONE`, no matter what its own property says.
See [Java interop checks](./checks/java-interop/java-interop.md) for more details.

| Check                                                                                                         | Property                               | Diagnostic                                  |
|---------------------------------------------------------------------------------------------------------------|----------------------------------------|---------------------------------------------|
| [Companion function without JvmStatic](./checks/java-interop/companion-api-without-jvm-static.md)             | `companionApiWithoutJvmStatic`         | `COMPANION_API_WITHOUT_JVM_STATIC`          |
| [Companion property without static access](./checks/java-interop/companion-property-without-static-access.md) | `companionPropertyWithoutStaticAccess` | `COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`  |
| [Default parameters without JvmOverloads](./checks/java-interop/default-parameters-without-jvm-overloads.md)  | `defaultParametersWithoutJvmOverloads` | `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`  |
| [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md)        | `kotlinOnlyApiWithoutJvmSynthetic`     | `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`     |
| [Mangled JVM names in public API](./checks/java-interop/mangled-jvm-name-public-api.md)                       | `mangledJvmNamePublicApi`              | `MANGLED_JVM_NAME_PUBLIC_API`               |
| [Top-level API without JvmName](./checks/java-interop/top-level-api-without-jvm-name.md)                      | `topLevelApiWithoutJvmName`            | `TOP_LEVEL_API_WITHOUT_JVM_NAME`            |

## Without Gradle

When invoking the compiler directly, configure severities with the repeatable plugin option:

```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=<NAME>:<severity>
```

Parameters:
- `<NAME>` is a diagnostic name. Any value from the [Property reference](./configuration.md#property-reference) is valid.
- `<severity>` is `error`, `warning`, or `none`.

The [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md) check is not available without Gradle because the
compiler alone can't distinguish dependencies declared with `api` from those declared with
`implementation`.

For example, to demote undocumented public API to a warning use the following argument form:

```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
```

## Next steps

- [Adding the plugin to existing libraries](./existing-libs.md)
- [Exemptions and internal API](./exemptions.md)
- [Binary compatibility validation suggestion](./abi-validation-suggestion.md)
- [API reference](https://mr3zee.github.io/Library-API-Watchdog/api/)
