# Undocumented public API

`UNDOCUMENTED_PUBLIC_API` reports public declarations that have no KDoc.

|                  |                                               |
|------------------|-----------------------------------------------|
| Diagnostic       | `UNDOCUMENTED_PUBLIC_API`                     |
| Default severity | Error                                         |
| Gradle property  | [`undocumentedPublicApi`](configuration.md)   |
| Exemption        | [`@IntentionallyUndocumented`](exemptions.md) |

## What it reports

Each publicly visible declaration a user can reference - classes, interfaces, objects, enum
classes, annotation classes, type aliases, functions, properties, secondary constructors, and enum
entries - is flagged when it carries no KDoc. Only the presence of a KDoc is checked, not its
content:

```kotlin
// UNDOCUMENTED_PUBLIC_API
public class Cache
```

## Rationale

A KDoc is the contract a user can rely on. Without one, callers can only guess intent from the
implementation, and any later change - even a bug fix - risks breaking a usage nobody wrote down as
supported. Writing the contract down helps your library avoid these issues. See the
[Kotlin API guidelines on documenting your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api).

### Don't

```kotlin
// UNDOCUMENTED_PUBLIC_API
public class Cache {
    // UNDOCUMENTED_PUBLIC_API
    public fun get(key: String): String? = store[key]
  
    private val store: MutableMap<String, String> = mutableMapOf()
}
```

### Do

```kotlin
/** An in-memory string cache. */
public class Cache {
    /**
     * Returns the cached value for [key], 
     * or null when nothing is cached under it. 
     */
    public fun get(key: String): String? = store[key]

    private val store: MutableMap<String, String> = mutableMapOf()
}
```

### Don't {id="dont-2"}

A class KDoc alone doesn't document its constructor properties. Each one still needs a matching
`@property` tag (or `@param` for a `val`/`var` declared in the primary constructor):

```kotlin
/** A user profile. */
public class Profile(
    // UNDOCUMENTED_PUBLIC_API
    public val name: String,
    // UNDOCUMENTED_PUBLIC_API
    public val age: Int,
) 
```

### Do {id="do-2"}

```kotlin
/**
 * A user profile.
 *
 * @property name the user's display name.
 * @property age the user's age in years.
 */
public class Profile(
  public val name: String, 
  public val age: Int,
)
```

## Notes

- Overrides and `actual` declarations inherit the KDoc of the declaration they implement.
- Compiler-generated members (data class `copy`/`componentN`, enum `values`/`valueOf`/`entries`)
  have no source of their own and are never reported.
- A plain `//` or `/* */` comment doesn't count; only a KDoc block (`/** ... */`) satisfies the
  check.
- `@PublishedApi` annotated declarations are skipped during this check.

## Exemption

Apply `@IntentionallyUndocumented` directly on the class, type alias, function, property,
constructor, or enum entry that stays undocumented. It doesn't cover nested or member
declarations:

```kotlin
// No example here, because I couldn't find a good one when an API
// shouldn't be documented. 
```

## Configuration

```kotlin
apiWatchdog {
    undocumentedPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
```

## See also

- [Kotlin API guidelines: thoroughly document your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api)
- [](exemptions.md)
