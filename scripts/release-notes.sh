#!/usr/bin/env bash

# Prints the GitHub generated release notes of a release: the pull requests up to HEAD since the previous release. The
# previous release is the highest local `vMAJOR.MINOR.PATCH` tag earlier than the release, so the notes of a backport
# start at the previous release of its line. Each heading is one level lower than in the GitHub notes, so the notes fit
# under the `## VERSION` heading of CHANGELOG.md. Needs `gh` with `GH_TOKEN`, `GITHUB_REPOSITORY`, and the tags of the
# repository.

set -euo pipefail

script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
source "$script_dir/lib/project-version.sh"

if [[ "$#" -ne 1 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Usage: scripts/release-notes.sh MAJOR.MINOR.PATCH' >&2
  exit 1
fi
version="$1"
: "${GITHUB_REPOSITORY:?Set GITHUB_REPOSITORY to OWNER/REPOSITORY.}"

previous="$(git -C "$watchdog_repository_dir" tag --list 'v*' | awk -v version="$version" '
  function later(a, b,   x, y, i) {
    split(a, x, ".")
    split(b, y, ".")
    for (i = 1; i <= 3; i++) if (x[i] + 0 != y[i] + 0) return x[i] + 0 > y[i] + 0
    return 0
  }
  /^v[0-9]+\.[0-9]+\.[0-9]+$/ {
    tag = substr($0, 2)
    if (later(version, tag) && (previous == "" || later(tag, previous))) previous = tag
  }
  END { if (previous != "") print "v" previous }
')"

fields=(-f "tag_name=v$version" -f "target_commitish=$(git -C "$watchdog_repository_dir" rev-parse HEAD)")
# Without a previous tag, GitHub lists the pull requests of the whole history.
if [[ -n "$previous" ]]; then
  fields+=(-f "previous_tag_name=$previous")
fi
gh api --method POST "repos/$GITHUB_REPOSITORY/releases/generate-notes" "${fields[@]}" --jq .body | awk '
  { sub(/\r$/, "") }
  /^#+ / { $0 = "#" $0 }
  { lines[++size] = $0 }
  END {
    while (size > 0 && lines[size] ~ /^[[:space:]]*$/) size--
    for (line = 1; line <= size; line++) print lines[line]
  }
'
