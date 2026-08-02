# Documentation authoring guide

Rules and templates for the pages under `docs/docs/`. The site is a [Docusaurus](https://docusaurus.io/)
project rooted at `docs/`, built by `.github/workflows/docs.yml` together with a Dokka API reference
served under `/api`.

```bash
cd docs
npm install
npm start    # dev server with hot reload
npm run build
```

`npm run build` fails on a broken link, a broken anchor, and a code sample comment that names a
diagnostic missing from `diagnostics.json`, so it is the docs check as well.

## Hard rules

- Never use the em dash character (U+2014). Use a plain hyphen, a colon, or rewrite the sentence.
  Also avoid smart quotes. Use straight ASCII quotes.
- Naming: the product is `libs-api-watchdog`. The Gradle plugin id is
  `org.jetbrains.kotlinx.libs.api.watchdog`. The Gradle extension is `apiWatchdog`. The
  annotations package is `org.jetbrains.kotlinx.libs.api.watchdog`.
- Every Kotlin API example must compile in explicit API mode: `public` modifiers and explicit
  return types on all API declarations.
- Exemption examples must satisfy `EXEMPTION_WITHOUT_EXPLANATION`: `reason =
  ExemptionReason.FOR_BACKWARDS_COMPATIBILITY` or `ExemptionReason.API_DESIGN` may stand alone.
  Every other reason (`INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, `OTHER`) also needs a
  non-empty `description`.
- Exactly one `#` heading per page, at the top. It is the page title shown in the sidebar.
- Use standard Docusaurus relative links between pages, including the path from the current file:
  `[Exemptions](../exemptions.md)`. Always provide descriptive link text.
- Write product names, versions, and URLs directly in page content. `docs/variables.mjs` is only
  for shared site configuration in `docusaurus.config.ts` and `sidebars.ts`.
- American English. Concise, active voice, no marketing fluff. Don't mention implementation
  details (FIR, checker class names) on user-facing pages.
- Facts must match the sources of truth: `README.md`, the shared diagnostic registry
  `diagnostics.json` at the repository root, the checker sources in
  `compiler-plugin/src/main/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/fir/`, the annotation KDoc in
  `plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/WatchdogAnnotations.kt`,
  and the extension in `gradle-plugin/src/main/kotlin/org/jetbrains/kotlinx/libs/api/watchdog/WatchdogGradleExtension.kt`.
- No imports in code snippets

## Code samples

Fenced Kotlin blocks are rendered by [Code Hike](https://codehike.org/). Mark the exact range the
compiler reports with a Code Hike `diag` annotation. Hovering or focusing its solid red underline
opens a diagnostic tooltip linked to the check page:

```kotlin
// !diag[/Point/] DATA_CLASS_PUBLIC_API ["Point"]
// !diag[/Point/] UNDOCUMENTED_PUBLIC_API ["class","Point"]
public data class Point(public val x: Int, public val y: Int)
```

Use the inline regex range from the matching `<!DIAGNOSTIC!>...<!>` marker under
`compiler-plugin/src/test/data/diagnostics`. After the diagnostic name, provide the parameters
passed to `reportOn` as a JSON string array, in placeholder order. Reports on the same range use
separate annotations and are combined into one tooltip. Reports on different parts of the next
code line stay independent:

```kotlin
// !diag[/tags/] UNDOCUMENTED_PUBLIC_API ["property","tags"]
// !diag[/MutableList<String>/] MUTABLE_COLLECTION_PUBLIC_API ["property","tags","MutableList"]
public val tags: MutableList<String>
```

The name has to exist in `diagnostics.json`, the parameter count has to fill every message
placeholder, and the regex has to match, otherwise the build fails. Every other comment stays as
written and is shown as part of the sample.

When a long parameter value is shared with the compiler checker, define it once in the
diagnostic's `parameterValues` object in `diagnostics.json`. Reference a value with `$name`, or
fill its own placeholders with `$name(argument1,argument2)`:

```kotlin
// !diag[/refresh/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["refresh","$suspend"]
public suspend fun refresh() { }

// !diag[/onEach/] KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC ["onEach","$unitFunctionType(action)"]
public fun onEach(action: (Int) -> Unit) { }
```

The docs tooltip and generated compiler code resolve the same named values. Diagnostic messages
are rendered as Markdown in the tooltip, including inline code and links.

## Page map

Root (`docs/docs/`):

| File                           | Title                                      |
|--------------------------------|--------------------------------------------|
| `overview.md`                  | Get started                                |
| `configuration.md`             | Configuration                              |
| `existing-libs.md`             | Adding the plugin to existing libraries    |
| `exemptions.md`                | Exemptions and internal API                |
| `abi-validation-suggestion.md` | Binary compatibility validation suggestion |

Checks (`docs/docs/checks/`):

| File                                                  | Title                                                  |
|-------------------------------------------------------|--------------------------------------------------------|
| `open-api-without-subclass-opt-in.md`                 | Open API without subclass opt-in                       |
| `subclass-opt-in-without-markers.md`                  | Subclass opt-in without markers                        |
| `exhaustive-public-api.md`                            | Exhaustive public API                                  |
| `undocumented-public-api.md`                          | Undocumented public API                                |
| `function-type-alias-public-api.md`                   | Function type aliases in public API                    |
| `data-class-public-api.md`                            | Data classes in public API                             |
| `stateful-class-without-equals-hashcode-to-string.md` | Stateful classes without equals, hashCode, and toString |
| `mutable-collection-public-api.md`                    | Mutable collections in public API                      |
| `pair-or-triple-public-api.md`                        | Pair and Triple in public API                          |
| `boolean-parameter-public-api.md`                     | Boolean parameters in public API                       |
| `nullable-boolean-public-api.md`                      | Nullable Booleans in public API                        |
| `required-parameter-after-optional.md`                | Required parameters after optional ones                |
| `inconsistent-parameter-order-in-overloads.md`        | Inconsistent parameter order in overloads              |
| `inline-function-with-logic.md`                       | Inline functions with logic                            |

Special checks (`docs/docs/checks/special/`):

| File                                     | Title                                |
|------------------------------------------|--------------------------------------|
| `exemption-without-explanation.md`       | Exemptions without explanation       |
| `dsl-marker-noop-target.md`              | DSL markers with no-op targets       |
| `dsl-marker-without-explicit-targets.md` | DSL markers without explicit targets |
| `dsl-marker-noop-type-position.md`       | DSL markers on no-op type positions  |

Java interop (`docs/docs/checks/java-interop/`):

| File                                          | Title                                   |
|-----------------------------------------------|-----------------------------------------|
| `java-interop.md`                             | Java interop checks                     |
| `mangled-jvm-name-public-api.md`              | Mangled JVM names in public API         |
| `kotlin-only-api-without-jvm-synthetic.md`    | Kotlin-only API without JvmSynthetic    |
| `companion-api-without-jvm-static.md`         | Companion API without JvmStatic         |
| `companion-constant-without-jvm-field.md`     | Companion constants without JvmField    |
| `top-level-api-without-jvm-name.md`           | Top-level API without JvmName           |
| `default-parameters-without-jvm-overloads.md` | Default parameters without JvmOverloads |

A new page also needs an entry in `docs/sidebars.ts`, which defines the order of the sidebar.

## Check page template

Use exactly this structure and section order for every page under `checks/`:

```markdown
# <Human title from the page map>

`<DIAGNOSTIC_NAME>` reports <one sentence: what shape is flagged>.

|                  |                                      |
|------------------|--------------------------------------|
| Diagnostic       | `<DIAGNOSTIC_NAME>`                  |
| Default severity | Error                                |
| Gradle property  | [`<propertyName>`](../configuration.md) |
| Exemption        | [`@Intentionally<X>`](../exemptions.md) |

## What it reports

Two or three sentences on the exact scope, plus a minimal triggering example:

    ```kotlin
    // !diag[/<compiler-reported range>/] <DIAGNOSTIC_NAME> ["<parameter>"]
    public data class User(val name: String)
    ```

## Rationale

Why this shape is hard to evolve or hurts API quality. Ground it in binary or source
compatibility, call-site readability, or debuggability. Link the relevant Kotlin library
authors' guidelines page.

### Don't

    ```kotlin
    // the hazardous shape, possibly annotated with a comment on what breaks later
    //
    // !diag[/<compiler-reported range>/] <DIAGNOSTIC_NAME> ["<parameter>"]
    <example-dont>
    ```

### Do

    ```kotlin
    // the evolvable alternative
    <example-do>
    ```

Repeat Don't/Do pairs for distinct scenarios when the check has several. A repeated heading needs
an explicit Docusaurus id, spelled `### Don't {#dont-2}`.

## Notes

Any notes about the behaviour worth adding.
Cover the notable edge cases and the deliberate exceptions the checker implements (a short list is fine).

## Exemption

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
  Configuration: the whole group is disabled with `javaInterop { enabled = false }`. The property
  lives inside the `javaInterop { }` block. Link [Java interop checks](./java-interop.md) in the
  intro or See also. Since these pages are nested one level deeper, their links to root pages use
  `../../`, such as `../../configuration.md`.
- Special-check pages are also nested one level deeper and use `../../` for links to root pages.
- `EXEMPTION_WITHOUT_EXPLANATION` is always an error: its table says
  `| Default severity | Error (not configurable) |`, `| Gradle property | none |`,
  `| Exemption | none |`, and it has no Configuration section.
- Checks without an exemption annotation write `| Exemption | none |` and replace the
  "When to exempt" section with how to legitimately silence the check, if anything.
- Target length 60 to 140 lines. Prefer fewer, sharper examples over exhaustive enumeration.
  The deliberate-exception lists from README.md can be compressed to bullets.

## Structural pages

`overview.md`, `configuration.md`, `existing-libs.md`, `exemptions.md`,
`abi-validation-suggestion.md`, and `java-interop.md` don't use the check template. They follow the
hard rules and keep the same tone. Their outlines are defined by the task that produces them.
