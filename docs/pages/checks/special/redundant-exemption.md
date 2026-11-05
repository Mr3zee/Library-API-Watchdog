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
- In a multiplatform project, an exemption on an `expect` declaration is redundant when neither the
  `expect` declaration nor an `actual` declaration needs it. The compilation that reports it depends on
  the check:
  - A check that reports `expect` declarations, such as
    [Undocumented public API](../undocumented-public-api.md) or
    [Boolean parameters in public API](../boolean-parameter-public-api.md), decides from the `expect`
    declaration. Every compilation and the IDE report the exemption.
  - A check that reports `actual` declarations, such as
    [Data classes in public API](../data-class-public-api.md) or
    [Mangled JVM names in public API](../java-interop/mangled-jvm-name-public-api.md), needs them. A
    platform compilation reports the exemption when its own `actual` declaration doesn't need it. The
    common compilation and the IDE don't see `actual` declarations, so they don't report it.

  The copy of the exemption on an `actual` declaration isn't reported while the `expect` declaration
  has the exemption. After you remove the exemption from the `expect` declaration, the copy is an
  exemption of the `actual` declaration alone and gets its own verdict.
  [`exemptExistingApi`](../../existing-libs.md) removes the copies with the exemption on the `expect`
  declaration. See
  [Exemptions in a multiplatform project](../../exemptions.md#exemptions-in-a-multiplatform-project)
  for more details about the copies.
- An [annotation ignore rule](../../exemptions.md#annotation-ignore-rules) doesn't make an exemption
  redundant.
- [`exemptExistingApi`](../../existing-libs.md) removes a redundant exemption, and the import of the
  annotation when the file doesn't have another use of it.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Exemptions without explanation](./exemption-without-explanation.md)
