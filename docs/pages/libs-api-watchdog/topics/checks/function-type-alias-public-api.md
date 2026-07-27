# Function type aliases in public API

`FUNCTION_TYPE_ALIAS_PUBLIC_API` reports public type aliases that expand to a function type.

|                  |                                                    |
|------------------|----------------------------------------------------|
| Diagnostic       | `FUNCTION_TYPE_ALIAS_PUBLIC_API`                   |
| Default severity | Error                                              |
| Gradle property  | [`functionTypeAliasPublicApi`](configuration.md)   |
| Exemption        | [`@IntentionallyFunctionTypeAlias`](exemptions.md) |

## What it reports

A public or protected type alias whose expanded type is a function type: plain, `suspend`,
nullable, or with a receiver.

```kotlin
// FUNCTION_TYPE_ALIAS_PUBLIC_API
public typealias Callback = (Int) -> Unit
```

## Rationale

A type alias is not a real type: it is erased at compile time, so a user compiled against
`Callback` really binds to `(Int) -> Unit`. The alias can never grow a second member, a default
implementation, or a name that documents intent; the only way to change the shape later is a
breaking change to the bare function type. A
[`fun interface`](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases)
keeps the same lambda call-site ergonomics (SAM conversion) behind a real type that can
add default members without breaking binary compatibility, or be extended from.

### Don't

```kotlin
// FUNCTION_TYPE_ALIAS_PUBLIC_API
public typealias Callback = (Int) -> Unit
```

### Do

```kotlin
public fun interface Callback {
    public fun onCall(value: Int): Unit
}
```

### Don't {id="dont-2"}

```kotlin
// FUNCTION_TYPE_ALIAS_PUBLIC_API
public typealias SuspendAction = suspend () -> Unit
```

### Do {id="do-2"}

```kotlin
public fun interface SuspendAction {
    public suspend fun invoke(): Unit
}
```

## Notes

- Nullable and receiver variants are caught the same way: `public typealias Some = ((String) -> Boolean)?` and
  `public typealias Some = StringBuilder.() -> Unit` are both function types under the alias.
- A function type nested inside another type, such as `List<(Int) -> Unit>`, doesn't trigger the
  check; only the type an alias directly expands to counts.

## Exemption

Apply `@IntentionallyFunctionTypeAlias` when exposing the bare function type is intended.

```kotlin
@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
public typealias Callback = (Int) -> Unit
```

## Configuration

```kotlin
apiWatchdog {
    functionTypeAliasPublicApi = WatchdogSeverity.WARNING
}
```

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=FUNCTION_TYPE_ALIAS_PUBLIC_API:warning
```

## See also

- [Functional interfaces vs. type aliases](https://kotlinlang.org/docs/fun-interfaces.html#functional-interfaces-vs-type-aliases)
- [](exemptions.md)
- [](data-class-public-api.md)
