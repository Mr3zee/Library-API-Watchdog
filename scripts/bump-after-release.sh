#!/usr/bin/env bash

# Sets the library version to the next development version after a release: the next minor version by default, for
# example 0.3.0 after 0.2.1, or the next patch version with `patch`, for example 0.2.2. Changes no file when the version
# in library.properties is that version or later.

set -euo pipefail

script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
source "$script_dir/lib/project-version.sh"

part="${2:-minor}"
if [[ "$#" -lt 1 || "$#" -gt 2 || ( "$part" != minor && "$part" != patch ) || ! "$1" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo 'Usage: scripts/bump-after-release.sh MAJOR.MINOR.PATCH [minor|patch]' >&2
  exit 1
fi
released=("${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}")
if [[ "$part" == patch ]]; then
  next=("${released[0]}" "${released[1]}" "$((released[2] + 1))")
else
  next=("${released[0]}" "$((released[1] + 1))" 0)
fi
next_version="${next[0]}.${next[1]}.${next[2]}"

current_version="$(read_source_version)"
[[ "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]]
current=("${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}")

for part in 0 1 2; do
  if (( current[part] != next[part] )); then
    if (( current[part] > next[part] )); then
      break
    fi
    "$script_dir/update-version.sh" "$next_version"
    exit 0
  fi
done
echo "The library version $current_version is $next_version or later. No change."
