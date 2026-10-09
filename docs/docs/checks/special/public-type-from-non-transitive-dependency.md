# Public types from non-transitive dependencies

`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` reports dependency types in public signatures when
their artifacts are not available transitively to consumers.

|                  |                                                                     |
|------------------|---------------------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`                        |
| Default severity | Always an error                                                     |
| Gradle property  | [`publicTypesMustBeTransitiveDependencies`](../../configuration.md) |
| Allowance        | [`apiWatchdogAllowedNonTransitive`](#allowing-a-dependency)         |
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
// !diag[/ExternalModel/] PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY ["function","loadModel","com.example.models.ExternalModel"]
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

## Allowing a dependency

When the environment that loads the library supplies a dependency on its own, its types are safe
in the public API even though nothing publishes them. Declare such a dependency on the
`apiWatchdogAllowedNonTransitive` configuration, and the check accepts its types everywhere in the
module without a per-declaration exemption. Any dependency notation works, including a version
catalog entry, a project, and `gradleApi()`. The version is optional, the compile classpath's is
used:

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")

    apiWatchdogAllowedNonTransitive("com.example:models")
    apiWatchdogAllowedNonTransitive(libs.host.api)
    apiWatchdogAllowedNonTransitive(project(":host-api"))
}
```

The same allowance can be recorded in the configuration block of the declaration itself, so the
dependency is named once. Inside that block, `this` is the dependency being declared:

```kotlin build.gradle.kts
import org.jetbrains.kotlinx.library.api.watchdog.allowExposingNonTransitivelyInApi

dependencies {
    compileOnly("com.example:models:1.0") { allowExposingNonTransitivelyInApi(this) }
}
```

The allowance covers the named dependency and everything it pulls onto the compile classpath, so
a Kotlin Multiplatform library is allowed together with its platform artifact. To allow everything
in a configuration, such as every `compileOnly` dependency of a Gradle plugin, extend from it
through the `apiWatchdog` extension:

```kotlin build.gradle.kts
apiWatchdog {
    allowedNonTransitiveDependencies {
        extendsFrom(configurations.compileOnly.get())
    }
}
```

The configuration is a marker only. It adds nothing to the compile classpath, and the dependency
is still declared as `implementation` or `compileOnly`. See
[Allowed non-transitive dependencies](../../configuration.md#allowed-non-transitive-dependencies)
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
If this API shape is intentional, apply `@IntentionallyNonTransitiveDependency` to the declaration,
an enclosing class, a parameter, or a type usage.

Use the exemption when the environment that loads the library supplies the dependency on its own,
so publishing it transitively is not needed or not possible. A Gradle plugin is the common case: it
compiles against `gradleApi()` or a `compileOnly` dependency, yet it runs inside a Gradle process
that already provides every Gradle type. An IDE plugin, an annotation processor loaded by the
compiler, or a servlet container's API work the same way. Name that environment in the
`description`, since nothing about the declaration shows where the dependency comes from. When
every use of the dependency is of that kind, [allow the dependency](#allowing-a-dependency) for the
module instead of exempting each declaration.

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
    reason = ExemptionReason.INTEROP,
    description = "The host application ships com.example:models on its classpath.",
)
public fun loadModel(): ExternalModel = TODO()
```

On a class, the exemption also covers every declaration nested in it, so a task or extension class
whose members all mention the environment's types needs a single acknowledgement:

```kotlin
// !hide-focused
/** Loads and stores models through the host application. */
@IntentionallyNonTransitiveDependency(
    reason = ExemptionReason.INTEROP,
    description = "The host application ships com.example:models on its classpath.",
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
