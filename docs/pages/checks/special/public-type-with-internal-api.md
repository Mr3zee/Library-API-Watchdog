# Internal API types in public API

|                  |                                                                                    |
|------------------|------------------------------------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_WITH_INTERNAL_API`                                                    |
| Default severity | Always an error while enabled                                                      |
| Gradle property  | [`publicTypeWithInternalApi`](../../configuration.md)                        |
| Exemption        | [`@IntentionallyExposedInternalApiForBackwardsCompatibility`](../../exemptions.md) |

## What it reports

An internal API type in a public API signature. An internal API type is one of these:

- A type with an [internal API annotation](../../exemptions.md#internal-api-annotations), or a type
  nested in it.
- A type of the module that an exclusion name rule of the [API filters](../../exemptions.md#api-filters)
  matches, or a type nested in it. The rule can come from the filters of the binary compatibility
  validation of the project, such as `ignoredPackages`.

The check doesn't report a type only because no inclusion rule of the API filters matches it, and a
name rule doesn't apply to a type of a dependency.

A type with an internal API annotation:

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
// !diag[/InternalModel/] PUBLIC_TYPE_WITH_INTERNAL_API ["$function","loadModel","InternalModel","$markedInternal(InternalLibApi)"]
public fun loadModel(): InternalModel = InternalModel()
```

With the exclusion name rule `com.example.internal.**`, the check reports a public function that
returns `com.example.internal.Model`.

## Rationale

Callers must use the types in a supported declaration's signature. An internal API type has no
supported compatibility contract, so changes can break those callers. The same is true for a type
that an exclusion rule matches, because binary compatibility validation doesn't report a change of
that type.

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
- Type aliases don't hide internal API annotations on the alias or its expanded type.
- If the exposing declaration is also internal API, mark it with the internal API annotation, or
  exclude it with a name rule.

## Exemption

<!-- diagnostic-exemption: PUBLIC_TYPE_WITH_INTERNAL_API -->
If this is intended, apply `@IntentionallyExposedInternalApiForBackwardsCompatibility` to the declaration,
a parameter, or a type usage.

Two of the three fixes break the users of a published declaration:

- Remove the type from the signature. This breaks the users.
- Add an internal API annotation with an opt-in requirement to the declaration. This breaks the
  users.
- Exclude the declaration with a name rule. Binary compatibility validation then doesn't check the
  declaration either.

The exemption doesn't have a `reason` parameter, because backwards compatibility is the only valid
reason. Its `description` is optional:

```kotlin
// !hide-focused
@file:JvmName("LegacyModels")

// !hide-focused
/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class InternalLibApi

// !hide-focused
@InternalLibApi
public class InternalModel

// !hide-focused
/** Loads the current model. */
@IntentionallyExposedInternalApiForBackwardsCompatibility(description = "Shipped in 1.0.")
public fun loadModel(): InternalModel = InternalModel()
```

On a class, the annotation covers the supertypes and the type parameter bounds, not the members.
Each member that exposes an internal type needs its own annotation, so a new member stays checked.
The [`exemptExistingApi`](../../existing-libs.md) task adds the annotation to the reported declaration.

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
