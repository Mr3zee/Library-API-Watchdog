#!/usr/bin/env bash

# Prints the CHANGELOG.md section of a release without its heading. The heading is `## VERSION` or
# `## VERSION - YYYY-MM-DD`. Fails when the section is missing, empty, or duplicated.

set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/lib/project-version.sh"

if [[ "$#" -ne 1 ]]; then
  echo 'Usage: scripts/changelog-section.sh VERSION' >&2
  exit 1
fi
validate_library_version "$1"

awk -v version="$1" '
  function release_heading(heading, date) {
    if (heading == version) return 1
    if (index(heading, version " - ") != 1) return 0
    date = substr(heading, length(version) + 4)
    return date ~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$/
  }
  { sub(/\r$/, "") }
  /^## / {
    in_section = release_heading(substr($0, 4))
    if (in_section) count++
    next
  }
  in_section { lines[++size] = $0 }
  END {
    if (count != 1) {
      printf "CHANGELOG.md must have one \"## %s\" section, found %d.\n", version, count > "/dev/stderr"
      exit 1
    }
    first = 1
    while (first <= size && lines[first] ~ /^[[:space:]]*$/) first++
    while (size >= first && lines[size] ~ /^[[:space:]]*$/) size--
    if (first > size) {
      printf "The \"## %s\" section of CHANGELOG.md is empty. Add the changes of the release.\n", version > "/dev/stderr"
      exit 1
    }
    for (line = first; line <= size; line++) print lines[line]
  }
' "$watchdog_repository_dir/CHANGELOG.md"
