# libs-api-watchdog <img src="logo.svg" width="48" align="right" alt="libs-api-watchdog logo"/>

A Kotlin K2 compiler plugin that warns library authors about public API declarations that are
hard to evolve.

**[Documentation](https://mr3zee.github.io/libs-api-watchdog/)** - a full write-up for every
check: rationale, do/don't examples, exemptions, and configuration. The
[API reference](https://mr3zee.github.io/libs-api-watchdog/api/) covers the exemption
annotations.

## Setup

The plugin only runs in modules compiled with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
(strict or warning variant); without it the checkers are not registered at all, and the Gradle
plugin prints a warning.

```kotlin
plugins {
    id("org.jetbrains.kotlinx.libs.api.watchdog") version "<version>"
}

kotlin {
    explicitApi()
}
```

Applying the Gradle plugin registers the compiler plugin for every compilation except test
compilations, whose sources are never published, and automatically
adds the `org.jetbrains.kotlin:libs-api-watchdog-plugin-annotations` dependency with the `@Intentionally*` exemption annotations. The
plugin is intentionally restrictive by default: every check reports a compilation error until it
is individually demoted to a warning or disabled through the `apiWatchdog` extension:

```kotlin
import org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity

apiWatchdog {
    undocumentedPublicApi = WatchdogSeverity.WARNING
    javaInterop {
        enabled = false // A Kotlin-only library can drop the whole Java interop group.
    }
}
```

See [Setup](https://mr3zee.github.io/libs-api-watchdog/setup.html) and the
[Gradle plugin reference](https://mr3zee.github.io/libs-api-watchdog/gradle-plugin.html) for all
options, including direct compiler invocation without Gradle.

## Exemptions

Declarations that are public for technical reasons only are excluded from all checks by marking
the library's internal-API annotation with `@InternalAnnotationMarker`. A single declaration is
exempted in place with the matching `@Intentionally*` annotation, which must explain itself
through an `ExemptionReason` and a description. See
[Exemptions and internal API](https://mr3zee.github.io/libs-api-watchdog/exemptions.html).

## Adopting on an existing library

A library that has already shipped can't change the shape of its public API without breaking
users, so the watchdog's first run typically floods it with diagnostics that are not actionable
anymore. The Gradle plugin registers an `updateBackwardsCompatibilityExempts` task that
acknowledges all of them in one sweep:

```bash
./gradlew updateBackwardsCompatibilityExempts
```

The task depends on the regular main Kotlin compilation tasks for every KGP target, including JS,
Native, Wasm, and metadata-only projects. Those compilations record diagnostics with their exact
source positions; the task merges and deduplicates the reports, then inserts the matching
`@Intentionally*` annotations with `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` (adding
imports as needed). Checks disabled through `apiWatchdog` are not exempted, and the few diagnostics
no annotation can acknowledge are listed as warnings for manual follow-up. Run it on a clean
working tree and review the diff; from then on the checks only guard newly added API. See the
[Gradle plugin reference](https://mr3zee.github.io/libs-api-watchdog/gradle-plugin.html) for details.

## Checks

### API surface

- [`OPEN_API_WITHOUT_SUBCLASS_OPT_IN`](https://mr3zee.github.io/libs-api-watchdog/open-api-without-subclass-opt-in.html) -
  open/abstract classes and interfaces that can be subclassed outside the library without
  restriction.
- [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](https://mr3zee.github.io/libs-api-watchdog/subclass-opt-in-without-markers.html) -
  `@SubclassOptInRequired` annotations that list no marker classes and so restrict nothing.
- [`EXHAUSTIVE_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/exhaustive-public-api.html) -
  enums and sealed hierarchies, which users can match exhaustively, so adding an entry or a
  subtype later breaks them.
- [`UNDOCUMENTED_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/undocumented-public-api.html) -
  public declarations of every kind without KDoc.
- [`FUNCTION_TYPE_ALIAS_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/function-type-alias-public-api.html) -
  type aliases of function types; the alias is erased from the compiled API, unlike a
  `fun interface`.
- [`DATA_CLASS_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/data-class-public-api.html) -
  data classes, whose generated `copy`/`componentN` bake the exact property list into the
  compiled API.
- [`STATEFUL_CLASS_WITHOUT_TO_STRING`](https://mr3zee.github.io/libs-api-watchdog/stateful-class-without-to-string.html) -
  stateful classes without a `toString`, rendering as the opaque default in logs and debuggers.
- [`MUTABLE_COLLECTION_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/mutable-collection-public-api.html) -
  mutable collection types (arrays included) in public signatures.
- [`PAIR_OR_TRIPLE_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/pair-or-triple-public-api.html) -
  `Pair` and `Triple` in public signatures; tuple components carry no domain meaning.
- [`BOOLEAN_PARAMETER_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/boolean-parameter-public-api.html) -
  Boolean parameters of public functions; a positional `true`/`false` reveals nothing at the
  call site.
- [`NULLABLE_BOOLEAN_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/nullable-boolean-public-api.html) -
  `Boolean?` in public signatures: three states with only two of them named.
- [`REQUIRED_PARAMETER_AFTER_OPTIONAL`](https://mr3zee.github.io/libs-api-watchdog/required-parameter-after-optional.html) -
  required parameters declared after optional ones.
- [`INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS`](https://mr3zee.github.io/libs-api-watchdog/inconsistent-parameter-order-in-overloads.html) -
  same-named parameters ordered differently across overloads, inviting silently swapped
  arguments.
- [`INLINE_FUNCTION_WITH_LOGIC`](https://mr3zee.github.io/libs-api-watchdog/inline-function-with-logic.html) -
  public inline bodies that do more than delegate; the logic freezes into user binaries.

### Java interop

Only run in JVM compilations; a Kotlin-only library disables the group with
`javaInterop { enabled = false }`. See the
[group overview](https://mr3zee.github.io/libs-api-watchdog/java-interop.html).

- [`MANGLED_JVM_NAME_PUBLIC_API`](https://mr3zee.github.io/libs-api-watchdog/mangled-jvm-name-public-api.html) -
  value classes in signatures mangle the compiled JVM name out of Java's reach.
- [`KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC`](https://mr3zee.github.io/libs-api-watchdog/kotlin-only-api-without-jvm-synthetic.html) -
  suspend/reified/Kotlin-function-type shapes left visible to Java sources.
- [`COMPANION_API_WITHOUT_JVM_STATIC`](https://mr3zee.github.io/libs-api-watchdog/companion-api-without-jvm-static.html) -
  companion functions Java can only reach as `Outer.Companion.member(...)`.
- [`COMPANION_CONSTANT_WITHOUT_JVM_FIELD`](https://mr3zee.github.io/libs-api-watchdog/companion-constant-without-jvm-field.html) -
  constant-shaped companion `val`s readable from Java only through the companion instance.
- [`TOP_LEVEL_API_WITHOUT_JVM_NAME`](https://mr3zee.github.io/libs-api-watchdog/top-level-api-without-jvm-name.html) -
  file facades without `@file:JvmName`, leaking the file name into the Java API surface.
- [`DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS`](https://mr3zee.github.io/libs-api-watchdog/default-parameters-without-jvm-overloads.html) -
  for functions with default parameter values without `@JvmOverloads` Java callers must pass every argument.

### DSL markers

- [`DSL_MARKER_NOOP_TARGET`](https://mr3zee.github.io/libs-api-watchdog/dsl-marker-noop-target.html) -
  `@Target` entries on which a `@DslMarker` annotation has no effect.
- [`DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`](https://mr3zee.github.io/libs-api-watchdog/dsl-marker-without-explicit-targets.html) -
  DSL markers relying on the default target set, which allows nine no-op targets and forbids the
  effective ones.
- [`DSL_MARKER_NOOP_TYPE_POSITION`](https://mr3zee.github.io/libs-api-watchdog/dsl-marker-noop-type-position.html) -
  DSL markers written on type positions where receiver scope control ignores them.

### Exemption hygiene

- [`EXEMPTION_WITHOUT_EXPLANATION`](https://mr3zee.github.io/libs-api-watchdog/exemption-without-explanation.html) -
  `@Intentionally*` exemptions whose reason and description explain nothing. Always an error,
  not configurable.

### Build-level suggestions

Performed by the Gradle plugin rather than the compiler:

- [Explicit API mode warning](https://mr3zee.github.io/libs-api-watchdog/setup.html) - warns when
  explicit API mode is not enabled, since the watchdog registers no checks without it.
- [Tapmoc suggestion](https://mr3zee.github.io/libs-api-watchdog/tapmoc-suggestion.html) - warns
  when [Tapmoc](https://github.com/GradleUp/Tapmoc) is not applied, so the Java and Kotlin
  compatibility levels are not pinned.
- [Binary compatibility validation suggestion](https://mr3zee.github.io/libs-api-watchdog/abi-validation-suggestion.html) -
  warns when neither the Kotlin Gradle plugin's built-in ABI validation nor the standalone
  Binary Compatibility Validator is enabled.

## Development

The build consumes the sibling [compiler-plugin-dev-kit](../compiler-plugin-dev-kit) from
`mavenLocal`; publish it first:

```bash
cd ../compiler-plugin-dev-kit
./gradlew -p artifact-transform publishToMavenLocal
./gradlew -p plugins publishToMavenLocal
./gradlew publishToMavenLocal
```

Tests:

```bash
./gradlew :compiler-plugin:test               # diagnostics tests
./gradlew :compiler-plugin:generateTests      # regenerate JUnit classes from test data
./gradlew :gradle-plugin:functionalTest       # Gradle integration tests
```

Modules:

- [`:compiler-plugin`](compiler-plugin/src) - the compiler plugin (FIR checkers only). Test data
  lives in [compiler-plugin/src/test/data/diagnostics](compiler-plugin/src/test/data/diagnostics).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) - the `@Intentionally*`
  exemption annotations, `@InternalAnnotationMarker`, and the `ExemptionReason` enum.
- [`:gradle-plugin`](gradle-plugin/src) - applies the compiler plugin and the annotations
  dependency (plugin id `org.jetbrains.kotlinx.libs.api.watchdog`).

The documentation site is built from [docs/pages](docs/pages) (Writerside) by
[docs.yml](.github/workflows/docs.yml); see [docs/authoring.md](docs/authoring.md) for the page
template and rules.
