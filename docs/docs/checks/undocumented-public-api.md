# Undocumented public API

`UNDOCUMENTED_PUBLIC_API` reports public declarations that have no KDoc.

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `UNDOCUMENTED_PUBLIC_API`                        |
| Default severity | Error                                            |
| Gradle property  | [`undocumentedPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyUndocumented`](../exemptions.md) |

## What it reports

Each declaration in the public API that a user can reference is reported when it carries no KDoc.
Only the presence of a KDoc is checked, not its content:

```kotlin
// !diag[/Store/] UNDOCUMENTED_PUBLIC_API ["class","Store","$declarationDocumentation"]
public class Store
```

## Rationale

A KDoc is the contract a user can rely on. Without one, callers can only guess intent from the
implementation, and any later change - even a bug fix - risks breaking a usage nobody wrote down as
supported. Writing the contract down helps your library avoid these issues. See the
[Kotlin API guidelines on documenting your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api).


### Don't

```kotlin
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
// !diag[/Cache/] UNDOCUMENTED_PUBLIC_API ["class","Cache","$declarationDocumentation"]
public class Cache {
    // !diag[/get/] UNDOCUMENTED_PUBLIC_API ["function","get","$declarationDocumentation"]
    public fun get(key: String): String? = store[key]
    // !hide-focused(1:3)

    // Supporting implementation
    private val store: MutableMap<String, String> = mutableMapOf()
}
```

### Do

```kotlin
/** An in-memory string cache. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Cache {
    /**
     * Returns the cached value for [key],
     * or null when nothing is cached under it.
     */
    public fun get(key: String): String? = store[key]
    // !hide-focused(1:3)

    // Supporting implementation
    private val store: MutableMap<String, String> = mutableMapOf()
}
```



### Don't {#dont-2}

A class KDoc alone doesn't document its constructor properties. Each one still needs a matching
`@property` tag (or `@param` for a `val`/`var` declared in the primary constructor):

```kotlin
/** Profile information displayed for a user. */
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Profile(
    // !diag[/name/] UNDOCUMENTED_PUBLIC_API ["property","name","$constructorPropertyDocumentation"]
    public val name: String,
    // !diag[/age/] UNDOCUMENTED_PUBLIC_API ["property","age","$constructorPropertyDocumentation"]
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
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
public class Profile(
    public val name: String,
    public val age: Int,
)
```

## Notes

- Overrides and `actual` declarations inherit the KDoc of the declaration they implement and are not reported.
- Compiler-generated members (data class `copy`/`componentN`, enum `values`/`valueOf`/`entries`)
  have no source of their own and are never reported.
- A plain `//` or `/* */` comment doesn't count, only a KDoc block (`/** ... */`) satisfies the
  check.
- `@PublishedApi internal` declarations, including declarations inside a `@PublishedApi internal`
  class, are not reported because library users can't write source code against them and there is
  no usage contract to document. Other checks still watch their binary shape.

## Exemption

<!-- diagnostic-exemption: UNDOCUMENTED_PUBLIC_API -->
If this API shape is intentional, apply `@IntentionallyUndocumented` directly to the undocumented
declaration.

```kotlin
// No example here, because I couldn't find a valid case when an API
// shouldn't be documented 🤷
```

## Configuration

```kotlin
apiWatchdog {
    undocumentedPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning
```

## See also

- [Kotlin API guidelines: thoroughly document your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api)
- [Exemptions and internal API](../exemptions.md)
