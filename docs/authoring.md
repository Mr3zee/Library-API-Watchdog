# Documentation authoring guide

The reader of every page is a library author. They know Kotlin and don't read the plugin source.
`DOCS_GUIDELINE.md` and the `code-doc-writer` skill give the text rules. This page gives the shape of
each page and what the build checks.

## Pages

| Page | Holds |
|---|---|
| `docs/overview.md` | The setup in short, one sample, and the check list. The check list is the source of truth for the order and the descriptions in `README.md`. |
| `docs/configuration.md` | The Gradle plugin, the `apiWatchdog` extension, and the compiler arguments. |
| `docs/existing-libs.md` | How to add the plugin to a library with published API. |
| `docs/report.md` | The report formats. |
| `docs/exemptions.md` | The `@Intentionally*` annotations and the internal API annotations. |
| `docs/abi-validation-suggestion.md` | The BCV suggestion. |
| `docs/checks/java-interop/java-interop.md` | The Java interop group: the switch for the group and the per-declaration exemption. |
| `docs/checks/**` | One page for each check. The three stateful-class diagnostics share one page. |

A fact has one page. Another page links to it.

A new check page goes to the path that the `docs` field of its entry in `diagnostics.json` gives,
and to:

- `docs/sidebars.ts`, alphabetical in its group.
- The check list in `docs/overview.md`.
- The check list in `README.md`, with the same order and the same description.

## Shape of a check page

Sections, in this order. Omit a section that has nothing to say.

1. The title. It is the `title` of the diagnostic in `diagnostics.json`.
2. One sentence: the diagnostic name and what it reports.
3. The fact table, with no header row:

   | Row | Holds |
   |---|---|
   | Diagnostic | The diagnostic name. |
   | Default severity | `Error`, or `Error (not configurable)` for a check without a severity property. |
   | Applies to | `JVM compilations only`. Only on a Java interop page. |
   | Gradle property | The severity property as a link to `configuration.md`, or `none`. |
   | Exemption | The annotation as a link to `exemptions.md`, or `none`. |

4. `## What it reports`: the condition in one or two sentences, then one sample. Name
   `@PublishedApi internal` when the check includes it. Don't list the report targets.
5. `## Rationale`: why the shape is hard to evolve. This section teaches, so it can be long.
   Each paragraph holds one fact. Parallel facts are a list. A sample shows what user code can do
   with the shape, or what breaks on a change. End with `### Don't` and `### Do`, one sample each.
   Link the Kotlin library authors' guideline when one covers the check.
6. `## Notes`: a list of what the check doesn't report and the corner cases.
7. `## Exemption`: the marker and the standard paragraph ([Diagnostic exemption
   wording](#diagnostic-exemption-wording)), then when to use the exemption, with one sample that
   gives an `ExemptionReason`.
8. `## Configuration`: the `apiWatchdog { }` block, then the `-P` compiler argument. On a Java
   interop page, the property is in the `javaInterop { }` block.
9. `## See also`: links to external guides and related pages.

Each section has one topic. The common case comes before the exceptions. A rule comes before the
sample that shows it.

## Terms

- **public API** is effective public or protected visibility. Don't shorten it to "public". Don't
  write "public or protected" on a check page.
- A diagnostic name is the name users see in compiler output, for example `DATA_CLASS_PUBLIC_API`.
- A diagnostic name outside its own page is a link to its check page. On its own page it is
  never a link. In a table row that already links the check page, the name can be plain.
- A link to a check page uses the check title as the link text.
- No FIR, checker, or other class names from the plugin source.
- Straight quotes only. `npm test` and `npm run build` don't check this.

Sources for a claim:

- `diagnostics.json`
- The checker sources in `compiler-plugin/src/main/kotlin/org/jetbrains/kotlinx/library/api/watchdog/fir/`
- The annotation KDoc in `plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlinx/library/api/watchdog/WatchdogAnnotations.kt`
- `WatchdogGradleExtension.kt` in the Gradle plugin

## Kotlin samples

Every Kotlin fence with a `public` declaration compiles in explicit API mode with every check on.
It must report exactly the diagnostics that its `!diag` annotations declare. A fence without a
`public` declaration, such as a configuration block, isn't compiled.

The annotations in a Kotlin fence:

| Annotation | Effect |
|---|---|
| `// !diag[/range/] NAME ["arg", ...]` | The diagnostic that the compiler reports on `range` in the next line. |
| `// !hide-focused` | Hides the next line in the Focused view. |
| `// !link[/range/] url` | Turns `range` in the next line into a link. |
| ` ```kotlin Name.kt ` | Shows a file name on the fence. |

The `!diag` annotation:

- The range is the one that the compiler reports. Take it from the matching marker under
  `compiler-plugin/src/test/data/diagnostics`.
- The arguments are the ones of the `reportOn` call in the checker, in placeholder order. The build
  checks the count against the message in `diagnostics.json`.
- A literal argument is a name from the sample.
- A wording argument, such as a declaration kind or a fix, is a `$name` or `$name(arguments)`
  reference. It points to the `parameterValues` of the diagnostic or to the `sharedParameterValues`.
  The build rejects a literal that has a space or equals a listed value.

A fence with a `!hide-focused` annotation renders with a Full and a Focused tab. Focused hides the
code that only satisfies other checks, such as KDoc. The full source compiles and passes the whole
audit.

The samples compile with the declarations of `docs.support` from `DocsExamplesTest.kt`. It declares
its own `@Poko` annotation without the Poko plugin, so the test ignores the three stateful-class
diagnostics on a `@Poko` class.

In a code span or a fence, `{{kotlinVersion}}` is the Kotlin version from `gradle/libs.versions.toml`,
and `{{libraryApiWatchdogVersion}}` is the Watchdog version from `gradle.properties`. The
`<IdeaGenerateShortcut />` component renders the IDE shortcut for the reader's platform.

## Diagnostic exemption wording

The last paragraph of a diagnostic message is its exemption guidance. The check page holds the
wording, in the `## Exemption` section, after a marker with the diagnostic name:

```markdown
<!-- diagnostic-exemption: DIAGNOSTIC_NAME -->
If this is intended, apply `@IntentionallyExample` to the declaration.
```

The paragraph starts with "If this is intended, apply `@Intentionally". The stateful-class page
uses a `diagnostic-exemption-table` marker over its table of the three exemptions instead.

A message can have a kind argument where the page names every declaration kind. Then a substitution
marker between the exemption marker and the paragraph maps the words to the argument:

```markdown
<!-- diagnostic-exemption: EXAMPLE_DIAGNOSTIC -->
<!-- diagnostic-exemption-substitution: function or constructor => {0} -->
If this is intended, apply `@IntentionallyExample` to the function or constructor.
```

After a change to a marked paragraph, run `npm run sync:diagnostic-exemptions` from `docs/`. It
copies the wording into `diagnostics.json`.

## Checks

From `docs/`, after `npm ci`:

| Command | Checks |
|---|---|
| `npm test` | The `!diag` annotations, the diagnostic-name links, and the check list of `README.md` against `docs/overview.md`. |
| `npm run check:diagnostic-exemptions` | The exemption wording of every diagnostic against its page. |
| `npm run build` | Links and anchors. |

From the repository root:

```bash
./gradlew :kotlin-library-api-watchdog-gradle-plugin:k240FunctionalTest \
  --tests org.jetbrains.kotlinx.library.api.watchdog.DocsExamplesTest
```

It compiles the Kotlin samples. The `docs.yml` workflow runs all four.
