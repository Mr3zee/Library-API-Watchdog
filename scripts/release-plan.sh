#!/usr/bin/env bash

# Prints the plan of a release as `key=value` lines for `GITHUB_OUTPUT`. Reads the `vMAJOR.MINOR.PATCH` tags of the
# repository. Fails when the version and the branch break these rules:
#
# - From `main`, the version must be later than every release tag.
# - From `release/MAJOR.MINOR.x`, the version must be in that line and later than every release tag of the line. The
#   line must have a release tag.
#
# When the tag of the version exists, an earlier run tested and tagged the release commit, so the script checks
# nothing. The keys:
#
# - `tagged`: `true` when the tag of the version exists.
# - `from_main`: `true` for a release from `main`.
# - `latest`: `true` when the version is later than every other release tag.
# - `docs_version`: the version that the website shows after the release, the highest release version.
# - `next_part`: the part that the next development version increments, `minor` from `main`, `patch` otherwise.

set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/lib/project-version.sh"

if [[ "$#" -ne 2 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Usage: scripts/release-plan.sh MAJOR.MINOR.PATCH BRANCH' >&2
  exit 1
fi

git -C "$watchdog_repository_dir" tag --list 'v*' | awk -v version="$1" -v branch="$2" '
  function later(a, b,   x, y, i) {
    split(a, x, ".")
    split(b, y, ".")
    for (i = 1; i <= 3; i++) if (x[i] + 0 != y[i] + 0) return x[i] + 0 > y[i] + 0
    return 0
  }
  function fail(message) {
    print message > "/dev/stderr"
    exit 1
  }
  BEGIN {
    split(version, parts, ".")
    line = parts[1] "." parts[2]
  }
  /^v[0-9]+\.[0-9]+\.[0-9]+$/ {
    tag = substr($0, 2)
    if (tag == version) {
      tagged = 1
      next
    }
    if (highest == "" || later(tag, highest)) highest = tag
    split(tag, parts, ".")
    if (parts[1] "." parts[2] == line && (line_highest == "" || later(tag, line_highest))) line_highest = tag
  }
  END {
    from_main = branch == "main"
    latest = highest == "" || later(version, highest)
    if (!tagged) {
      if (!from_main && branch != "release/" line ".x") {
        fail("Release " version " from main or from release/" line ".x, not from " branch ".")
      }
      if (from_main && !latest) {
        fail("Version " version " isn'\''t later than v" highest ". Release a backport from release/" line ".x.")
      }
      if (!from_main && line_highest == "") {
        fail("The " line " line has no release tag. Create release/" line ".x from a release tag.")
      }
      if (!from_main && !later(version, line_highest)) {
        fail("Version " version " isn'\''t later than v" line_highest ", the latest release of the " line " line.")
      }
    }
    print "tagged=" (tagged ? "true" : "false")
    print "from_main=" (from_main ? "true" : "false")
    print "latest=" (latest ? "true" : "false")
    print "docs_version=" (latest ? version : highest)
    print "next_part=" (from_main ? "minor" : "patch")
  }
'
