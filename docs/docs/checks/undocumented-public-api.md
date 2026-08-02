# Undocumented public API

`UNDOCUMENTED_PUBLIC_API` reports public declarations that have no KDoc.

|                  |                                               |
|------------------|-----------------------------------------------|
| Diagnostic       | `UNDOCUMENTED_PUBLIC_API`                     |
| Default severity | Error                                         |
| Gradle property  | [`undocumentedPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyUndocumented`](../exemptions.md) |

## What it reports

Each publicly visible declaration a user can reference - classes, interfaces, objects, enum
classes, annotation classes, type aliases, functions, properties, secondary constructors, and enum
entries - is flagged when it carries no KDoc. Only the presence of a KDoc is checked, not its
content:

```kotlin
// !diag[/Cache/] UNDOCUMENTED_PUBLIC_API ["class","Cache"]
public class Cache
```

## Rationale

A KDoc is the contract a user can rely on. Without one, callers can only guess intent from the
implementation, and any later change - even a bug fix - risks breaking a usage nobody wrote down as
supported. Writing the contract down helps your library avoid these issues. See the
[Kotlin API guidelines on documenting your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api).

### Don't

```kotlin
// !collapse(1:1) collapsed details
@Poko
// !diag[/Cache/] UNDOCUMENTED_PUBLIC_API ["class","Cache"]
public class Cache {
    // !diag[/get/] UNDOCUMENTED_PUBLIC_API ["function","get"]
    public fun get(key: String): String? = store[key]

    // !collapse(1:2) collapsed
    // Supporting implementation
    private val store: MutableMap<String, String> = mutableMapOf()
}
```

### Do

```kotlin
/** An in-memory string cache. */
// !collapse(1:1) collapsed details
@Poko
public class Cache {
    /**
     * Returns the cached value for [key],
     * or null when nothing is cached under it.
     */
    public fun get(key: String): String? = store[key]

    // !collapse(1:2) collapsed
    // Supporting implementation
    private val store: MutableMap<String, String> = mutableMapOf()
}
```

### Don't {#dont-2}

A class KDoc alone doesn't document its constructor properties. Each one still needs a matching
`@property` tag (or `@param` for a `val`/`var` declared in the primary constructor):

```kotlin
/** Profile information displayed for a user. */
// !collapse(1:1) collapsed details
@Poko
public class Profile(
    // !diag[/name/] UNDOCUMENTED_PUBLIC_API ["property","name"]
    public val name: String,
    // !diag[/age/] UNDOCUMENTED_PUBLIC_API ["property","age"]
    public val age: Int,
)
```

### Do {#do-2}

```kotlin
/**
 * Profile information displayed for a user.
 *
 * @property name the user's display name.
 * @property age the user's age in years.
 */
// !collapse(1:1) collapsed details
@Poko
public class Profile(
  public val name: String,
  public val age: Int,
)
```

## Notes

- Overrides and `actual` declarations inherit the KDoc of the declaration they implement.
- Compiler-generated members (data class `copy`/`componentN`, enum `values`/`valueOf`/`entries`)
  have no source of their own and are never reported.
- A plain `//` or `/* */` comment doesn't count, only a KDoc block (`/** ... */`) satisfies the
  check.
- Declarations that only `@PublishedApi` puts on the API surface are skipped, together with
  everything inside a `@PublishedApi internal` class. They stay `internal` in sources, so no user
  writes code against them and there is no usage contract to document - unlike their binary shape,
  which the other checks still watch.

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
- [Exemptions and internal API](../exemptions.md)
