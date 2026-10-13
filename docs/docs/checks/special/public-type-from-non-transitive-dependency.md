# Public types from non-transitive dependencies

`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` reports dependency types in public signatures when
their artifacts are not available transitively to consumers.

|                  |                                                                     |
|------------------|---------------------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`                        |
| Default severity | Always an error                                                     |
| Gradle property  | [`publicTypesMustBeTransitiveDependencies`](../../configuration.md) |
| Module exemption | [`exemptNonTransitiveDependency(this)`](#exempting-a-dependency)    |
| Exemption        | [`@IntentionallyNonTransitiveDependency`](../../exemptions.md)      |

Library consumers must be able to resolve every type in the public API and in
`@PublishedApi internal` signatures. A type from an `implementation` dependency is available while
the library itself compiles, but it is absent from the consumer's compile classpath. The consumer
then can't use the affected declaration.

```kotlin build.gradle.kts
dependencies {
    implementation("com.example:models:1.0")
}
```

```kotlin Library.kt
// !hide-focused
@file:JvmName("Library")

// !hide-focused
/** Loads the current external model. */
// !diag[/ExternalModel/] PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY ["$function","loadModel","com.example.models.ExternalModel","$exemptDeclaration(implementation, \"com.example:models:1.0\")"]
public fun loadModel(): ExternalModel = TODO()
```

Publish the dependency transitively when one of its types is part of the API:

```kotlin
dependencies {
    api("com.example:models:1.0")
}
```

The check covers return, receiver, value and context parameter types, nested type arguments, generic
bounds, class supertypes, and public type aliases. It is always reported as an error and can't be
demoted to a warning.

Android libraries are checked per build variant. A dependency declared with `debugApi` or `freeApi`
is published by that variant alone, so the other variants report its types. AAR dependencies, from
projects and repositories alike, count like jars, and platform classes from `android.jar` are not
dependencies at all.

## Exempting a dependency

When the environment that loads the library supplies a dependency on its own, its types are safe
in the public API even though nothing publishes them. Exempt such a dependency in the
configuration block of its declaration, and the check accepts its types everywhere in the module
without a per-declaration exemption. Inside that block, `this` is the dependency being declared:

```kotlin build.gradle.kts
import org.jetbrains.kotlinx.library.api.watchdog.exemptNonTransitiveDependency

dependencies {
    compileOnly("com.example:models:1.0") { exemptNonTransitiveDependency(this) }
}
```

The diagnostic prints this snippet for the declaration that pulls the type in.

A dependency can also be declared on the `apiWatchdogExemptNonTransitive` configuration, apart
from its own declaration. Any dependency notation works, including a version catalog entry, a
project, and `gradleApi()`. The version is optional, the compile classpath's is used:

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")

    apiWatchdogExemptNonTransitive("com.example:models")
    apiWatchdogExemptNonTransitive(libs.host.api)
    apiWatchdogExemptNonTransitive(project(":host-api"))
}
```

The exemption covers the named dependency and everything it pulls onto the compile classpath, so
a Kotlin Multiplatform library is exempt together with its platform artifact. To exempt everything
in a configuration, such as every `compileOnly` dependency of a Gradle plugin, extend from it
through the `apiWatchdog` extension:

```kotlin build.gradle.kts
apiWatchdog {
    exemptNonTransitiveDependencies {
        extendsFrom(configurations.compileOnly.get())
    }
}
```

The configuration is a marker only. It adds nothing to the compile classpath, and the dependency
is still declared as `implementation` or `compileOnly`. See
[Exempt non-transitive dependencies](../../configuration.md#exempt-non-transitive-dependencies)
in the configuration reference.

If dependency exposure is deliberately managed outside Gradle metadata for the whole module, disable
the check for the module:

```kotlin
apiWatchdog {
    publicTypesMustBeTransitiveDependencies = false
}
```

## Notes

- This check needs Gradle's dependency model and therefore does not run during direct compiler
  invocation.
- `@PublishedApi internal` declarations are included because public inline bodies can copy calls to
  them into consumer binaries, whose linkage still requires every type in those signatures.

## Exemption

<!-- diagnostic-exemption: PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY -->
If this is intended, apply `@IntentionallyNonTransitiveDependency` to the declaration, an
enclosing class, a parameter, or a type usage.

Use an exemption when the environment that loads the library supplies the dependency on its own,
so publishing it transitively isn't needed or not possible. A Gradle plugin is the common case: it
compiles against `gradleApi()` or a `compileOnly` dependency, yet it runs inside a Gradle process
that already provides every Gradle type. An IDE plugin, an annotation processor loaded by the
compiler, or a servlet container's API work the same way. Prefer to
[exempt the dependency](#exempting-a-dependency) in its declaration, which accepts every use of
its types. Use the annotation when only some declarations must expose the dependency. Name the
environment in the `description`, because nothing about the declaration shows where the
dependency comes from.

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")
}
```

```kotlin Library.kt
// !hide-focused
@file:JvmName("Library")

// !hide-focused
/** Loads the current external model. */
@IntentionallyNonTransitiveDependency(
    description = "The host application ships com.example:models on its classpath.",
    reason = ExemptionReason.INTEROP,
)
public fun loadModel(): ExternalModel = TODO()
```

On a class, the exemption also covers every declaration nested in it, so a task or extension class
whose members all mention the environment's types needs a single acknowledgement:

```kotlin
// !hide-focused
/** Loads and stores models through the host application. */
@IntentionallyNonTransitiveDependency(
    description = "The host application ships com.example:models on its classpath.",
    reason = ExemptionReason.INTEROP,
)
public class ModelLoader {
    /** Loads the current model. */
    public fun load(): ExternalModel = TODO()

    /** Stores [model] as the current one. */
    public fun store(model: ExternalModel) {
        TODO()
    }
}
```

## See also

- [Public types marked as internal API](./public-type-with-internal-api.md)
- [Exemptions and internal API](../../exemptions.md)
