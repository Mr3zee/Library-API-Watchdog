# Public types from non-transitive dependencies

|                  |                                                                     |
|------------------|---------------------------------------------------------------------|
| Diagnostic       | `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`                        |
| Default severity | Always an error while enabled                                       |
| Gradle property  | [`publicTypesMustBeTransitiveDependencies`](../../configuration.md) |
| Module exemption | [`apiWatchdogExemptNonTransitive`](#exempting-a-dependency)         |
| Exemption        | [`@IntentionallyNonTransitiveDependency`](../../exemptions.md)      |

## What it reports

Dependency types in public API and `@PublishedApi internal` signatures when consumers don't
get the dependency transitively:

```kotlin
// !hide-focused
@file:JvmName("Library")

// !hide-focused
/** Loads the current external model. */
// !diag[/ExternalModel/] PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY ["$function","loadModel","com.example.models.ExternalModel","$exemptDeclaration(implementation, \"com.example:models:1.0\")"]
public fun loadModel(): ExternalModel = TODO()
```

## Rationale

An `implementation` dependency is absent from the consumer's compile classpath. Consumers can't use
declarations whose signatures need its types.

### Avoid exposing dependency types

Prefer to keep dependency types out of public API and `@PublishedApi internal` signatures.
Use your library's own types where possible. Keep the dependency as `implementation`:

```kotlin build.gradle.kts
dependencies {
    implementation("com.example:models:1.0")
}
```

### Use `api` only when needed

If your public API must expose the dependency's types, declare the dependency as `api` so consumers can
compile against them:

```kotlin
dependencies {
    api("com.example:models:1.0")
}
```

## Notes

- Runs only with the dependency model that the Gradle plugin passes to the compiler. The check
  has no documented compiler option.
- Includes nested type arguments, generic bounds, class supertypes, and type alias expansions.
- Public inline code can need `@PublishedApi internal` signatures in consumer binaries.
- The check runs for each Android variant. `debugApi` and `freeApi` publish dependencies only for
  their variants.
- The check includes AAR dependencies, including project dependencies, and skips platform classes
  from `android.jar`.

## Exemption

<!-- diagnostic-exemption: PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY -->
If this is intended, apply `@IntentionallyNonTransitiveDependency` to the declaration, an
enclosing class, a parameter, or a type usage.

Use an exemption when the host environment has the dependency on its classpath. Prefer to [exempt
the dependency](#exempting-a-dependency) for the whole module.

For selected declarations, use the annotation and name the host in `description`:

```kotlin
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

On a class, the annotation covers every nested declaration.

## Configuration

`publicTypesMustBeTransitiveDependencies` is `true` by default. The check can't report warnings. To
disable it for the module:

```kotlin build.gradle.kts
apiWatchdog {
    publicTypesMustBeTransitiveDependencies = false
}
```

### Exempting a dependency {#exempting-a-dependency}

If the host environment has the dependency on its classpath, also declare it on
`apiWatchdogExemptNonTransitive`:

```kotlin build.gradle.kts
dependencies {
    compileOnly("com.example:models:1.0")
    apiWatchdogExemptNonTransitive("com.example:models")
}
```

The version is optional. The configuration uses the version resolved on the compile classpath.

Any dependency notation works, including version catalog entries, projects, and `gradleApi()`. The
exemption covers transitive dependencies on the compile classpath, including multiplatform platform artifacts.

To exempt a whole configuration:

```kotlin build.gradle.kts
configurations.apiWatchdogExemptNonTransitive {
    extendsFrom(configurations.compileOnly.get())
}
```

`apiWatchdogExemptNonTransitive` adds nothing to the compile classpath. The dependency still needs
an `implementation` or `compileOnly` declaration.

## See also

- [Public types marked as internal API](./public-type-with-internal-api.md)
- [Exemptions and internal API](../../exemptions.md)
