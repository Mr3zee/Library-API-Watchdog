---
slug: /
---

# Get started

[![Latest dev version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fpackages.jetbrains.team%2Fmaven%2Fp%2Fkt-lib%2Feap%2Forg%2Fjetbrains%2Fkotlinx%2Fkotlin-library-api-watchdog-gradle-plugin%2Fmaven-metadata.xml&filter=%2A-dev-%2A&label=dev&color=blue&cacheSeconds=300)](https://packages.jetbrains.team/maven/p/kt-lib/eap/org/jetbrains/kotlinx/kotlin-library-api-watchdog-gradle-plugin/)
[![Kotlin 2.3.20+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=Kotlin%202.3.20%2B&logo=kotlin&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)
[![IntelliJ IDEA 2026.2+](https://img.shields.io/github/actions/workflow/status/Mr3zee/libs-watchdog/compiler-plugin-tests.yml?branch=main&label=IntelliJ%20IDEA%202026.2%2B&logo=intellijidea&logoColor=white)](https://github.com/Mr3zee/libs-watchdog/actions/workflows/compiler-plugin-tests.yml)

`library-api-watchdog` is a set of compiler checks for library authors. It finds public API
declarations that are hard to evolve: shapes that can't change later without a breaking change for
the library's users.

Every check is on by default and reports a compilation error, except
[Undocumented public API](./checks/undocumented-public-api.md), which reports a warning.
[Configuration](./configuration.md) changes the severity of a check or turns it off.
[Exemptions and internal API](./exemptions.md) exempts a declaration.

## What it looks like

A public data class with a mutable collection property and no KDoc gets three diagnostics at
once:

:::tip
Diagnostics in code examples are interactive. Hover over an underlined range to read the
diagnostic and follow its link to the full documentation.
:::

```kotlin
// !diag[/Config/] DATA_CLASS_PUBLIC_API ["Config"]
// !diag[/Config/] UNDOCUMENTED_PUBLIC_API ["$class","Config"]
public data class Config(
    // !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["$property","tags","MutableList","$returnTypeFix"]
    public val tags: MutableList<String>
)
```

The fixed version has KDoc, a read-only collection, and a plain class.
[Poko](https://github.com/drewhamilton/Poko) generates its `equals`, `hashCode`, and `toString`:

```kotlin
/** Immutable request configuration. */
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Config(
    /** Labels attached to this configuration. */
    public val tags: List<String>,
)
```

## Quick setup

Apply the Kotlin and Watchdog plugins and turn on explicit API mode:

```kotlin build.gradle.kts
plugins {
    kotlin("multiplatform") version "{{kotlinVersion}}"
    kotlin("library.api-watchdog") version "{{libraryApiWatchdogVersion}}"
}

kotlin {
    explicitApi() // Required to enable checks
}
```

The plugin also works for Kotlin/JVM and Android projects.

See [Configuration](./configuration.md) for more details about the setup and options.

A published library gets many diagnostics on the first run. The `exemptExistingApi` task exempts
every error in one run. See [Adding the plugin to existing libraries](./existing-libs.md) for more
details.

## All checks

### API surface checks

- [Boolean parameters in public API](./checks/boolean-parameter-public-api.md): Boolean value
  parameters. An unnamed `true` or `false` argument has no readable meaning at the call site.
- [Data classes in public API](./checks/data-class-public-api.md): data classes. The generated
  `copy` and `componentN` functions and the constructor expose the exact property list in the
  compiled API.
- [Exhaustive public API](./checks/exhaustive-public-api.md): enums and sealed hierarchies. Users
  can match them exhaustively, so a new entry or subtype breaks source compatibility.
- [Function type aliases in public API](./checks/function-type-alias-public-api.md): type aliases of
  function types. The alias isn't in the compiled API, so the type can't evolve into a richer
  abstraction later.
- [Inconsistent parameter order in overloads](./checks/inconsistent-parameter-order-in-overloads.md):
  overloads whose same-named parameters occupy different positions. A caller can swap the
  arguments and get no error.
- [Inline functions with logic](./checks/inline-function-with-logic.md): public inline functions
  with a body that does more than delegate. The compiler copies that body, and its bugs, into every
  user binary.
- [Mutable collections in public API](./checks/mutable-collection-public-api.md): mutable collection
  types in public signatures. The signature doesn't state whether user-side and library-side
  mutations affect each other.
- [Nullable Booleans in public API](./checks/nullable-boolean-public-api.md): nullable `Boolean`
  types in public signatures. They model three states but name only two.
- [Open API without subclass opt-in](./checks/open-api-without-subclass-opt-in.md): open or abstract
  classes and interfaces that any outside code can subclass without restriction.
- [Pair and Triple in public API](./checks/pair-or-triple-public-api.md): the tuple types `Pair` and
  `Triple`. They carry no domain meaning, and their fixed shape can't evolve.
- [Required parameters after optional ones](./checks/required-parameter-after-optional.md): required
  parameters after an optional one. A positional call must restate the earlier defaults.
- [Stateful classes without equals, hashCode, and toString](./checks/stateful-class-without-equals-hashcode-to-string.md):
  classes with a backing-field property that don't declare or inherit `equals`, `hashCode`, and
  `toString`. Instances compare by reference and render as an opaque default in logs and debuggers.
- [Subclass opt-in without markers](./checks/subclass-opt-in-without-markers.md):
  `@SubclassOptInRequired` annotations that list no marker classes. They don't restrict subclassing.
- [Undocumented public API](./checks/undocumented-public-api.md): public declarations without KDoc.
- [Public types from non-transitive dependencies](./checks/special/public-type-from-non-transitive-dependency.md):
  dependency types in public signatures that consumers don't get transitively.
- [Public types marked as internal API](./checks/special/public-type-with-internal-api.md): public
  signatures that expose types marked as internal API.

### Java interop checks

The [Java interop checks](./checks/java-interop/java-interop.md) run only in JVM compilations.

- [Companion function without JvmStatic](./checks/java-interop/companion-api-without-jvm-static.md):
  public companion object functions without `@JvmStatic`. Java callers can use them only through the
  `Companion` instance.
- [Companion property without static access](./checks/java-interop/companion-property-without-static-access.md):
  public companion object properties whose Java-visible accessors are on the `Companion` instance
  instead of the outer class.
- [Default parameters without JvmOverloads](./checks/java-interop/default-parameters-without-jvm-overloads.md):
  functions and constructors with default parameters but without `@JvmOverloads`. Java callers must
  pass every argument.
- [Kotlin-only API without JvmSynthetic](./checks/java-interop/kotlin-only-api-without-jvm-synthetic.md):
  functions with a shape that only Kotlin callers can use idiomatically.
- [Mangled JVM names in public API](./checks/java-interop/mangled-jvm-name-public-api.md): public
  API with a value class in the signature. The compiler mangles the JVM name, so Java sources can't
  call it.
- [Top-level API without JvmName](./checks/java-interop/top-level-api-without-jvm-name.md): files
  with public top-level declarations and no `@file:JvmName`. The facade `*Kt` class is named after
  the file, so a file rename breaks Java binary compatibility.

### DSL marker checks

- [DSL markers with no-op targets](./checks/special/dsl-marker-noop-target.md): `@DslMarker`
  annotation targets on which the marker has no effect. They give a false sense of scope control.
- [DSL markers without explicit targets](./checks/special/dsl-marker-without-explicit-targets.md):
  `@DslMarker` annotations without an explicit `@Target`. The default target set has mostly targets
  on which the marker has no effect.
- [DSL markers on no-op type positions](./checks/special/dsl-marker-noop-type-position.md): DSL
  markers on type positions where they have no effect on scope control.

### Meaningful exemptions

- [Exemptions without explanation](./checks/special/exemption-without-explanation.md):
  `@Intentionally*` annotations with the default `OTHER` reason and a blank description. They
  don't explain anything.
- [Redundant exemptions](./checks/special/redundant-exemption.md): `@Intentionally*` annotations
  on declarations their check doesn't report.

## Next steps

- [Configuration](./configuration.md)
- [Adding the plugin to existing libraries](./existing-libs.md)
- [Report generation](./report.md)
- [Exemptions and internal API](./exemptions.md)
- [API reference](https://mr3zee.github.io/Library-API-Watchdog/api/)
