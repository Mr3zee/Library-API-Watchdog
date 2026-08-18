# Public types marked as internal API

`PUBLIC_TYPE_WITH_INTERNAL_API` reports types marked with an internal API annotation when a
supported public signature exposes them.

|                  |                                                       |
|------------------|-------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_WITH_INTERNAL_API`                       |
| Default severity | Always an error while enabled                         |
| Gradle property  | [`publicTypeWithInternalApi`](../../configuration.md) |
| Exemption        | none                                                  |

An annotation whose class carries `@InternalAnnotationMarker` says that its declarations have no
supported compatibility contract. Exposing one of those declarations from supported API makes
the contract contradictory: users have to name and use an explicitly unsupported type to call
the supported declaration.

```kotlin
// !hide-focused
@file:JvmName("InternalModels")

package com.example

// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class InternalLibApi

@InternalLibApi
public class InternalModel

// !hide-focused
/** Loads the current model. */
// !diag[/InternalModel/] PUBLIC_TYPE_WITH_INTERNAL_API ["function","loadModel","com.example.InternalModel","InternalLibApi"]
public fun loadModel(): InternalModel = InternalModel()
```

Keep the internal type behind the implementation boundary and expose a supported type instead:

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

If the whole declaration is technical API, mark it with the library's internal annotation too:

```kotlin
// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class InternalLibApi

@InternalLibApi
public class InternalModel

@InternalLibApi
public fun loadInternalModel(): InternalModel = InternalModel()
```

The check covers return, receiver, value and context parameter types, nested type arguments,
generic bounds, class supertypes, and public type aliases. A nested class is internal when an
enclosing class is marked, and an internal type alias is detected independently of its expanded
type. Its diagnostic names the annotation that marks each exposed internal type. Markers declared
in dependency modules are resolved too.

`@PublishedApi internal` declarations are not reported. Although their binary shape is available
to public inline code, users cannot name those declarations as source API, so exposing an internal
type within their signatures does not create a supported source contract.

There is no `@Intentionally*` exemption: either remove the internal type from the supported
signature or mark the exposing declaration as internal API as well.

## Configuration

The mismatch between a supported declaration and an explicitly unsupported signature type is
always an error while the check is enabled. It cannot be demoted to a warning. Disable the whole
check only when the library deliberately permits this mismatch:

```kotlin
apiWatchdog {
    publicTypeWithInternalApi = false
}
```

With direct compiler invocation:

```
-P plugin:org.jetbrains.kotlin.library.api.watchdog:publicTypeWithInternalApi=false
```

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Public types from non-transitive dependencies](./public-type-from-non-transitive-dependency.md)
