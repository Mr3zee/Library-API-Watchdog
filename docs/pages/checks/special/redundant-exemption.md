# Redundant exemptions

|                  |                            |
|------------------|----------------------------|
| Diagnostic       | `REDUNDANT_EXEMPTION`      |
| Default severity | Warning (not configurable) |
| Gradle property  | none                       |
| Exemption        | none                       |

## What it reports

An `@Intentionally*` exemption that doesn't exempt anything on the element.

```kotlin
// !hide-focused
/** A connection to the server. */
// !diag[/@IntentionallyDataClass.*\)/] REDUNDANT_EXEMPTION ["IntentionallyDataClass","$declaration"]
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public class Connection
```

The common cases:

- The declaration isn't public API: `internal`, `private`, `@PublishedApi internal`, an override, or
  [internal API](../../exemptions.md#internal-api-annotations).
- The declaration doesn't have the shape the check reports, as in the sample.
- A broader exemption covers the same element: on the function and on its parameter, or on a class
  and on a member. The check reports the narrower one. Two overloads that both carry
  `@IntentionallyInconsistentParameterOrder` cover each other, and the check doesn't report either
  of them.
- The exemption is on an element the check doesn't visit: `@IntentionallyBooleanParameter` on a
  constructor parameter, or `@IntentionallyKotlinOnlyApi` on an interface member.

## Rationale

A redundant exemption states that a shape is intended, but the shape doesn't exist. A reader looks
for it. A later edit can give the declaration the shape, and the old exemption then hides the new
finding.

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

- The [report](../../report.md) tags the annotation as **Redundant**.
- A check with every diagnostic set to `NONE` reports no redundant exemption. A diagnostic set to
  `NONE` next to an enabled diagnostic of the same check keeps the exemptions it needs when it is on.
- The check skips `expect`, `actual`, and local declarations.
- An [annotation ignore rule](../../exemptions.md#annotation-ignore-rules) doesn't make an exemption
  redundant.
- [`exemptExistingApi`](../../existing-libs.md) removes a redundant exemption, and the import of the
  annotation when the file has no other use of it.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Exemptions without explanation](./exemption-without-explanation.md)
