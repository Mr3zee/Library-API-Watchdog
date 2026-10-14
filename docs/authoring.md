# Documentation authoring guide

The reader of every page is a library author. They know Kotlin and don't read the plugin source.
`DOCS_GUIDELINE.md` and the `code-doc-writer` skill give the text rules. This page gives what the
existing pages don't show.

A fact has one page. Another page links to it. The check list in `docs/pages/overview.md` is the
source of truth for the order and the descriptions in `README.md`.

## Check pages

Copy the shape of an existing check page. The sections, in order: the fact table, `What it
reports`, `Rationale`, `Notes`, `Exemption`, `Configuration`, `See also`. Omit an empty section.

- The title is the `title` of the diagnostic in `diagnostics.json`.
- `What it reports` gives the condition and one sample. It names `@PublishedApi internal` when the
  check includes it. It doesn't list the report targets.
- `Rationale` teaches, so it can be long. A sample shows what user code can do with the shape, or
  what breaks on a change. It ends with `### Don't` and `### Do`, one sample each.
- `Notes` lists what the check doesn't report and the corner cases.
- `Exemption` holds the standard paragraph ([Diagnostic exemption wording](#diagnostic-exemption-wording)),
  then when to use the exemption, with one sample that gives an `ExemptionReason`.

## Terms

- **public API** is effective public or protected visibility. Don't shorten it to "public". Don't
  write "public or protected" on a check page.
- A diagnostic name outside its own page is a link to its check page. On its own page it is
  never a link.
- No FIR, checker, or other class names from the plugin source.
- Straight quotes only. The build doesn't check this.

Sources for a claim:

- `diagnostics.json`
- The checker sources in `compiler-plugin/src/main/kotlin/org/jetbrains/kotlinx/library/api/watchdog/fir/`
- The annotation KDoc in `plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlinx/library/api/watchdog/WatchdogAnnotations.kt`
- `WatchdogGradleExtension.kt` in the Gradle plugin

## Kotlin samples

Every Kotlin fence with a `public` declaration compiles in explicit API mode with every check on.
It must report exactly the diagnostics that its `!diag` annotations declare. A fence without a
`public` declaration isn't compiled.

The `!diag` annotation:

- The range is the one that the compiler reports. Take it from the matching marker under
  `compiler-plugin/src/test/data/diagnostics`.
- The arguments are the ones of the `reportOn` call in the checker, in placeholder order.
- A `$name` argument points to the `parameterValues` of the diagnostic or to the
  `sharedParameterValues`. The build rejects a literal that has a space or equals a listed value.

`!hide-focused` hides the next line in the Focused tab of the fence. The full source compiles.

The samples compile with the declarations of `docs.support` from `DocsExamplesTest.kt`. It declares
its own `@Poko` annotation without the Poko plugin, so the test ignores the three stateful-class
diagnostics on a `@Poko` class.

## Diagnostic exemption wording

The last paragraph of a diagnostic message is its exemption guidance. The check page is the source:
the paragraph after the `diagnostic-exemption` marker in its `## Exemption` section. A
`diagnostic-exemption-substitution` marker maps the words of the paragraph to a message argument.

After a change to a marked paragraph, run `npm run sync:diagnostic-exemptions` from `docs/`. It
copies the wording into `diagnostics.json`.

## Checks

`CLAUDE.md` lists the `npm` checks. The Kotlin samples compile with:

```bash
./gradlew :kotlin-library-api-watchdog-gradle-plugin:k240FunctionalTest \
  --tests org.jetbrains.kotlinx.library.api.watchdog.DocsExamplesTest
```
