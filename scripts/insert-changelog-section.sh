#!/usr/bin/env bash

# Copies the section of a release from another changelog file into CHANGELOG.md, with its heading. The section goes
# above the first section of an earlier release, so the release sections stay in version order. Changes no file when
# CHANGELOG.md has a section of the release. Fails when the other file has no section of the release.

set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/lib/project-version.sh"

if [[ "$#" -ne 2 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Usage: scripts/insert-changelog-section.sh MAJOR.MINOR.PATCH SOURCE_CHANGELOG' >&2
  exit 1
fi
version="$1"
source_changelog="$2"
changelog="$watchdog_repository_dir/CHANGELOG.md"

# Prints the release version of a `## ` heading, or nothing for another heading.
readonly heading_version='
  function heading_version(heading) {
    if (match(heading, /^[0-9]+\.[0-9]+\.[0-9]+( - [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9])?[[:space:]]*$/)) {
      sub(/ - .*/, "", heading)
      sub(/[[:space:]]+$/, "", heading)
      return heading
    }
    return ""
  }
'

if awk -v version="$version" "$heading_version"'
  { sub(/\r$/, "") }
  /^## / && heading_version(substr($0, 4)) == version { found = 1 }
  END { exit !found }
' "$changelog"; then
  echo "CHANGELOG.md has a section of $version. No change."
  exit 0
fi

section="$(awk -v version="$version" "$heading_version"'
  { sub(/\r$/, "") }
  /^## / { in_section = heading_version(substr($0, 4)) == version }
  in_section { lines[++size] = $0 }
  END {
    while (size > 0 && lines[size] ~ /^[[:space:]]*$/) size--
    if (size == 0) {
      printf "%s has no section of %s.\n", FILENAME, version > "/dev/stderr"
      exit 1
    }
    for (line = 1; line <= size; line++) print lines[line]
  }
' "$source_changelog")"

updated="$(mktemp "${TMPDIR:-/tmp}/watchdog-changelog.XXXXXX")"
trap 'rm -f -- "$updated"' EXIT
SECTION="$section" awk -v version="$version" "$heading_version"'
  function later(a, b,   x, y, i) {
    split(a, x, ".")
    split(b, y, ".")
    for (i = 1; i <= 3; i++) if (x[i] + 0 != y[i] + 0) return x[i] + 0 > y[i] + 0
    return 0
  }
  { sub(/\r$/, "") }
  !inserted && /^## / {
    other = heading_version(substr($0, 4))
    if (other != "" && later(version, other)) {
      print ENVIRON["SECTION"]
      print ""
      inserted = 1
    }
  }
  { print }
  END {
    if (!inserted) {
      print ""
      print ENVIRON["SECTION"]
    }
  }
' "$changelog" > "$updated"
cat "$updated" > "$changelog"
