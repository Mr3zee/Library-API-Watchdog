#!/usr/bin/env bash

watchdog_repository_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

validate_library_version() {
  if [[ ! "${1:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ ]]; then
    echo 'Invalid library version. Use MAJOR.MINOR.PATCH with an optional alphanumeric suffix.' >&2
    return 1
  fi
}

read_source_version() {
  local source_version
  source_version="$(awk -F= '
    /^version=/ { count++; value = substr($0, length($1) + 2); sub(/\r$/, "", value) }
    END {
      if (count != 1) {
        print "gradle.properties must declare exactly one version property." > "/dev/stderr"
        exit 1
      }
      print value
    }
  ' "$watchdog_repository_dir/gradle.properties")" || return 1
  validate_library_version "$source_version" || return 1
  printf '%s\n' "$source_version"
}

read_library_version() {
  if [[ "${LIBRARY_VERSION+x}" == x ]]; then
    validate_library_version "$LIBRARY_VERSION" || return 1
    printf '%s\n' "$LIBRARY_VERSION"
  else
    read_source_version
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  read_library_version
fi
