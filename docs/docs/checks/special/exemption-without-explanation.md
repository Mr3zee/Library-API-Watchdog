# Exemptions without explanation

`EXEMPTION_WITHOUT_EXPLANATION` reports any `@Intentionally*` exemption annotation usage whose
`reason` doesn't explain itself.

|                  |                                 |
|------------------|---------------------------------|
| Diagnostic       | `EXEMPTION_WITHOUT_EXPLANATION` |
| Default severity | Error (not configurable)        |
| Gradle property  | none                            |
| Exemption        | none                            |

## What it reports

This check fires on the annotation call of `@Intentionally*` annotations with reasons other than
`ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` or `ExemptionReason.API_DESIGN` and a blank `description`:

```kotlin
// !hide-focused
/** Base type for components hosted by an application. */
// !diag[/@IntentionallyOpen/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyOpen","OTHER"]
@IntentionallyOpen
public open class Component
```

## Rationale

An exemption is supposed to be a deliberate, documented decision, not a silent escape hatch. A
bare `@Intentionally*` call with no self-explanatory reason and no description records nothing for
the next reader: a reviewer can't tell whether the shape was chosen on purpose or the warning was
just muted to make the build pass. Requiring an explanation is what keeps every other exemption in
this plugin trustworthy, so this check is always an error and can't be turned off.

Self-explanatory reasons are:
- `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` - The exempted shape is kept to stay compatible with existing users.
- `ExemptionReason.API_DESIGN` - The exempted shape is a deliberate part of the API design.

Non-self-explanatory reasons are:
- `ExemptionReason.INTEROP` - The exempted shape is dictated by interoperability with another language, platform, or
  framework. Which interop constraint applies is not obvious from the entry alone, so the
  `description` must still name it.
- `ExemptionReason.EXTERNAL_CONTRACT` - The exempted shape mirrors an externally defined contract - a specification, a protocol,
  or a closed real-world domain. Which contract is mirrored is not obvious from the entry
  alone, so the `description` must still name it.
- `ExemptionReason.IGNORE_JAVA_INTEROP` - The exempted declaration deliberately ignores Java interoperability. This reason marks the
  handful of spots where Java ergonomics are knowingly sacrificed - a library that doesn't
  support Java callers at all disables the Java-interop diagnostics wholesale in its build
  configuration instead. Why this particular declaration gets to ignore Java callers is not
  obvious from the entry alone, so the `description` must still explain it.
- `ExemptionReason.OTHER` - None of the other entries fits. This is the default, and it explains nothing by itself,
  so the exemption annotation must spell the motivation out in its `description`.


### Don't

```kotlin
// !hide-focused
/** Base type for UI elements rendered by an application. */
// !diag[/@IntentionallyOpen/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyOpen","OTHER"]
@IntentionallyOpen
public open class Widget

// !hide-focused
/** Base type for UI elements supplied by extensions. */
// !diag[/@IntentionallyOpen.*$/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyOpen","EXTERNAL_CONTRACT"]
@IntentionallyOpen(reason = ExemptionReason.EXTERNAL_CONTRACT)
public open class OtherWidget

// !diag[/@IntentionallyUndocumented.*$/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyUndocumented","OTHER"]
@IntentionallyUndocumented(description = "   ")
public class UndocumentedThing
```

### Do

```kotlin
// !hide-focused
/** A UI widget deliberately open to external subclasses. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget

// !hide-focused
/** Another UI widget deliberately open for internal testing. */
@IntentionallyOpen(description = "Kept open for internal testing")
public open class OtherWidget

@IntentionallyUndocumented(description = "There is no good reason to not document APIs")
public class UndocumentedThing
```


## Notes

- An exemption on a property promoted from a constructor parameter is validated once, on the
  property.
- [`@InternalAnnotationMarker`](../../exemptions.md#internal-api-annotations) is a different annotation, 
  not one of the exemption annotations this check covers:
  the marked annotation class documents the internal API surface itself and needs no `reason` or `description`.

## How to satisfy it

There is no exemption annotation for this check - exempting an explanation requirement would
defeat its purpose. The only way to silence it on a given `@Intentionally*` usage is to actually
explain that exemption: use `FOR_BACKWARDS_COMPATIBILITY` or `API_DESIGN` alone, or add a
non-blank `description` next to any other reason.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Data class exemption](../data-class-public-api.md#exemption) for an example check that defines an exemption annotation
