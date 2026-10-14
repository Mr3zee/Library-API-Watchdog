# Configuration

The Gradle plugin, the `apiWatchdog` extension, and the compiler options of `library-api-watchdog`.

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

Apply the plugin:

```kotlin build.gradle.kts
plugins {
    kotlin("library.api-watchdog") version "{{libraryApiWatchdogVersion}}"
}
```

The checks run only in a module with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
on. Turn it on:

```kotlin build.gradle.kts
kotlin {
    explicitApi()
}
```

`explicitApiWarning()` and the `-Xexplicit-api` compiler flag also count. Without explicit API
mode the plugin registers no checks.

## What applying does

Applying the plugin:

- Registers the compiler plugin for every compilation except test compilations.
- Adds the `org.jetbrains.kotlinx:kotlin-library-api-watchdog-plugin-annotations` dependency,
  the library with the `@Intentionally*` exemption annotations.
- Warns when explicit API mode is off.
- Warns when binary compatibility validation is off, with a setup snippet.
  [Binary compatibility validation suggestion](./abi-validation-suggestion.md) gives the details.

## The `apiWatchdog` extension

The sample lists every property. A severity or a switch shows its default value:

```kotlin build.gradle.kts
apiWatchdog {
    // !link[/enabled/] /configuration#disabling-the-watchdog
    enabled = true
    // !link[/suggestAbiValidation/] /abi-validation-suggestion
    suggestAbiValidation = true
    // !link[/publicTypesMustBeTransitiveDependencies/] /checks/special/public-type-from-non-transitive-dependency
    publicTypesMustBeTransitiveDependencies = true
    // !link[/publicTypeWithInternalApi/] /checks/special/public-type-with-internal-api
    publicTypeWithInternalApi = true
    // !link[/reportUndocumentedClassProperties/] /checks/undocumented-public-api#class-properties
    reportUndocumentedClassProperties = false
    // !link[/internalApiAnnotations/] /configuration#internal-api-annotations
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
    // !link[/ignore/] /configuration#annotation-ignore-rules
    ignore(WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS, whenAnnotatedWith = listOf("com.example.GeneratedValueMembers"))
    // !link[/reports/] /report#selecting-the-formats
    reports {
        html = true
        htmlFile = layout.buildDirectory.file("reports/api-watchdog/report.html")
        sarif = true
        sarifFile = layout.buildDirectory.file("reports/api-watchdog/report.sarif")
    }

    // !link[/openApiWithoutSubclassOptIn/] /checks/open-api-without-subclass-opt-in
    openApiWithoutSubclassOptIn = WatchdogSeverity.ERROR
    // !link[/subclassOptInWithoutMarkers/] /checks/subclass-opt-in-without-markers
    subclassOptInWithoutMarkers = WatchdogSeverity.ERROR
    // !link[/exhaustivePublicApi/] /checks/exhaustive-public-api
    exhaustivePublicApi = WatchdogSeverity.ERROR
    // !link[/undocumentedPublicApi/] /checks/undocumented-public-api
    undocumentedPublicApi = WatchdogSeverity.WARNING
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

## Disabling the watchdog

`enabled = false` turns off every check in the project. The plugin still adds the annotations
library:

```kotlin build.gradle.kts
apiWatchdog {
    enabled = false
}
```

## Annotation ignore rules

`ignore` skips a check on every declaration that carries one of the listed annotations:

```kotlin build.gradle.kts
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks

apiWatchdog {
    ignore(
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS,
        whenAnnotatedWith = listOf(
            "com.example.GeneratedValueMembers",
            "com.example.GeneratedEntityMembers",
        ),
    )
}
```

`ignore` doesn't work for always-error checks. See [Exemptions and internal API](./exemptions.md)
for more details about when to use a rule.

## Internal API annotations

`internalApiAnnotations` lists annotation classes by their fully qualified
names. A listed annotation counts as an internal API annotation:

```kotlin build.gradle.kts
apiWatchdog {
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
}
```

See [Internal API annotations](./exemptions.md#internal-api-annotations) for more details.

## Exempt non-transitive dependencies

A dependency declared on the `apiWatchdogExemptNonTransitive` configuration is exempt from
[Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md)
in the whole module:

```kotlin build.gradle.kts
dependencies {
    compileOnly(libs.kotlin.gradle.plugin)

    apiWatchdogExemptNonTransitive(libs.kotlin.gradle.plugin)
}
```

To exempt a whole configuration, extend `apiWatchdogExemptNonTransitive` from it:

```kotlin build.gradle.kts
configurations.apiWatchdogExemptNonTransitive {
    extendsFrom(configurations.compileOnly.get())
}
```

See [Exempting a dependency](./checks/special/public-type-from-non-transitive-dependency.md#exempting-a-dependency)
for more details.

## Severities

A severity property holds a `WatchdogSeverity`:

| Value     | Effect                                                                           |
|-----------|----------------------------------------------------------------------------------|
| `ERROR`   | Fails the compilation. The default, except for `undocumentedPublicApi`.          |
| `WARNING` | Reports a compiler warning. The build succeeds. Default for `undocumentedPublicApi`. |
| `NONE`    | Turns the check off.                                                             |

Three checks have no severity property:

- [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md): always an
  error.
- [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md): always an
  error while on. Turn off with `publicTypeWithInternalApi`.
- [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md):
  always an error while on. Turn off with `publicTypesMustBeTransitiveDependencies`.

## Property reference

Each configurable check, its property, and its diagnostic name:

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

These properties are in the `javaInterop { }` block. The checks run only in JVM compilations.
`javaInterop.enabled` (default `true`) is one switch for all six: `false` turns every one off,
whatever its own severity. 

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

A direct compiler call configures the plugin with `-P` options of this form:

```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:<option>=<value>
```

| Option                      | Value                                                  | Gradle equivalent             |
|-----------------------------|--------------------------------------------------------|-------------------------------|
| `diagnosticSeverity`        | `<NAME>:<severity>`. Repeatable.                       | The severity property.        |
| `ignoreWhenAnnotated`       | `<NAME>:<annotation class name>`. Repeatable.          | `ignore`                      |
| `internalApiAnnotation`     | `<annotation class name>`. Repeatable.                 | `internalApiAnnotations`      |
| `publicTypeWithInternalApi` | `true` or `false`.                                     | `publicTypeWithInternalApi`   |
| `reportUndocumentedClassProperties` | `true` or `false`.                             | `reportUndocumentedClassProperties` |

`<NAME>` is a diagnostic name from the [Property reference](#property-reference). `<severity>` is
`error`, `warning`, or `none`. A diagnostic that no option names has its default severity. One
value of each option:

```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:error
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:ignoreWhenAnnotated=STATEFUL_CLASS_WITHOUT_EQUALS:com.example.GeneratedValueMembers
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:internalApiAnnotation=com.example.dependency.InternalDependencyApi
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:publicTypeWithInternalApi=false
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:reportUndocumentedClassProperties=true
```

[`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md)
doesn't run without Gradle.

## Next steps

- [Adding the plugin to existing libraries](./existing-libs.md)
- [Exemptions and internal API](./exemptions.md)
- [Binary compatibility validation suggestion](./abi-validation-suggestion.md)
- [API reference](https://mr3zee.github.io/Library-API-Watchdog/api/)
