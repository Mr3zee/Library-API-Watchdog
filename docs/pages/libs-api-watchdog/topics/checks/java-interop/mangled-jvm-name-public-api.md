# Mangled JVM names in public API

`MANGLED_JVM_NAME_PUBLIC_API` reports public functions, properties, and constructors that Java
sources can't call because a value class in their signature makes the JVM backend mangle the
compiled name.

| | |
|---|---|
| Diagnostic | `MANGLED_JVM_NAME_PUBLIC_API` |
| Default severity | Error |
| Applies to | JVM compilations only |
| Gradle property | [`mangledJvmNamePublicApi`](configuration.md) |
| Exemption | [`@IntentionallyMangledJvmName`](exemptions.md) |

## What it reports

A value class among the value parameters, the extension receiver, or the context parameters of a
function or property - nullable types and type parameters bounded by a value class included - or
a value class *return* type on a class, interface, or object member, makes the JVM backend append
a hash suffix to the compiled name, and the `-` is not a legal Java identifier. A constructor with
a value class parameter gets no such suffix; instead the visible constructor becomes private and
the public one gains a synthetic marker parameter, which Java still can't call:

```kotlin
@JvmInline
public value class UserId(public val raw: String)

// MANGLED_JVM_NAME_PUBLIC_API
public fun take(id: UserId): Unit = Unit
```

## Rationale

Value classes compile to their underlying type, so the backend needs a hashed suffix to keep the
compiled method distinct from an overload taking the underlying type directly - `take(id: UserId)`
compiles to `take-4ZD5Yi0(...)`. A constructor gets no such suffix: the visible one becomes
private and a synthetic overload with a marker parameter takes its place. Kotlin callers resolve
by the source signature and never notice, but for Java the declaration is unreachable. See the
Kotlin guide on
[inline value classes and mangling](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes).

## Don't

```kotlin
// Compiles to take-4ZD5Yi0(...): an illegal Java identifier.
public fun take(id: UserId): Unit = Unit
```

## Do

```kotlin
@JvmName("take")
public fun take(id: UserId): Unit = Unit
```

## Don't {id="dont-2"}

```kotlin
// The public constructor is replaced: a private one plus a synthetic marker-parameter overload.
public class Wallet(public val id: UserId)
```

## Do {id="do-2"}

```kotlin
@JvmExposeBoxed
public class Wallet(public val id: UserId)
```

`@JvmExposeBoxed` generates Java-callable boxed variants alongside the mangled ones; it is the
only fix for constructors and overridable members, since `@JvmName` doesn't accept them.

## Notes

- A value class inside a type argument (`List<UserId>`) is boxed and keeps the JVM name; only the
  classifier itself mangles, not a type it is nested in.
- A top-level callable that merely *returns* a value class keeps its JVM name; the return type
  only mangles for members, where the dispatch receiver makes the difference.
- A `var` property's setter mangles independently of the getter - renaming or hiding one accessor
  leaves the other checked on its own.
- Members and constructors of the value class itself are exempt: declaring the public value class
  is the deliberate choice, and `@JvmName` is not even applicable inside it.
- `suspend` functions are exempt: an unmangled name would not make them Java-callable anyway.
- Overrides are exempt; the fixed signature is reported on the base declaration.
- `@JvmSynthetic` declarations and accessors are exempt: already hidden from Java on purpose.

## Exemption

Apply `@IntentionallyMangledJvmName` when Java callers are not supported for this declaration. It
targets the declaration itself, a primary constructor `val`/`var` parameter (covering the property
made from it), or a containing class (covering every declaration inside):

```kotlin
@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun acknowledged(id: UserId): Unit = Unit
```

## Configuration

```kotlin
apiWatchdog {
    javaInterop {
        mangledJvmNamePublicApi = WatchdogSeverity.WARNING
    }
}
```

The property lives inside the `javaInterop { }` block; `javaInterop { enabled = false }` turns off
this check along with the rest of the [Java interop checks](java-interop.md) group.

With direct compiler invocation:
```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=MANGLED_JVM_NAME_PUBLIC_API:warning
```

## See also

- [Inline value classes and mangling](https://kotlinlang.org/docs/java-to-kotlin-interop.html#inline-value-classes)
- [Java interop checks](java-interop.md)
- [Kotlin-only API without JvmSynthetic](kotlin-only-api-without-jvm-synthetic.md), the sibling
  check for shapes that stay visible to Java but are not idiomatically callable
- [Exemptions and internal API](exemptions.md)
