# Public types marked as internal API

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_WITH_INTERNAL_API`                       |
| Default severity | Always an error while enabled                         |
| Gradle property  | [`publicTypeWithInternalApi`](../../configuration.md) |
| Exemption        | none                                                  |

## What it reports

A type marked with an [internal API
annotation](../../exemptions.md#internal-api-annotations) in a public API signature:

```kotlin
// !hide-focused
@file:JvmName("InternalModels")

// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class InternalLibApi

@InternalLibApi
public class InternalModel

// !hide-focused
/** Loads the current model. */
// !diag[/InternalModel/] PUBLIC_TYPE_WITH_INTERNAL_API ["$function","loadModel","InternalModel","InternalLibApi"]
public fun loadModel(): InternalModel = InternalModel()
```

## Rationale

Callers must use the types in a supported declaration's signature. An internal API type has no
supported compatibility contract, so changes can break those callers.

Expose a supported type instead:

```kotlin
// !hide-focused
@file:JvmName("Models")

// !hide-focused
/** The supported model returned to users. */
public class Model

// !hide-focused
/** Loads the current model. */
public fun loadModel(): Model = Model()
```

## Notes

- The check skips `@PublishedApi internal` signatures.
- An internal API annotation on an enclosing class also marks its nested types.
- Type aliases don't hide internal API annotations on the alias or its expanded type.
- If the exposing declaration is also internal API, mark it with the internal API annotation.

## Configuration

`publicTypeWithInternalApi` is `true` by default. The check can't report warnings. To disable it:

```kotlin build.gradle.kts
apiWatchdog {
    publicTypeWithInternalApi = false
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:publicTypeWithInternalApi=false
```

## See also

- [Public types from non-transitive dependencies](./public-type-from-non-transitive-dependency.md)
- [Exemptions and internal API](../../exemptions.md)
