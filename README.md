# Library API Watchdog <img src="assets/logo.svg" width="48" align="right" alt="library-api-watchdog logo"/>

[![Kotlin 2.3.20+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=Kotlin%202.3.20%2B&logo=kotlin&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)
[![IntelliJ IDEA 2026.2+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=IntelliJ%20IDEA%202026.2%2B&logo=intellijidea&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)

A Kotlin K2 compiler plugin that warns library authors about public API declarations that are
hard to evolve.

The [documentation](https://mr3zee.github.io/Library-API-Watchdog/) has a page for every check:
the rationale, examples, exemptions, and configuration. The
[API reference](https://mr3zee.github.io/Library-API-Watchdog/api/) covers the exemption
annotations.

## Setup

The plugin and the annotations library are in the Space EAP repository. Add it to
`settings.gradle.kts`:

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

Apply the plugin and turn on
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode),
strict or warning:

```kotlin
plugins {
    kotlin("library.api-watchdog") version "0.1.0"
}

kotlin {
    explicitApi()
}
```

The checks run only in modules with explicit API mode. Without it the plugin registers no checks,
and the Gradle plugin reports a warning.

The Gradle plugin registers the compiler plugin for every compilation except test compilations.
`explicitApi()` reaches the `main` compilations, the metadata compilations, and the Android variants.
A custom compilation such as `testFixtures` has no checks without the `-Xexplicit-api` flag.
It also adds the dependency with the `@Intentionally*` exemption annotations.

Every check reports a compilation error by default, except `UNDOCUMENTED_PUBLIC_API`, which
reports a warning. The `apiWatchdog` extension changes the severity of a check or turns it off.
`enabled = false` turns every check off:

```kotlin
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity

apiWatchdog {
    enabled = false // disable all checks 
    
    dataClassPublicApi = WatchdogSeverity.WARNING
    undocumentedPublicApi = WatchdogSeverity.NONE
    
    javaInterop {
        enabled = false // Kotlin-only library
    }
}
```

See [Configuration](https://mr3zee.github.io/Library-API-Watchdog/configuration) for more details about every option
and the compiler flags for a build without Gradle.

## Exemptions

Four ways to exempt API from the checks:

- **[Internal API](https://mr3zee.github.io/Library-API-Watchdog/exemptions)**: every check
  skips the declarations under an internal-API annotation. Mark the library's own annotation with
  `@InternalAnnotationMarker`. For an annotation from a dependency, use
  `apiWatchdog { internalApiAnnotations.add("com.example.InternalApi") }`.
- **[One declaration](https://mr3zee.github.io/Library-API-Watchdog/exemptions)**: apply the
  matching `@Intentionally*` annotation with an `ExemptionReason`. A reason other than
  `FOR_BACKWARDS_COMPATIBILITY` or `API_DESIGN` also needs a description.
- **[One check on every declaration with a given annotation](https://mr3zee.github.io/Library-API-Watchdog/configuration#annotation-ignore-rules)**:
  use
  `apiWatchdog { ignore(WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS, whenAnnotatedWith = listOf("com.example.GeneratedValueMembers")) }`.
  Only a configurable check accepts this rule.
- **[A dependency that the runtime environment gives](https://mr3zee.github.io/Library-API-Watchdog/configuration#exempt-non-transitive-dependencies)**:
  for example, the Gradle API for a Gradle plugin. Declare it on the
  `apiWatchdogExemptNonTransitive` configuration: `apiWatchdogExemptNonTransitive(libs.host.api)`.
  `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` then accepts its types in the whole module.

## Adopting on an existing library

A published library can't change its public API, so the first run reports many diagnostics that
the author can't act on. The `exemptExistingApi` task exempts every declaration with an error:
an `@Intentionally*` annotation, or a placeholder KDoc for `UNDOCUMENTED_PUBLIC_API`. A warning
stays:

```bash
./gradlew exemptExistingApi
```

[Adding the plugin to existing libraries](https://mr3zee.github.io/Library-API-Watchdog/existing-libs)
describes the task.

## Reports

The `exemptExistingApi` task and the `generateApiWatchdogReport` task write a report to
`build/reports/api-watchdog/`. `generateApiWatchdogReport` changes no source file. The report has
two formats:

- `report.html`: every applied `@Intentionally*` annotation and the diagnostics without one.
- `report.sarif`: the diagnostics without an annotation, in
  [SARIF 2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html), for a code
  scanning service.

Both formats are on by default. The `reports` block turns one off:

```kotlin
apiWatchdog {
    reports {
        html = false
    }
}
```

The report aggregation plugin combines the reports of several projects:

```kotlin
plugins {
    kotlin("library.api-watchdog-report-aggregation") version "0.1.0"
}

dependencies {
    apiWatchdogReports(project(":library-core"))
    apiWatchdogReports(project(":library-client"))
}
```

```bash
./gradlew aggregateApiWatchdogReports
```

See [The API Watchdog report](https://mr3zee.github.io/Library-API-Watchdog/report) for more details.

## Checks

### API surface

- [`BOOLEAN_PARAMETER_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/boolean-parameter-public-api) -
  Boolean value parameters. An unnamed `true` or `false` argument has no readable meaning at the
  call site.
- [`DATA_CLASS_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/data-class-public-api) -
  data classes. The generated `copy` and `componentN` functions and the constructor expose the
  exact property list in the compiled API.
- [`EXHAUSTIVE_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/exhaustive-public-api) -
  enums and sealed hierarchies. Users can match them exhaustively, so a new entry or subtype
  breaks source compatibility.
- [`FUNCTION_TYPE_ALIAS_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/function-type-alias-public-api) -
  type aliases of function types. The alias isn't in the compiled API, so the type can't evolve
  into a richer abstraction later.
- [`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`](https://mr3zee.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads) -
  overloads whose same-named parameters occupy different positions. A caller can swap
  the arguments and get no error.
- [`INLINE_FUNCTION_WITH_LOGIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/inline-function-with-logic) -
  public inline functions with a body that does more than delegate. The compiler copies that
  body, and its bugs, into every user binary.
- [`MUTABLE_COLLECTION_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/mutable-collection-public-api) -
  mutable collection types in public signatures. The signature doesn't state whether user-side
  and library-side mutations affect each other.
- [`NULLABLE_BOOLEAN_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/nullable-boolean-public-api) -
  nullable `Boolean` types in public signatures. They model three states but name only two.
- [`OPEN_API_WITHOUT_SUBCLASS_OPT_IN`](https://mr3zee.github.io/Library-API-Watchdog/checks/open-api-without-subclass-opt-in) -
  open or abstract classes and interfaces that any outside code can subclass without
  restriction.
- [`PAIR_OR_TRIPLE_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/pair-or-triple-public-api) -
  the tuple types `Pair` and `Triple`. They carry no domain meaning, and their fixed shape can't
  evolve.
- [`REQUIRED_PARAMETER_AFTER_OPTIONAL`](https://mr3zee.github.io/Library-API-Watchdog/checks/required-parameter-after-optional) -
  required parameters after an optional one. A positional call must restate the earlier
  defaults.
- [`STATEFUL_CLASS_WITHOUT_EQUALS`, `STATEFUL_CLASS_WITHOUT_HASH_CODE`, and `STATEFUL_CLASS_WITHOUT_TO_STRING`](https://mr3zee.github.io/Library-API-Watchdog/checks/stateful-class-without-equals-hashcode-to-string) -
  classes with a backing-field property that don't declare or inherit `equals`, `hashCode`, and
  `toString`. Instances compare by reference and render as an opaque default in logs and
  debuggers.
- [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](https://mr3zee.github.io/Library-API-Watchdog/checks/subclass-opt-in-without-markers) -
  `@SubclassOptInRequired` annotations that list no marker classes. They don't restrict
  subclassing.
- [`UNDOCUMENTED_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/undocumented-public-api) -
  public declarations without KDoc.
- [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-from-non-transitive-dependency) -
  dependency types in public signatures that consumers don't get transitively.
- [`PUBLIC_TYPE_WITH_INTERNAL_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/public-type-with-internal-api) -
  public signatures that expose types marked as internal API.

### Java interop

The [Java interop checks](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop)
run only in JVM compilations. A Kotlin-only library turns off the group with
`javaInterop { enabled = false }`.

- [`COMPANION_API_WITHOUT_JVM_STATIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-api-without-jvm-static) -
  public companion object functions without `@JvmStatic`. Java callers can use them only through
  the `Companion` instance.
- [`COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/companion-property-without-static-access) -
  public companion object properties whose Java-visible accessors are on the `Companion`
  instance instead of the outer class.
- [`DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/default-parameters-without-jvm-overloads) -
  functions and constructors with default parameters but without `@JvmOverloads`. Java callers
  must pass every argument.
- [`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/kotlin-only-api-without-jvm-synthetic) -
  functions with a shape that only Kotlin callers can use idiomatically.
- [`MANGLED_JVM_NAME_PUBLIC_API`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/mangled-jvm-name-public-api) -
  public API with a value class in the signature. The compiler mangles the JVM name, so Java
  sources can't call it.
- [`TOP_LEVEL_API_WITHOUT_JVM_NAME`](https://mr3zee.github.io/Library-API-Watchdog/checks/java-interop/top-level-api-without-jvm-name) -
  files with public top-level declarations and no `@file:JvmName`. The facade `*Kt` class is
  named after the file, so a file rename breaks Java binary compatibility.

### DSL markers

- [`DSL_MARKER_NOOP_TARGET`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-target) -
  `@DslMarker` annotation targets on which the marker has no effect. They give a false sense of
  scope control.
- [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-without-explicit-targets) -
  `@DslMarker` annotations without an explicit `@Target`. The default target set has mostly
  targets on which the marker has no effect.
- [`DSL_MARKER_NOOP_TYPE_POSITION`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/dsl-marker-noop-type-position) -
  DSL markers on type positions where they have no effect on scope control.

### Exemption hygiene

- [`EXEMPTION_WITHOUT_EXPLANATION`](https://mr3zee.github.io/Library-API-Watchdog/checks/special/exemption-without-explanation) -
  `@Intentionally*` annotations with the default `OTHER` reason and a blank description. They
  explain nothing.

### Build-level suggestions

The Gradle plugin reports these, not the compiler:

- [Explicit API mode warning](https://mr3zee.github.io/Library-API-Watchdog/configuration) -
  explicit API mode is off, so the watchdog registers no checks.
- [Binary compatibility validation suggestion](https://mr3zee.github.io/Library-API-Watchdog/abi-validation-suggestion) -
  neither the ABI validation of the Kotlin Gradle plugin nor the Binary Compatibility Validator
  plugin is on.

## Development

The build gets the compiler-plugin-dev-kit from its
[Space EAP repository](https://packages.jetbrains.team/maven/p/compiler-plugin-dev-kit/eap).
The dev kit settings plugin gives the Kotlin development repositories, the plugin coordinates,
and the functional-test publishing. The `kotlin.compiler.plugin.devkit.*` properties in
`gradle.properties` declare the companion library and the compiler-plugin project.

Tests:

```bash
./gradlew :kotlin-library-api-watchdog-compiler-plugin:allTests           # diagnostics tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-compiler-plugin:generateTests      # regenerate the JUnit classes from the test data
./gradlew :kotlin-library-api-watchdog-compiler-plugin:benchmarkCorpusAudit # check that the corpus triggers every benchmark-eligible checker
./gradlew :kotlin-library-api-watchdog-exempts-fixer:allTests             # exemption fixer tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-gradle-plugin:functionalTest       # Gradle TestKit tests against every supported Kotlin version
./gradlew :kotlin-library-api-watchdog-report-aggregation:test            # report aggregation tests
```

CI runs one functional test task for each supported Kotlin version.
`:kotlin-library-api-watchdog-gradle-plugin:printCiFunctionalTestMatrix` lists those tasks.

Modules:

- [`:kotlin-library-api-watchdog-compiler-plugin`](compiler-plugin/src) - the compiler plugin, FIR checkers only. The
  test data is in [compiler-plugin/src/test/data/diagnostics](compiler-plugin/src/test/data/diagnostics).
- [`:kotlin-library-api-watchdog-plugin-annotations`](plugin-annotations/src/commonMain/kotlin) - the `@Intentionally*`
  exemption annotations, `@InternalAnnotationMarker`, and the `ExemptionReason` enum.
- [`:kotlin-library-api-watchdog-exempts-fixer`](exempts-fixer/src) - reads the compiler diagnostic reports and applies
  the exemption annotations to Kotlin sources.
- [`:kotlin-library-api-watchdog-gradle-plugin`](gradle-plugin/src) - applies the compiler plugin and the annotations
  dependency, and registers the `exemptExistingApi` and `generateApiWatchdogReport` tasks. Plugin id
  `org.jetbrains.kotlin.library.api-watchdog`.
- [`:kotlin-library-api-watchdog-report-aggregation`](report-aggregation/src) - combines the reports of several
  projects. Plugin id `org.jetbrains.kotlin.library.api-watchdog-report-aggregation`.

The documentation site is a [Docusaurus](https://docusaurus.io/) project in [docs](docs), built by
[docs.yml](.github/workflows/docs.yml). See [docs/authoring.md](docs/authoring.md) for more details.
