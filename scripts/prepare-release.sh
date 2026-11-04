#!/usr/bin/env bash

# Makes the changes of a release commit:
#
# 1. CHANGELOG.md gets the `## VERSION - YYYY-MM-DD` section in version order. The section holds the text of the
#    `## Unreleased` section, if CHANGELOG.md has one, and then the release notes in NOTES_FILE. The `## Unreleased`
#    section goes away. The date is the UTC date. `RELEASE_DATE` overrides it.
# 2. The library version changes to the next development version, the next `minor` or `patch` version after VERSION.
#    The version doesn't change when it's that version or later.
# 3. The website changelog gets the new section.
#
# Fails when CHANGELOG.md has a section of VERSION or more than one `## Unreleased` section, and when the new section
# has no text.

set -euo pipefail

script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
source "$script_dir/lib/project-version.sh"

if [[ "$#" -ne 3 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ! "$2" =~ ^(minor|patch)$ || ! -f "$3" ]]; then
  echo 'Usage: scripts/prepare-release.sh MAJOR.MINOR.PATCH minor|patch NOTES_FILE' >&2
  exit 1
fi
version="$1"
notes_file="$3"
date="${RELEASE_DATE:-$(date -u +%F)}"
if [[ ! "$date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo 'Invalid RELEASE_DATE. Use YYYY-MM-DD.' >&2
  exit 1
fi

changelog="$watchdog_repository_dir/CHANGELOG.md"
without_unreleased="$(mktemp "${TMPDIR:-/tmp}/watchdog-changelog.XXXXXX")"
unreleased="$(mktemp "${TMPDIR:-/tmp}/watchdog-unreleased.XXXXXX")"
section="$(mktemp "${TMPDIR:-/tmp}/watchdog-section.XXXXXX")"
trap 'rm -f -- "$without_unreleased" "$unreleased" "$section"' EXIT

awk -v version="$version" -v unreleased="$unreleased" '
  { sub(/\r$/, "") }
  /^## / {
    heading = substr($0, 4)
    sub(/[[:space:]]+$/, "", heading)
    in_unreleased = heading == "Unreleased"
    if (in_unreleased) {
      count++
      next
    }
    if (heading == version || index(heading, version " - ") == 1) released++
  }
  in_unreleased {
    print > unreleased
    next
  }
  # Blank lines at the end of the file go away, so the section goes after one blank line.
  /^[[:space:]]*$/ {
    blank++
    next
  }
  {
    for (; blank > 0; blank--) print ""
    print
  }
  END {
    if (released) {
      printf "CHANGELOG.md has a section of %s.\n", version > "/dev/stderr"
      exit 1
    }
    if (count > 1) {
      print "CHANGELOG.md must have at most one \"## Unreleased\" section, found " count "." > "/dev/stderr"
      exit 1
    }
  }
' "$changelog" > "$without_unreleased"

# Prints a file without the blank lines at its start and end.
trim() {
  awk '
    { sub(/\r$/, "") }
    /[^[:space:]]/ {
      for (; blank > 0; blank--) print ""
      print
      text = 1
      next
    }
    text { blank++ }
  ' "$1"
}
intro="$(trim "$unreleased")"
notes="$(trim "$notes_file")"
if [[ -z "$intro" && -z "$notes" ]]; then
  echo "The $version section has no text: $notes_file and the \"## Unreleased\" section are empty." >&2
  exit 1
fi

# insert-changelog-section.sh copies a section from a file in the format of a changelog.
{
  printf '# Changelog\n\n## %s - %s\n' "$version" "$date"
  for part in "$intro" "$notes"; do
    if [[ -n "$part" ]]; then
      printf '\n%s\n' "$part"
    fi
  done
} > "$section"

cat "$without_unreleased" > "$changelog"
"$script_dir/insert-changelog-section.sh" "$version" "$section" > /dev/null
"$script_dir/bump-after-release.sh" "$version" "$2"
node "$watchdog_repository_dir/docs/scripts/sync-changelog.mjs"
