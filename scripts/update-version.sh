#!/usr/bin/env bash

set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/lib/project-version.sh"

if [[ "$#" -ne 1 ]]; then
  echo 'Usage: scripts/update-version.sh VERSION|--check' >&2
  exit 1
fi

previous="$(read_source_version)"
check_only=false
if [[ "$1" == --check ]]; then
  check_only=true
  version="$previous"
else
  version="$1"
  validate_library_version "$version"
fi

version_tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/watchdog-version.XXXXXX")"
trap 'rm -rf -- "$version_tmpdir"' EXIT
mkdir -p "$version_tmpdir/docs"

awk -v version="$version" '
  /^version=/ { $0 = "version=" version }
  { print }
' "$watchdog_repository_dir/gradle.properties" > "$version_tmpdir/gradle.properties"

awk -v version="$version" '
  {
    remaining = $0
    result = ""
    while (match(remaining, /kotlin\("library\.api-watchdog(-report-aggregation)?"\)[[:space:]]+version[[:space:]]+"[^"]+"/)) {
      usage = substr(remaining, RSTART, RLENGTH)
      prefix = substr(remaining, 1, RSTART - 1)
      remaining = substr(remaining, RSTART + RLENGTH)
      sub(/version[[:space:]]+"[^"]+"$/, "version \"" version "\"", usage)
      result = result prefix usage
    }
    print result remaining
  }
' "$watchdog_repository_dir/README.md" > "$version_tmpdir/README.md"

for file in docs/package.json docs/package-lock.json; do
  awk -v version="$version" -v is_lock="$([[ "$file" == *package-lock.json ]] && echo 1 || echo 0)" '
    /^  "version":/ {
      sub(/"version": "[^"]+"/, "\"version\": \"" version "\"")
      top_versions++
    }
    /^    "": \{/ { root_package = 1 }
    /^    },?$/ { root_package = 0 }
    root_package && /^      "version":/ {
      sub(/"version": "[^"]+"/, "\"version\": \"" version "\"")
      package_versions++
    }
    { print }
    END {
      if (top_versions != 1 || (is_lock && package_versions != 1)) {
        print "Unexpected package version fields in " FILENAME "." > "/dev/stderr"
        exit 1
      }
    }
  ' "$watchdog_repository_dir/$file" > "$version_tmpdir/$file"
done

failures=0
for file in gradle.properties README.md docs/package.json docs/package-lock.json; do
  if ! cmp -s "$watchdog_repository_dir/$file" "$version_tmpdir/$file"; then
    if "$check_only"; then
      echo "$file has a stale library version. Run scripts/update-version.sh $previous." >&2
      failures=1
    else
      cat "$version_tmpdir/$file" > "$watchdog_repository_dir/$file"
    fi
  fi
done

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi
if "$check_only"; then
  echo "All library version references match $version."
else
  echo "Updated the library version from $previous to $version."
fi
