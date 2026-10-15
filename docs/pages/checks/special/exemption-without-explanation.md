# Exemptions without explanation

|                  |                                 |
|------------------|---------------------------------|
| Diagnostic       | `EXEMPTION_WITHOUT_EXPLANATION` |
| Default severity | Error (not configurable)        |
| Gradle property  | none                            |
| Exemption        | none                            |

## What it reports

An `@Intentionally*` exemption with a `reason` parameter and a blank `description`, unless
the reason is `FOR_BACKWARDS_COMPATIBILITY`, `API_DESIGN`, or `FOR_DEPRECATION`.

`OTHER` is the default reason. A whitespace-only `description` counts as blank.

```kotlin
// !hide-focused
/** Base type for components hosted by an application. */
// !diag[/@IntentionallyOpen/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyOpen","OTHER"]
@IntentionallyOpen
public open class Component
```

## Rationale

A reason such as `INTEROP` doesn't identify the language, platform, or framework that needs the
exemption. Name that requirement in `description`.

### Don't

```kotlin
// !hide-focused
/** Base type for UI elements rendered by an application. */
// !diag[/@IntentionallyOpen/] EXEMPTION_WITHOUT_EXPLANATION ["IntentionallyOpen","OTHER"]
@IntentionallyOpen
public open class Widget
```

### Do

```kotlin
// !hide-focused
/** A UI widget open to external subclasses. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Widget
```

## Notes

- The check inspects declaration and type-usage exemptions, including both `expect` and `actual`
  usages.
- `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` has no `reason` parameter and needs
  no `description`.

## See also

- [Exemptions and internal API](../../exemptions.md)
- [Data class exemption](../data-class-public-api.md#exemption) for an example check that defines an exemption annotation
