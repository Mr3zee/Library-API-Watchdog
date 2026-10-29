# Redundant exemptions

|                  |                            |
|------------------|----------------------------|
| Diagnostic       | `REDUNDANT_EXEMPTION`      |
| Default severity | Warning (not configurable) |
| Gradle property  | none                       |
| Exemption        | none                       |

## What it reports

An `@Intentionally*` exemption that changes nothing: without it, the check it belongs to reports
nothing on the annotated element.

```kotlin
// !hide-focused
/** A settled value holder. */
// !diag[/@IntentionallyDataClass.*\)/] REDUNDANT_EXEMPTION ["IntentionallyDataClass","$declaration"]
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public class Coordinates(public val x: Int, public val y: Int) {
    override fun equals(other: Any?): Boolean = other is Coordinates && other.x == x && other.y == y

    override fun hashCode(): Int = 31 * x + y

    override fun toString(): String = "Coordinates($x, $y)"
}
```

The check runs the owning check a second time with the exemption absent. The exemption is redundant
when that run reports nothing new. The common cases:

- The declaration isn't public API: it is `internal`, `private`, `@PublishedApi internal`, an
  override, or marked as [internal API](../../exemptions.md#internal-api-annotations).
- The declaration doesn't have the shape the check reports, as in the sample.
- A broader exemption covers the same element: an exemption on the function and on its parameter,
  or on a class and on a member. The check reports the narrower one. Two overloads that both carry
  `@IntentionallyInconsistentParameterOrder` are peers, and the check reports neither.
- The exemption is on an element the check never visits: `@IntentionallyBooleanParameter` on a
  constructor parameter, or `@IntentionallyKotlinOnlyApi` on an interface member.

The message names what the exemption covers: the declaration, the parameter, the type usage, the type
parameter, the file, or the class and its members.

## Rationale

An exemption is a statement about the API: this shape is intended. A redundant exemption makes a
statement about a shape that doesn't exist. A reader trusts it and looks for the shape. A later edit
can give the declaration the shape, and then the old exemption hides a new finding.

### Don't

```kotlin
// !hide-focused
/** A queue of messages. */
public class Queue {
    /** Reads a snapshot of the queue. */
    // !diag[/@IntentionallyMutableCollection.*\)/] REDUNDANT_EXEMPTION ["IntentionallyMutableCollection","$declaration"]
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public fun snapshot(): List<String> = emptyList()
}
```

### Do

```kotlin
// !hide-focused
/** A queue of messages. */
public class Queue {
    /** Reads a snapshot of the queue. */
    public fun snapshot(): List<String> = emptyList()
}
```

## Notes

- The check is a warning in every mode, [collection mode](../../existing-libs.md#collection-mode)
  included. The [report](../../report.md) lists the warning and tags the applied annotation as
  **Redundant**.
- A check set to `NONE` reports no redundant exemption. Its exemptions stay for the day the check is
  on again.
- An exemption on an `expect` or `actual` declaration is never reported. The checks split between the
  two declarations, and the exemption on the `expect` serves the `actual`.
- An [annotation ignore rule](../../exemptions.md#annotation-ignore-rules) doesn't make an exemption
  redundant. The check looks at the diagnostics before the rule removes them.
- [`exemptExistingApi`](../../existing-libs.md) removes a redundant exemption, and the import of the
  annotation when the file has no other use of it.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Exemptions without explanation](./exemption-without-explanation.md)
