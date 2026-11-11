# Exemptions and internal API

Five mechanisms exclude a declaration from a check while the check stays enabled for the project:

| Mechanism                                           | Excludes                                              | From                       |
|-----------------------------------------------------|-------------------------------------------------------|----------------------------|
| [Exemption annotation](#exemption-annotations)      | One declaration                                       | One check                  |
| [Dependency exemption](#dependency-exemptions)      | Every use of a dependency's types in the module       | One check                  |
| [Internal API annotation](#internal-api-annotations) | One declaration and the declarations nested in it     | Every public API check     |
| [API filter](#api-filters)                          | Every declaration that the name and annotation rules leave out | Every public API check |
| [Annotation ignore rule](#annotation-ignore-rules)  | Every declaration with a given annotation             | One configurable check     |

## Exemption annotations

An `@Intentionally*` annotation exempts one declaration from one check. Use it when the
declaration has a reason to break the rule of the check, and state the reason on the annotation.

The "Exemption" section of each check page names the exemption annotation
([example for data classes](./checks/data-class-public-api.md#exemption)).

Every `@Intentionally*` annotation except
[`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`](#the-dsl-marker-exemption) has two parameters:

| Parameter     | Type              | Default |
|---------------|-------------------|---------|
| `description` | `String`          | `""`    |
| `reason`      | `ExemptionReason` | `OTHER` |

`FOR_BACKWARDS_COMPATIBILITY`, `API_DESIGN`, and `FOR_DEPRECATION` explain the exemption on their
own. Every other reason needs a `description`, or
[Exemptions without explanation](./checks/special/exemption-without-explanation.md) reports the
annotation. [Redundant exemptions](./checks/special/redundant-exemption.md) reports an exemption
that its check doesn't need.

An explained exemption:

```kotlin
// !hide-focused
/** Legacy RPC configuration. */
@IntentionallyDataClass(
    description = "Serialized as-is by the legacy RPC layer, " +
            "which reflects on componentN.",
    reason = ExemptionReason.INTEROP,
)
public data class LegacyConfig(
    // !hide-focused
    /** The network host that serves RPC requests. */
    public val host: String,
    // !hide-focused
    /** The TCP port exposed by [host]. */
    public val port: Int,
)
```

For a published API, the [`exemptExistingApi` task](./existing-libs.md) adds the exemptions.

### Exemptions in a multiplatform project

Put the exemption on the declaration that the check reports:

- A check that reports the `expect` declaration, such as
  [Boolean parameters in public API](./checks/boolean-parameter-public-api.md), reads the exemption
  there. The compiler reports `ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT` when an `actual` declaration
  doesn't have an annotation of its `expect` declaration, so put a copy on each `actual` declaration.
- A check that reports an `actual` declaration, such as
  [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md),
  reads an exemption on that `actual` declaration. It also reads an exemption on the `expect`
  declaration. That exemption then needs a copy on each `actual` declaration.

[`exemptExistingApi`](./existing-libs.md) adds the exemption where the check reports and the copies on
the `actual` declarations. See [Redundant exemptions](./checks/special/redundant-exemption.md) for
more details about an exemption that neither the `expect` declaration nor its `actual` declarations
need.

When the `actual` class declares a property as a `val` constructor parameter, put the copy of the
property's exemption on that constructor parameter.

#### Actual type aliases

For an `actual typealias`, the compiler requires the annotations of the `expect` class on the class
that the type alias expands to, not on the type alias. `exemptExistingApi` doesn't add a copy to that
class:

- A class of another module can't get an annotation.
- The exemption on the `expect` class doesn't exempt the class that the type alias expands to. An
  annotation on a class of the module is an exemption of that class only. When that class doesn't break
  the rule of the check, [Redundant exemptions](./checks/special/redundant-exemption.md) reports the
  annotation.

When the compiler reports `ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT` for an `actual typealias`, add
`@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")` to the type alias.

### The DSL marker exemption

`@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` exempts a DSL marker from
[DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md) and
[DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md).
It doesn't have a `reason` parameter, because backwards compatibility is the only valid reason for this
exemption. Its `description` is optional.

### All exemption annotations

| Annotation                                                     | Exempts                                                                                                                                                                                                             |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@IntentionallyOpen`                                           | [Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md)                                                                                                                                    |
| `@IntentionallyExhaustive`                                     | [Exhaustive public API](./checks/exhaustive-public-api.md)                                                                                                                                                          |
| `@IntentionallyUndocumented`                                   | [Undocumented public API](./checks/undocumented-public-api.md)                                                                                                                                                      |
| `@IntentionallyFunctionTypeAlias`                              | [Function type aliases in public API](./checks/function-type-alias-public-api.md)                                                                                                                                   |
| `@IntentionallyDataClass`                                      | [Data classes in public API](./checks/data-class-public-api.md)                                                                                                                                                     |
| `@IntentionallyWithoutEqualsHashCodeOrToString`                | All three [stateful class checks](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                                                     |
| `@IntentionallyWithoutEquals`                                  | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyWithoutHashCode`                                | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyWithoutToString`                                | [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md)                                                                                             |
| `@IntentionallyMutableCollection`                              | [Mutable collections in public API](./checks/mutable-collection-public-api.md)                                                                                                                                      |
| `@IntentionallyPairOrTriple`                                   | [Pair and Triple in public API](./checks/pair-or-triple-public-api.md)                                                                                                                                              |
| `@IntentionallyBooleanParameter`                               | [Boolean parameters in public API](./checks/boolean-parameter-public-api.md)                                                                                                                                        |
| `@IntentionallyNullableBoolean`                                | [Nullable Booleans in public API](./checks/nullable-boolean-public-api.md)                                                                                                                                          |
| `@IntentionallyRequiredParameterAfterOptional`                 | [Required parameters after optional ones](./checks/required-parameter-after-optional.md)                                                                                                                            |
| `@IntentionallyInconsistentParameterOrder`                     | [Inconsistent parameter order in overloads](./checks/inconsistent-parameter-order-in-overloads.md)                                                                                                                  |
| `@IntentionallyInlinedLogic`                                   | [Inline functions with logic](./checks/inline-function-with-logic.md)                                                                                                                                               |
| `@IntentionallyMangledJvmName`                                 | [Mangled JVM names in public API](./checks/java-interop/mangled-jvm-name-public-api.md)                                                                                                                             |
| `@IntentionallyKotlinOnlyApi`                                  | [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md)                                                                                                              |
| `@IntentionallyNonStaticCompanionApi`                          | [Companion function without JvmStatic](./checks/java-interop/companion-api-without-jvm-static.md) and [Companion property without static access](./checks/java-interop/companion-property-without-static-access.md) |
| `@IntentionallyDefaultFacadeName`                              | [Top-level API without JvmName](./checks/java-interop/top-level-api-without-jvm-name.md)                                                                                                                            |
| `@IntentionallyWithoutJvmOverloads`                            | [Default parameters without JvmOverloads](./checks/java-interop/default-parameters-without-jvm-overloads.md)                                                                                                        |
| `@IntentionallyNonTransitiveDependency`                        | [Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md)                                                                                                     |
| `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` | [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md) and [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md)                                    |

## Dependency exemptions

A dependency exemption exempts every use of a dependency's types in the module from
[Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md).
Use it instead of `@IntentionallyNonTransitiveDependency` on each declaration when the whole module
exposes the dependency's types. Declare the dependency on the `apiWatchdogExemptNonTransitive`
configuration:

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")

    apiWatchdogExemptNonTransitive("com.example:models")
}
```

[Exempting a dependency](./checks/special/public-type-from-non-transitive-dependency.md#exempting-a-dependency)
on the check page describes the configuration.

## Internal API annotations

An internal API annotation, such as `@InternalMyLibraryApi`, excludes a declaration from every
public API check. Use it when a declaration is public only for technical reasons and the library
doesn't give a compatibility guarantee for it.

Annotate the library's internal API annotation with `@InternalAnnotationMarker`:

```kotlin
// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalMyLibraryApi

@InternalMyLibraryApi // Internal API, not watched
public class ReflectionHelper
```

The public API checks skip every declaration with the marked annotation and every declaration
nested in it. Four checks still run there, because they check every visibility:
- [Exemptions without explanation](./checks/special/exemption-without-explanation.md),
- [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md),
- [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md), and
- [DSL markers on no-op type positions](./checks/special/dsl-marker-noop-type-position.md).

The marker annotation class itself is public API and stays watched.

A public declaration without the annotation must not use an internal API type in its signature.
[Internal API types in public API](./checks/special/public-type-with-internal-api.md) reports
the declaration as an error. A class is an internal API type when the class or an enclosing class
has the annotation. An annotation on the file doesn't make the classes of the file internal API
types, because an incremental build reads an unchanged class without the annotations of its file.

An annotation class from a dependency can't carry `@InternalAnnotationMarker`. List its fully
qualified name in `internalApiAnnotations` instead. The plugin then treats it like a marked
annotation. The name can have the [wildcards of an API filter](#filter-names):

```kotlin build.gradle.kts
apiWatchdog {
    internalApiAnnotations.add("com.example.dependency.InternalDependencyApi")
}
```

See [Without Gradle](./configuration.md#without-gradle) for more details about the compiler option.

## API filters

An API filter removes declarations from every public API check, by name or by annotation. The
watchdog doesn't have filters of its own. It copies the filters of the binary compatibility
validation of the project, so it doesn't check a declaration that the validation leaves out of the
ABI dump.

"A validation tool" on this page is the [ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) of the Kotlin Gradle plugin or the
[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) plugin. The watchdog reads the filters of a tool only while
the tool validates the project:

| Tool                                       | Validates the project when                                                                                                          | Filters that the watchdog reads                                      |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| ABI validation of the Kotlin Gradle plugin | The build script calls `abiValidation()` or `abiValidation { }`. On Kotlin 2.2 and 2.3, `enabled` is `true`.                        | The `filters` block of `abiValidation`.                              |
| Binary Compatibility Validator plugin      | The plugin is applied to the project or to a parent project, the project isn't in `ignoredProjects`, and `validationDisabled` is `false`. | The sets of the `apiValidation` block of the project that applies the plugin. |

When both tools validate the project, the watchdog copies the filters of the ABI validation of the
Kotlin Gradle plugin only. It doesn't merge the filters of the two tools.

The rules on this page have the names of the `filters` block of `abiValidation`. Each set of the
Binary Compatibility Validator plugin is one kind of rule:

| Plugin set         | Watchdog rule                                    |
|--------------------|--------------------------------------------------|
| `ignoredPackages`  | `exclude.byNames`, a package `p` as `p.**`       |
| `ignoredClasses`   | `exclude.byNames`, a `$` as a `.`                |
| `nonPublicMarkers` | `exclude.annotatedWith`                          |
| `publicPackages`   | `include.byNames`, a package `p` as `p.**`       |
| `publicClasses`    | `include.byNames`, a `$` as a `.`                |
| `publicMarkers`    | `include.annotatedWith`                          |

`useAbiValidationFilters = false` in the `apiWatchdog` extension turns the filters off. Default is
`true`.

### Filter rules

| Rule                                       | Effect                                                                                                        |
|--------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `exclude.byNames`                          | The watchdog doesn't check a matched declaration or the declarations nested in it.                            |
| `exclude.annotatedWith`                    | The annotation is an [internal API annotation](#internal-api-annotations).                                    |
| `include.byNames`, `include.annotatedWith` | When one of the two sets has a rule, the watchdog checks only a matched declaration and the declarations nested in it. |

More rules:

- An exclusion rule has priority over an inclusion rule.
- The watchdog also checks a class with an included function, property, enum entry, or constructor
  that is public API. It doesn't check the other members of the class. An included nested class
  doesn't include the enclosing class.
- An annotation on a file counts for every declaration of the file, with one exception that the
  next item gives.
- The four checks that run on every visibility don't use the filters. See
  [Internal API annotations](#internal-api-annotations) for more details about these checks.
- [Internal API types in public API](./checks/special/public-type-with-internal-api.md) reports a
  checked declaration that exposes an excluded type. An excluded type is a type that an exclusion
  name rule matches, or a type with an exclusion annotation on the type or on an enclosing class.
  A name rule applies only to the types of the module. The check doesn't report a type only because
  no inclusion rule matches it.
- An exclusion annotation on a file doesn't make the classes of the file excluded types for this
  check. An incremental build reads an unchanged class from the previous output, without the
  annotations of its file. Put the annotation on the class itself, or use a name rule.
- [Redundant exemptions](./checks/special/redundant-exemption.md) reports an `@Intentionally*`
  annotation on a declaration that the filters remove from the checks.

An annotation rule reads these positions:

| Annotation position                                                                    | `include.annotatedWith`         | `exclude.annotatedWith`         |
|----------------------------------------------------------------------------------------|---------------------------------|---------------------------------|
| The declaration                                                                        | Counts.                         | Counts.                         |
| An enclosing class or the file                                                         | Counts.                         | Counts.                         |
| A property accessor (`@get:`, `@set:`)                                                 | Counts for the property.        | Doesn't count for the property. |
| The backing field (`@field:`)                                                          | Counts for the property.        | Counts for the property.        |
| The `expect` declaration or its file                                                   | Also includes the `actual`.     | Doesn't count for the `actual`. |
| The `actual` declaration or its file                                                   | Doesn't count for the `expect`. | Doesn't count for the `expect`. |

A name rule gives an `expect` declaration and its `actual` declaration the same result, because
they have the same name. An `actual typealias` gets the result of its `expect` class. The class that
the typealias expands to has a name of its own, so the rules of the `expect` class don't apply to it.

Put an `include.annotatedWith` annotation on the `expect` declaration or on its file. These rules
include only the `actual` declaration and leave the `expect` declaration outside the filters:

- An annotation on the `actual` declaration or on its file.
- A name rule for a member that only the `actual` class declares.

The checks that report a pair on the `expect` declaration then don't report the pair, for example
[Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md) and
[Undocumented public API](./checks/undocumented-public-api.md).

### Filter names

A name is fully qualified. A `.` separates all parts, also a nested class and a member:

| Declaration                                 | Name                              |
|---------------------------------------------|-----------------------------------|
| Class or type alias                         | `com.example.Container`           |
| Nested class                                | `com.example.Container.Value`     |
| Companion object                            | `com.example.Container.Companion` |
| Member function, property, or enum entry    | `com.example.Container.member`    |
| Top-level function or property              | `com.example.topLevelFunction`    |

A function name matches every overload. A constructor, an accessor, and a parameter don't have a
name of their own. The rule of the class, the property, or the function applies to them.

A name can have wildcards:

| Wildcard | Matches                    |
|----------|----------------------------|
| `**`     | Any characters.            |
| `*`      | Any characters except `.`. |
| `?`      | One character.             |

### Differences from the ABI dump

The watchdog checks Kotlin declarations, and the validation tools read class files and klibs.
So some rules have another effect in the watchdog:

- A rule on a class also applies to its nested classes. The validation tools match each class by
  itself.
- A name rule doesn't match the JVM facade class of a file, such as `com.example.UtilsKt`. Use a
  package pattern.
- On JVM, the validation tools match a name rule only against class names. A rule with the name of
  a function, a property, or an enum entry changes only the watchdog there.
- An annotation on a file also removes the classes of the file from the checks. In a JVM dump it
  applies only to the top-level functions and properties.
- The validation tools don't read an annotation with `SOURCE` retention. The watchdog does.
- The Binary Compatibility Validator plugin uses `publicPackages`, `publicClasses`, and
  `publicMarkers` only for a JVM dump. The watchdog uses them for every target.
- The Binary Compatibility Validator plugin doesn't validate a project of `ignoredProjects` or a
  project with `validationDisabled`. The watchdog still checks such a project, without the
  filters of the plugin. To turn the watchdog off for the project, set
  `apiWatchdog { enabled = false }`. The watchdog doesn't read the `klib` settings of the plugin.

## Annotation ignore rules

An annotation ignore rule skips one check on every declaration that carries a given annotation.
Use it when every annotated declaration already satisfies the check.

```kotlin build.gradle.kts
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks

apiWatchdog {
    ignore(
        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS,
        whenAnnotatedWith = listOf("com.example.GeneratedValueMembers"),
    )
}
```

A rule works for configurable checks only, not for always-error checks. With `recursive = true`,
the rule also covers every declaration nested in the annotated one.

## The annotations artifact

The `@Intentionally*` annotations, `@InternalAnnotationMarker`, and `ExemptionReason` are in
`org.jetbrains.kotlinx:kotlin-library-api-watchdog-plugin-annotations`. The Gradle plugin adds this
dependency to the project.
