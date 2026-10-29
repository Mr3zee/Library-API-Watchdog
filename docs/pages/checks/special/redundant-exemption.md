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

## Notes

- The [report](../../report.md) tags the annotation as **Redundant**.
- A check set to `NONE` doesn't report anything, so its exemptions are redundant.
  [`exemptExistingApi`](../../existing-libs.md) removes them. To keep them, turn the check on
  before you run the task.
- An exemption on an `expect` declaration is redundant when neither the `expect` nor its `actual`
  declaration needs it. Each platform compilation reports it for its own `actual`. The common
  compilation doesn't report it.
- When the `expect` and the `actual` declaration carry the same exemption, the one on the `actual`
  is redundant.
- An [annotation ignore rule](../../exemptions.md#annotation-ignore-rules) doesn't make an exemption
  redundant.
- [`exemptExistingApi`](../../existing-libs.md) removes a redundant exemption, and the import of the
  annotation when the file doesn't have another use of it.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Exemptions without explanation](./exemption-without-explanation.md)
