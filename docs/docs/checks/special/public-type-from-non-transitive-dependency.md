# Public types from non-transitive dependencies

`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` reports dependency types in public signatures when
their artifacts are not available transitively to consumers.

|                  |                                                                     |
|------------------|---------------------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`                        |
| Default severity | Always an error                                                     |
| Gradle property  | [`publicTypesMustBeTransitiveDependencies`](../../configuration.md) |
| Exemption        | None                                                                |

Library consumers must be able to resolve every type in the public API. A type from an
`implementation` dependency is available while the library itself compiles, but it is absent from
the consumer's compile classpath. The consumer then cannot use the affected declaration.

```kotlin build.gradle.kts
dependencies {
    implementation("com.example:models:1.0")
}
```

```kotlin Library.kt
@file:JvmName("Library")

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

The check covers return, receiver, value and context parameter types, type arguments, generic
bounds, class supertypes, and public type aliases. It is always reported as an error and cannot be
suppressed in source or demoted to a warning.

If dependency exposure is deliberately managed outside Gradle metadata, disable the check for the
module:

```kotlin
apiWatchdog {
    publicTypesMustBeTransitiveDependencies = false
}
```

The check needs Gradle's dependency model and therefore does not run during direct compiler
invocation.
