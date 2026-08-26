# Documentation authoring guide

This guide records constraints that are not evident from the existing pages. Follow neighboring
pages for structure, tone, linking, and presentation. Add new pages to `docs/sidebars.ts`.

## Validation

From `docs/`, run:

```bash
npm test
npm run build
```

The tests enforce diagnostic links and synchronization between the README and the check lists in
`docs/docs/overview.md`. The build rejects broken links, broken anchors, and invalid diagnostic
annotations.

From the repository root, run the CI check that compiles public-API samples in isolation with all
watchdog checks enabled:

```bash
./gradlew :kotlin-library-api-watchdog-gradle-plugin:k240FunctionalTest \
  --tests org.jetbrains.kotlinx.library.api.watchdog.DocsExamplesTest
```

## Content constraints

- Use American English, concise active voice, and straight ASCII punctuation. Never use an em dash
  or smart quotes.
- Keep implementation details such as FIR and checker class names out of user-facing pages.
- Use **public API** for effective public-or-protected visibility. Do not shorten that scope to
  just "public" or repeat "public or protected" on individual check pages. Explicitly mention
  `@PublishedApi internal` on pages for checks that include the published binary surface.
- Don't write report-target lists in the check page's **What it reports** section. Mention the
  target only when it helps explain the diagnostic or a non-obvious behavior.
- Verify technical claims against `README.md`, `diagnostics.json`, the checker sources under
  `compiler-plugin/src/main/kotlin/org/jetbrains/kotlinx/library/api/watchdog/fir/`, annotation KDoc
  in `plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlinx/library/api/watchdog/WatchdogAnnotations.kt`,
  and `WatchdogGradleExtension.kt` in the Gradle plugin.
- Treat `docs/docs/overview.md` as the source of truth for README check order and descriptions.
- Use `docs/variables.mjs` only for values shared by `docusaurus.config.ts` and `sidebars.ts`.
- Use `{{libraryApiWatchdogVersion}}` for the Watchdog version and `{{kotlinVersion}}` for the base
  Kotlin version in fenced or inline code. The docs build reads them from `gradle.properties` and
  `gradle/libs.versions.toml`, respectively.

## Diagnostic exemption wording

The exemption guidance in a diagnostic and its check page must use the same wording. Put the
canonical paragraph in the check page's **Exemption** section immediately after a marker keyed by
the diagnostic name:

```markdown
<!-- diagnostic-exemption: DIAGNOSTIC_NAME -->
If this API shape is intentional, apply `@IntentionallyExample` to the declaration.
```

From `docs/`, run `npm run sync:diagnostic-exemptions` to copy every marked paragraph into the final,
separate paragraph of its message in `diagnostics.json`. CI runs
`npm run check:diagnostic-exemptions` and fails when the registry is stale or a marker is missing.
The stateful-class page uses a `diagnostic-exemption-table` marker instead because its three
diagnostics share one page; the task composes their standard messages from the table rows.

## Kotlin sample checks

- Public API samples must compile in explicit API mode and produce exactly the diagnostics declared
  by their `!diag` annotations. Configuration fragments and comment-only placeholders are ignored.
- Take each annotation's regex range from the matching compiler test marker under
  `compiler-plugin/src/test/data/diagnostics`.
- Take diagnostic arguments from the checker's `reportOn` call and list them in message-placeholder
  order. The diagnostic name, argument count, and range are validated against `diagnostics.json`.
- Put shared long argument values in the diagnostic's `parameterValues` entry in `diagnostics.json`.
  Reference them as `$name` or `$name(arguments)` so docs and compiler messages use the same value.
- The full source behind a Full/Focused sample must compile and pass the complete audit. Focused
  output may omit declarations or annotations used only to satisfy unrelated checks.
