# Library API Watchdog <img src="logo.svg" width="48" align="right" alt="library-api-watchdog logo"/>

[![Kotlin 2.3.20+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=Kotlin%202.3.20%2B&logo=kotlin&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)
[![IntelliJ IDEA 2026.2+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=IntelliJ%20IDEA%202026.2%2B&logo=intellijidea&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)

A Kotlin K2 compiler plugin that warns library authors about public API declarations that are
hard to evolve.

**[Documentation](https://mr3zee.github.io/Library-API-Watchdog/)** - a full write-up for every
check: rationale, do/don't examples, exemptions, and configuration. The
[API reference](https://mr3zee.github.io/Library-API-Watchdog/api/) covers the exemption
annotations.

## Setup

Add the Space EAP repository to `settings.gradle.kts` for both plugin and library resolution:

```kotlin
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

The plugin only runs in modules compiled with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
(strict or warning variant). Without it the checkers are not registered at all, and the Gradle
plugin prints a warning.

```kotlin
plugins {
    kotlin("library.api-watchdog") version "0.1.0"
}

kotlin {
    explicitApi()
}
```

Applying the Gradle plugin registers the compiler plugin for every compilation except test
compilations, whose sources are never published, and automatically
adds the dependency with the `@Intentionally*` exemption annotations. 

The plugin is intentionally restrictive by default: every check reports a compilation error until it
is individually demoted to a warning or disabled through the `apiWatchdog` extension:

```kotlin
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity

apiWatchdog {
    undocumentedPublicApi = WatchdogSeverity.WARNING
    javaInterop {
        enabled = false // A Kotlin-only library can drop the whole Java interop group.
    }
}
```

See [Setup](https://mr3zee.github.io/Library-API-Watchdog/) and the
[Gradle plugin reference](https://mr3zee.github.io/Library-API-Watchdog/configuration) for all
options, including direct compiler invocation without Gradle.

## Exemptions

Declarations that are public for technical reasons only are excluded from all checks by marking
the library's internal-API annotation with `@InternalAnnotationMarker`. A single declaration is
exempted in place with the matching `@Intentionally*` annotation, which must explain itself
through an `ExemptionReason` and a description. See
[Exemptions and internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions).

## Adopting on an existing library

A library that has already shipped can't change the shape of its public API without breaking
users, so the watchdog's first run typically floods it with diagnostics that are not actionable
anymore. The Gradle plugin registers an `updateBackwardsCompatibilityExempts` task that
acknowledges all of them in one sweep:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

The separate, non-mutating `generateBackwardsCompatibilityExemptsReport` task writes a grouped
HTML report to `build/reports/api-watchdog/backwards-compatibility-exempts.html`, including
every applied Watchdog `@Intentionally*` annotation and diagnostics that have not been acknowledged.
Multi-project builds can use the dependency-driven report aggregation plugin.

See the [existing-library guide](https://mr3zee.github.io/Library-API-Watchdog/existing-libs) for details.

## Checks

### API surface

- [`BOOLEAN_PARAMETER_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/boolean-parameter-public-api) -
  Boolean value parameters are confusing at the call site, as unnamed `true`/`false` arguments
  reveal nothing about their meaning.
- [`DATA_CLASS_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/data-class-public-api) -
  data classes generate `copy`, `componentN` methods and constructor, which is hard to evolve and
  it defies the purpose of the data class in the first place.
- [`EXHAUSTIVE_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/exhaustive-public-api) -
  users can exhaustively match enums and sealed hierarchies, so a new entry or a subtype breaks
  source compatibility.
- [`FUNCTION_TYPE_ALIAS_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/function-type-alias-public-api) -
  type aliases that abbreviate function types erase from the compiled API, so the type can't
  evolve into a richer abstraction later.
- [`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`](https://mr3zee.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads) -
  overloads with same-named parameters that appear in a different relative order have a risk
  silently swapped arguments and unintuitive call sites.
- [`INLINE_FUNCTION_WITH_LOGIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/inline-function-with-logic) -
  public inline functions with a body that does more than delegate will have the compiler copy
  that body, and its bugs, into every user binary.
- [`MUTABLE_COLLECTION_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/mutable-collection-public-api) -
  mutable collection types in public signatures leave it unclear whether user-side and
  library-side mutations affect each other.
- [`NULLABLE_BOOLEAN_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/nullable-boolean-public-api) -
  nullable `Boolean`s in public signatures model three states, but name only two.
- [`OPEN_API_WITHOUT_SUBCLASS_OPT_IN`](https://mr3zee.github.io/Library-API-Watchdog/checks/open-api-without-subclass-opt-in) -
  open or abstract classes and interfaces that any outside code can subclass without restriction.
- [`PAIR_OR_TRIPLE_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/pair-or-triple-public-api) -
  the tuple types `Pair` and `Triple` carry no domain meaning and can't evolve because of the
  fixed shape.
- [`REQUIRED_PARAMETER_AFTER_OPTIONAL`](https://mr3zee.github.io/Library-API-Watchdog/checks/required-parameter-after-optional) -
  required parameters declared after an optional one can't be passed positionally without
  restating the earlier defaults.
- [`STATEFUL_CLASS_WITHOUT_EQUALS`, `STATEFUL_CLASS_WITHOUT_HASH_CODE`, and `STATEFUL_CLASS_WITHOUT_TO_STRING`](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) -
  classes with a backing-field property that don't declare or inherit `equals`, `hashCode`, and
  `toString`, so instances render as an opaque default in logs and debuggers, and comparison is
  reference based.
- [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](https://mr3zee.github.io/Library-API-Watchdog/checks/subclass-opt-in-without-markers) -
  `@SubclassOptInRequired` annotations that list no marker classes don't actually restrict
  subclassing.
- [`UNDOCUMENTED_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/undocumented-public-api) -
  public declarations that have no KDoc.
- [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-from-non-transitive-dependency) -
  dependency types exposed in public signatures but not provided transitively to consumers.
- [`PUBLIC_TYPE_WITH_INTERNAL_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-with-internal-api) -
  public signatures that expose effectively internal types.

### Java interop

These checks only run in JVM compilations. A Kotlin-only library disables the group with
`javaInterop { enabled = false }`. See the
[group overview](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop).

- [`COMPANION_API_WITHOUT_JVM_STATIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-api-without-jvm-static) -
  public companion object functions without `@JvmStatic`, which Java callers can only use with
  the `Companion` instance.
- [`COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-property-without-static-access) -
  public companion object properties with Java-visible accessors that remain on the `Companion`
  instance instead of the outer class.
- [`DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/default-parameters-without-jvm-overloads) -
  functions and constructors with default parameters but without `@JvmOverloads` force Java
  callers to pass every argument.
- [`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/kotlin-only-api-without-jvm-synthetic) -
  functions with a shape only Kotlin callers can use idiomatically.
- [`MANGLED_JVM_NAME_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/mangled-jvm-name-public-api) -
  public API that Java sources can't call because a value class in the signature gets its JVM
  name mangled.
- [`TOP_LEVEL_API_WITHOUT_JVM_NAME`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/top-level-api-without-jvm-name) -
  files with public top-level declarations that compile into a facade `*Kt` class without a
  pinned `@file:JvmName`, so a file renaming breaks Java binary compatibility.

### DSL markers

- [`DSL_MARKER_NOOP_TARGET`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-target) -
  `@DslMarker` annotation targets on which the marker has no effect give a false sense of scope
  control.
- [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-without-explicit-targets) -
  `@DslMarker` annotations without an explicit `@Target` allow target set with mostly no-op
  targets.
- [`DSL_MARKER_NOOP_TYPE_POSITION`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-type-position) -
  DSL markers written on type positions where scope control doesn't react to them.

### Exemption hygiene

- [`EXEMPTION_WITHOUT_EXPLANATION`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/exemption-without-explanation) -
  an `@Intentionally*` exemption annotation left with the default `OTHER` reason and a blank
  description explains nothing.

### Build-level suggestions

Performed by the Gradle plugin rather than the compiler:

- [Explicit API mode warning](https://mr3zee.github.io/Library-API-Watchdog/configuration) - warns when
  explicit API mode is not enabled, since the watchdog registers no checks without it.
- [Binary compatibility validation suggestion](https://mr3zee.github.io/Library-API-Watchdog/abi-validation-suggestion) -
  warns when neither the Kotlin Gradle plugin's built-in ABI validation nor the standalone
  Binary Compatibility Validator is enabled.

## Development

The build resolves compiler-plugin-dev-kit from its
[Space EAP repository](https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap).
The dev-kit settings plugin supplies its Kotlin development repositories, aligned plugin
coordinates, and functional-test publishing setup. The supported Kotlin versions, companion
library, and compiler-plugin project are declared through the dev-kit's namespaced properties in
`gradle.properties`.

Tests:

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:allTests           # diagnostics tests for all supported Kotlin versions
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate JUnit classes from test data
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit # validate the benchmark corpus
./gradlew :kotlin-library-api-watchdog-exempts-fixer:allTests             # exemption fixer tests for all supported Kotlin versions
./gradlew :kotlin-library-api-watchdog-gradle-plugin:functionalTest       # Gradle integration tests for the default Kotlin version
./gradlew :kotlin-library-api-watchdog-report-aggregation:test            # report aggregation tests
```

CI runs the Gradle integration tests against every supported Kotlin version. Use
`:kotlin-library-api-watchdog-gradle-plugin:printCiFunctionalTestMatrix` to list the corresponding
versioned test tasks.

Modules:

- [`:kotlin-library-api-watchdog-compiler-plugin`](compiler-plugin/src) - the compiler plugin (FIR checkers only). Test data
  lives in [compiler-plugin/src/test/data/diagnostics](compiler-plugin/src/test/data/diagnostics).
- [`:kotlin-library-api-watchdog-plugin-annotations`](plugin-annotations/src/commonMain/kotlin) - the `@Intentionally*`
  exemption annotations, `@InternalAnnotationMarker`, and the `ExemptionReason` enum.
- [`:kotlin-library-api-watchdog-exempts-fixer`](exempts-fixer/src) - reads compiler diagnostic reports and applies
  backwards-compatibility exemption annotations to Kotlin sources.
- [`:kotlin-library-api-watchdog-gradle-plugin`](gradle-plugin/src) - applies the compiler plugin and annotations
  dependency, and registers the exemption update and module report tasks (plugin id
  `org.jetbrains.kotlin.library.api-watchdog`).
- [`:kotlin-library-api-watchdog-report-aggregation`](report-aggregation/src) - aggregates exemption reports from
  multiple projects (plugin id `org.jetbrains.kotlin.library.api-watchdog-report-aggregation`).

The documentation site is a [Docusaurus](https://docusaurus.io/) project in [docs](docs), built by
[docs.yml](.github/workflows/docs.yml). See [docs/authoring.md](docs/authoring.md) for the page
template and rules.
