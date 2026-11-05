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
- In a multiplatform project, a platform compilation reports an exemption on an `expect` declaration
  and its copy on the `actual` declaration together. It reports them when neither the `expect` nor the
  `actual` declaration needs the exemption on this platform. The common compilation doesn't report these
  exemptions. See
  [Exemptions in a multiplatform project](../../exemptions.md#exemptions-in-a-multiplatform-project)
  for more details about the copies.
- An [annotation ignore rule](../../exemptions.md#annotation-ignore-rules) doesn't make an exemption
  redundant.
- [`exemptExistingApi`](../../existing-libs.md) removes a redundant exemption, and the import of the
  annotation when the file doesn't have another use of it.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Exemptions without explanation](./exemption-without-explanation.md)
