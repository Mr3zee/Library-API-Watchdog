# Documentation authoring guide

Rules and templates for the Writerside topics under `docs/pages/libs-api-watchdog/topics/`.
The site is built by `.github/workflows/docs.yml`: Writerside instance `lw` plus a Dokka API
reference served under `/api`.

## Hard rules

- Never use the em dash character (U+2014). Use a plain hyphen, a colon, or rewrite the sentence.
  Also avoid smart quotes; use straight ASCII quotes.
- Naming: the product is `libs-api-watchdog`. The Gradle plugin id is
  `org.jetbrains.kotlinx.libs.api.watchdog`. The Gradle extension is `apiWatchdog`. The
  annotations package is `org.jetbrains.kotlinx.libs.api.watchdog`.
- Every Kotlin API example must compile in explicit API mode: `public` modifiers and explicit
  return types on all API declarations.
- Exemption examples must satisfy `EXEMPTION_WITHOUT_EXPLANATION`: `reason =
  ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` or `ExemptionReason.API_DESIGN` may stand alone;
  every other reason (`INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, `OTHER`) also needs a
  non-empty `description`.
- Exactly one `#` heading per page, at the top. It is the topic title shown in the TOC.
- Link between topics by bare file name, regardless of folder: `[Exemptions](exemptions.md)`.
  Never use relative paths like `../exemptions.md`.
- Writerside substitutes `%var%` variables everywhere, including fenced code blocks. Available:
  `%product%`, `%libs-api-watchdog-version%`, `%kotlin-version%`, `%repo-root-path%`, `%host%`.
  Escape a literal percent sign as `%%`.
- American English. Concise, active voice, no marketing fluff. Do not mention implementation
  details (FIR, checker class names) on user-facing pages.
- Facts must match the sources of truth: `README.md`, the checker sources in
  `compiler-plugin/src/main/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/fir/`, the diagnostic
  messages in `WatchdogDiagnostics.kt` there, the annotation KDoc in
  `plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/WatchdogAnnotations.kt`,
  and the extension in `gradle-plugin/src/main/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/WatchdogGradleExtension.kt`.
- No imports in code snippets

## Topic map

Root (`topics/`):

| File                           | Title                                      |
|--------------------------------|--------------------------------------------|
| `overview.md`                  | Get started                                |
| `configuration.md`             | Configuration                              |
| `exemptions.md`                | Exemptions and internal API                |
| `tapmoc-suggestion.md`         | Tapmoc suggestion                          |
| `abi-validation-suggestion.md` | Binary compatibility validation suggestion |

Checks (`topics/checks/`):

| File                                                  | Title                                     |
|-------------------------------------------------------|-------------------------------------------|
| `open-api-without-subclass-opt-in.md`                 | Open API without subclass opt-in          |
| `subclass-opt-in-without-markers.md`                  | Subclass opt-in without markers           |
| `exhaustive-public-api.md`                            | Exhaustive public API                     |
| `undocumented-public-api.md`                          | Undocumented public API                   |
| `function-type-alias-public-api.md`                   | Function type aliases in public API       |
| `data-class-public-api.md`                            | Data classes in public API                |
| `stateful-class-without-equals-hashcode-to-string.md` | Stateful classes without toString         |
| `mutable-collection-public-api.md`                    | Mutable collections in public API         |
| `pair-or-triple-public-api.md`                        | Pair and Triple in public API             |
| `boolean-parameter-public-api.md`                     | Boolean parameters in public API          |
| `nullable-boolean-public-api.md`                      | Nullable Booleans in public API           |
| `required-parameter-after-optional.md`                | Required parameters after optional ones   |
| `inconsistent-parameter-order-in-overloads.md`        | Inconsistent parameter order in overloads |
| `inline-function-with-logic.md`                       | Inline functions with logic               |
| `exemption-without-explanation.md`                    | Exemptions without explanation            |
| `dsl-marker-noop-target.md`                           | DSL markers with no-op targets            |
| `dsl-marker-without-explicit-targets.md`              | DSL markers without explicit targets      |
| `dsl-marker-noop-type-position.md`                    | DSL markers on no-op type positions       |

Java interop (`topics/checks/java-interop/`):

| File                                          | Title                                   |
|-----------------------------------------------|-----------------------------------------|
| `java-interop.md`                             | Java interop checks                     |
| `mangled-jvm-name-public-api.md`              | Mangled JVM names in public API         |
| `kotlin-only-api-without-jvm-synthetic.md`    | Kotlin-only API without JvmSynthetic    |
| `companion-api-without-jvm-static.md`         | Companion API without JvmStatic         |
| `companion-constant-without-jvm-field.md`     | Companion constants without JvmField    |
| `top-level-api-without-jvm-name.md`           | Top-level API without JvmName           |
| `default-parameters-without-jvm-overloads.md` | Default parameters without JvmOverloads |

## Check page template

Use exactly this structure and section order for every page under `checks/`:

```markdown
# <Human title from the topic map>

`<DIAGNOSTIC_NAME>` reports <one sentence: what shape is flagged>.

|                  |                                      |
|------------------|--------------------------------------|
| Diagnostic       | `<DIAGNOSTIC_NAME>`                  |
| Default severity | Error                                |
| Gradle property  | [`<propertyName>`](configuration.md) |
| Exemption        | [`@Intentionally<X>`](exemptions.md) |

## What it reports

Two or three sentences on the exact scope, plus a minimal triggering example:

    ```kotlin
    // <DIAGNOSTIC_NAME>
    public data class User(val name: String)
    ```

## Rationale

Why this shape is hard to evolve or hurts API quality. Ground it in binary or source
compatibility, call-site readability, or debuggability. Link the relevant Kotlin library
authors' guidelines page.

## Don't

    ```kotlin
    // the hazardous shape, possibly annotated with a comment on what breaks later
    //
    // <DIAGNOSTIC_NAME>
    <example-dont>
    ```

## Do

    ```kotlin
    // the evolvable alternative
    <example-do>
    ```

Repeat Don't/Do pairs for distinct scenarios when the check has several. Cover the notable
edge cases and the deliberate exceptions the checker implements (a short list is fine).

## When to exempt

When keeping the shape is a deliberate decision. Show the exemption annotation with a
fitting reason and description. Mention the supported placements (declaration, single
parameter, type usage, containing class) when the annotation has several.

## Configuration

    ```kotlin
    apiWatchdog {
        <propertyName> = WatchdogSeverity.WARNING
    }
    ```

With direct compiler invocation:
\```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=<DIAGNOSTIC_NAME>:warning
\```

## See also

- Kotlin guide links (from the README entry for this diagnostic)
- Related check pages
```

Adjustments:

- Java interop checks add a table row `| Applies to | JVM compilations only |` and a sentence in
  Configuration: the whole group is disabled with `javaInterop { enabled = false }`; the property
  lives inside the `javaInterop { }` block. Link [Java interop checks](java-interop.md) in the
  intro or See also.
- `EXEMPTION_WITHOUT_EXPLANATION` is always an error: its table says
  `| Default severity | Error (not configurable) |`, `| Gradle property | none |`,
  `| Exemption | none |`, and it has no Configuration section.
- Checks without an exemption annotation write `| Exemption | none |` and replace the
  "When to exempt" section with how to legitimately silence the check, if anything.
- Target length 60 to 140 lines. Prefer fewer, sharper examples over exhaustive enumeration;
  the deliberate-exception lists from README.md can be compressed to bullets.

## Structural pages

`overview.md`, `configuration.md`, `configuration.md`, `exemptions.md`, `tapmoc-suggestion.md`, and
`java-interop.md` do not use the check template. They follow the hard rules and keep the same
tone; their outlines are defined by the task that produces them.
