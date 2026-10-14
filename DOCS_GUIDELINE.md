# Docs guideline

Project rules for the `code-doc-writer` skill. They win over the skill.

## Purpose of this project

The plugin has two purposes: it checks library API, and it teaches API
authors why each check exists. The teaching lives on the documentation
website (`docs/`). The website can explain at length. Every other place is
read often and must stay short:

- KDoc on public declarations
- Inline comments
- Diagnostic messages in `diagnostics.json` and in the Gradle plugin
- Gradle task and configuration descriptions

A rationale belongs on the website. A KDoc or a message links to the
check page instead of repeating it.

## Places a reader meets the API

The same fact can appear on the website, in the README, and in the KDoc
of a public declaration. These are different entry points. Each one
carries the facts its reader needs there.

## Text rules

- Diagnostic names in public docs are the names users see in compiler
  output and in `WatchdogChecks`, for example `DATA_CLASS_PUBLIC_API`.
- A link to a check page uses the check title as the link text.
- A sentence that only points the reader to a page has the form
  "See [title](link) for more details [about <brief specifics>]." Add the specifics only when
  they make the pointer clearer. Several pages go into one sentence: "See [1] and [2] for more
  details" or "See [1], [2], and [3] for more details". A link inside a sentence that states a
  fact stays where it is.
