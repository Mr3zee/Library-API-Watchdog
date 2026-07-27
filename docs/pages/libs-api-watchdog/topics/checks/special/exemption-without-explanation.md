# Exemptions without explanation

`EXEMPTION_WITHOUT_EXPLANATION` reports any `@Intentionally*` exemption annotation usage whose
`reason` does not explain itself (anything other than `FOR_BACKWARDS_COMPATIBILITY` or
`API_DESIGN`, including the default `OTHER`, `INTEROP`, `EXTERNAL_CONTRACT`, and
`IGNORE_JAVA_INTEROP`) while `description` is empty or blank: such a bare exemption explains
nothing. See
[Exemptions and internal API](exemptions.md) for the full exemption mechanism this check enforces.

| | |
|---|---|
| Diagnostic | `EXEMPTION_WITHOUT_EXPLANATION` |
| Default severity | Error (not configurable) |
| Gradle property | none |
| Exemption | none |

## What it reports

Every `@Intentionally*` annotation call must explain itself, wherever it is written: on a
declaration, a single parameter, a type parameter, or a type usage. This check validates the
annotation call itself, not the annotated declaration, so it fires on every usage regardless of
the annotated declaration's visibility - including `internal` and `private` declarations, and
declarations inside a subtree marked `@InternalAnnotationMarker`:

```kotlin
// EXEMPTION_WITHOUT_EXPLANATION
@IntentionallyOpen
public open class Widget
```

## Rationale

An exemption is supposed to be a deliberate, documented decision, not a silent escape hatch. A
bare `@Intentionally*` call with no self-explanatory reason and no description records nothing for
the next reader: a reviewer cannot tell whether the shape was chosen on purpose or the warning was
just muted to make the build pass. Requiring an explanation is what keeps every other exemption in
this plugin trustworthy, so this check is always an error and cannot be turned off.

## Don't

```kotlin
// EXEMPTION_WITHOUT_EXPLANATION
@IntentionallyOpen
public open class Widget

// EXEMPTION_WITHOUT_EXPLANATION
@IntentionallyOpen(reason = ExemptionReason.OTHER)
public open class OtherWidget

// EXEMPTION_WITHOUT_EXPLANATION
@IntentionallyUndocumented(description = "   ")
public class UndocumentedThing
```

None of these say anything: the default reason `OTHER` does not speak for itself, and an empty or
whitespace-only `description` adds nothing next to it.

## Do

```kotlin
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget

@IntentionallyOpen(description = "kept open for mocking")
public open class OtherWidget
```

Satisfy the check either way:

- Pick a reason that explains itself: `FOR_BACKWARDS_COMPATIBILITY` or `API_DESIGN`. The
  description can then stay empty.
- Any other reason - `INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, or `OTHER` - only
  categorizes the exemption, so it still needs a non-blank `description` next to it.

## Edge cases

- Checked wherever an `@Intentionally*` call appears, including type parameters
  (`<@IntentionallyMutableCollection T : MutableList<Int>>`) and type usages
  (`List<@IntentionallyMutableCollection MutableList<Int>>`).
- An exemption on a property promoted from a constructor parameter is validated once, on the
  property.
- `@InternalAnnotationMarker` is a different annotation, not one of the exemption annotations this
  check covers: the marked annotation class documents the internal API surface itself and needs no
  `reason` or `description`.

## How to satisfy it

There is no exemption annotation for this check - exempting an explanation requirement would
defeat its purpose. The only way to silence it on a given `@Intentionally*` usage is to actually
explain that exemption: use `FOR_BACKWARDS_COMPATIBILITY` or `API_DESIGN` alone, or add a
non-blank `description` next to any other reason.

## See also

- [Exemptions and internal API](exemptions.md)
- [Data classes in public API](../data-class-public-api.md) for an example check that defines an
  exemption annotation
