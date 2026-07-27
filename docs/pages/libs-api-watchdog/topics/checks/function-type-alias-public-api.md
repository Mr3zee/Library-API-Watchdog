# Function type aliases in public API

`FUNCTION_TYPE_ALIAS_PUBLIC_API` reports public type aliases that expand to a function type.

| | |
|---|---|
| Diagnostic | `FUNCTION_TYPE_ALIAS_PUBLIC_API` |
| Default severity | Error |
| Gradle property | [`functionTypeAliasPublicApi`](configuration.md) |
| Exemption | [`@IntentionallyFunctionTypeAlias`](exemptions.md) |

## What it reports

A public or protected type alias whose expanded type is a function type: plain, `suspend`,
nullable, or with a receiver, and aliases of such aliases (the expansion is followed through, so
an alias of an alias is still caught).

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
keeps the same lambda call-site ergonomics (SAM conversion) behind a real, nominal type that can
add members without breaking binary compatibility.

## Don't

```kotlin
// FUNCTION_TYPE_ALIAS_PUBLIC_API
public typealias Callback = (Int) -> Unit
```

## Do

```kotlin
public fun interface Callback {
    public fun onCall(value: Int): Unit
}
```

## Don't {id="dont-2"}

```kotlin
// FUNCTION_TYPE_ALIAS_PUBLIC_API
public typealias SuspendAction = suspend () -> Unit
```

## Do {id="do-2"}

```kotlin
public fun interface SuspendAction {
    public suspend fun invoke(): Unit
}
```

Notable points:

- Nullable and receiver variants are caught the same way: `((String) -> Boolean)?` and
  `StringBuilder.() -> Unit` are both function types under the alias.
- An alias of an alias (`public typealias CallbackAlias = Callback`) is still reported: the
  expansion is followed through nested aliases.
- A function type nested inside another type, such as `List<(Int) -> Unit>`, does not trigger the
  check; only the type an alias directly expands to counts.
- Deliberate exception: aliases of the reflection types `KFunction`/`KSuspendFunction` are exempt
  and need no annotation, because a `fun interface` cannot replace a reflection type.

## Exemption

Apply `@IntentionallyFunctionTypeAlias` when exposing the bare function type is intended, for
example when the lambda only ever travels through an inline function and a nominal type would add
no value:

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
- [Exemptions and internal API](exemptions.md)
- [Data classes in public API](data-class-public-api.md)
