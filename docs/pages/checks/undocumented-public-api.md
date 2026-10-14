# Undocumented public API

|                  |                                                  |
|------------------|--------------------------------------------------|
| Diagnostic       | `UNDOCUMENTED_PUBLIC_API`                        |
| Default severity | Warning                                          |
| Gradle property  | [`undocumentedPublicApi`](../configuration.md)   |
| Exemption        | [`@IntentionallyUndocumented`](../exemptions.md) |

## What it reports

A declaration in the public API without its own KDoc.
The check tests for a KDoc block, not its content.

```kotlin
// !diag[/Store/] UNDOCUMENTED_PUBLIC_API ["$class","Store"]
public class Store
```

## Rationale

Document the behavior callers can rely on, including limits and failure conditions.
An undocumented use can become a compatibility constraint when users depend on it.

See [Kotlin API guidelines on documenting your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api)
for more details.

### Don't

```kotlin
// !hide-focused
// !link[/@Poko/] https://github.com/drewhamilton/Poko
@Poko
// !diag[/Cache/] UNDOCUMENTED_PUBLIC_API ["$class","Cache"]
public class Cache {
    // !diag[/get/] UNDOCUMENTED_PUBLIC_API ["$function","get"]
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

## Notes

- For the check to pass, each property needs its own KDoc. Class KDoc tags, including `@property` and `@param` are valid options to document properties, but this can't be checked by a compiler plugin. This is the reason this check is a warning.
- Overrides and `actual` declarations aren't reported.
- The check skips default-named companion objects and checks their members and named companions.
- Enum entries, primary constructors, and compiler-generated members aren't reported.
- Plain `//` and `/* ... */` comments don't count as KDoc.
- `@PublishedApi internal` declarations and declarations inside those classes aren't reported.

## Exemption

<!-- diagnostic-exemption: UNDOCUMENTED_PUBLIC_API -->
If this is intended, apply `@IntentionallyUndocumented` to the declaration.

```kotlin
// No example here, because I couldn't find a valid case when an API
// shouldn't be documented 🤷
```

The annotation exempts only that declaration.

The [`exemptExistingApi`](../existing-libs.md) task adds placeholder KDoc instead of this annotation.
It does this only when the check is set to `WatchdogSeverity.ERROR`.
Replace each placeholder with the declaration's contract.

## Configuration

```kotlin
apiWatchdog {
    undocumentedPublicApi = WatchdogSeverity.ERROR
}
```

For a direct compiler call:
```
-P plugin:org.jetbrains.kotlinx.library.api.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:error
```

## See also

- [Kotlin API guidelines: thoroughly document your API](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html#thoroughly-document-your-api)
- [Exemptions and internal API](../exemptions.md)
