#!/usr/bin/env bash

set -euo pipefail

readonly property_prefix='kotlin.compiler.plugin.devkit'
readonly properties_file='gradle.properties'
readonly badge_files=('README.md' 'docs/docs/overview.md')

read_property() {
  local property_name="$1"
  awk -F= -v property_name="$property_name" \
    '$1 == property_name { print substr($0, length(property_name) + 2) }' \
    "$properties_file"
}

readonly min_cli_version="$(read_property "${property_prefix}.minCliVersion")"
readonly min_idea_version="$(read_property "${property_prefix}.minIdeaVersion")"

if [[ -z "$min_cli_version" || -z "$min_idea_version" ]]; then
  echo 'The minimum CLI and IDEA versions must be declared in gradle.properties.' >&2
  exit 1
fi

if [[ ! "$min_idea_version" =~ ^([0-9]{2})([0-9])$ ]]; then
  echo "Unsupported IDEA baseline format: $min_idea_version" >&2
  exit 1
fi

readonly idea_version="20${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
readonly kotlin_badge="[![Kotlin ${min_cli_version}+]"
readonly idea_badge="[![IntelliJ IDEA ${idea_version}+]"
readonly kotlin_label="label=Kotlin%20${min_cli_version}%2B"
readonly idea_label="label=IntelliJ%20IDEA%20${idea_version}%2B"

for badge_file in "${badge_files[@]}"; do
  grep -Fq "$kotlin_badge" "$badge_file" || {
    echo "$badge_file does not contain the current Kotlin badge: $kotlin_badge" >&2
    exit 1
  }
  grep -Fq "$idea_badge" "$badge_file" || {
    echo "$badge_file does not contain the current IntelliJ IDEA badge: $idea_badge" >&2
    exit 1
  }
  grep -Fq "$kotlin_label" "$badge_file" || {
    echo "$badge_file has a stale Kotlin badge label: $kotlin_label" >&2
    exit 1
  }
  grep -Fq "$idea_label" "$badge_file" || {
    echo "$badge_file has a stale IntelliJ IDEA badge label: $idea_label" >&2
    exit 1
  }
done
