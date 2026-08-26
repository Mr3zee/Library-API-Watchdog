#!/usr/bin/env bash

set -euo pipefail

readonly properties_file='gradle.properties'
readonly version_catalog_file='gradle/libs.versions.toml'
readonly docs_prefix='docs/docs/'
readonly docs_version_template='{{libraryApiWatchdogVersion}}'
readonly kotlin_version_template='{{kotlinVersion}}'
readonly plugin_usage_pattern='kotlin\("library\.api-watchdog(-report-aggregation)?"\)[[:space:]]+version[[:space:]]+"[^"]+"'
readonly kotlin_plugin_usage_pattern='kotlin\("(android|js|jvm|kapt|multiplatform|native\.cocoapods|parcelize|plugin\.[^"]+|wasmJs|wasmWasi)"\)[[:space:]]+version[[:space:]]+"[^"]+"'
readonly kotlin_reference_usage_pattern='For Kotlin[[:space:]]+`?[^:`[:space:]]+`?:'
readonly version_value_pattern='version[[:space:]]+"([^"]+)"'
readonly kotlin_reference_value_pattern='For Kotlin[[:space:]]+`?([^:`[:space:]]+)`?:'

if [[ "$(grep -c '^version=' "$properties_file")" -ne 1 ]]; then
  echo "$properties_file must declare exactly one version property." >&2
  exit 1
fi

readonly project_version="$(awk -F= '$1 == "version" { print substr($0, length($1) + 2) }' "$properties_file")"
if [[ ! "$project_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ ]]; then
  echo "Unsupported project version: $project_version" >&2
  exit 1
fi
if [[ "$project_version" == *-SNAPSHOT ]]; then
  echo "The documented project version must be published, not a snapshot: $project_version" >&2
  exit 1
fi

if [[ "$(grep -Ec '^[[:space:]]*kotlin[[:space:]]*=[[:space:]]*"[^"]+"[[:space:]]*$' "$version_catalog_file")" -ne 1 ]]; then
  echo "$version_catalog_file must declare exactly one Kotlin version." >&2
  exit 1
fi

readonly kotlin_version="$(awk -F'"' '/^[[:space:]]*kotlin[[:space:]]*=/ { print $2 }' "$version_catalog_file")"
if [[ ! "$kotlin_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ ]]; then
  echo "Unsupported Kotlin version: $kotlin_version" >&2
  exit 1
fi

readonly usages="$(git grep -n -E "$plugin_usage_pattern" -- '*.md' '*.mdx' '*.gradle' '*.gradle.kts' || true)"
if [[ -z "$usages" ]]; then
  echo 'No Library API Watchdog plugin version usages found.' >&2
  exit 1
fi

failures=0
while IFS= read -r usage; do
  [[ -n "$usage" ]] || continue
  file="${usage%%:*}"
  location_and_source="${usage#*:}"
  line_number="${location_and_source%%:*}"
  source="${location_and_source#*:}"

  if [[ ! "$source" =~ $version_value_pattern ]]; then
    echo "Could not read the version at $file:$line_number" >&2
    failures=1
    continue
  fi
  actual_version="${BASH_REMATCH[1]}"

  expected_version="$project_version"
  if [[ "$file" == "$docs_prefix"* ]]; then
    expected_version="$docs_version_template"
  fi

  if [[ "$actual_version" != "$expected_version" ]]; then
    echo "$file:$line_number uses '$actual_version'. Expected '$expected_version'." >&2
    failures=1
  fi
done <<< "$usages"

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi

echo "All Library API Watchdog version usages align with $project_version."

readonly kotlin_plugin_usages="$(git grep -n -E "$kotlin_plugin_usage_pattern" -- '*.md' '*.mdx' '*.gradle' '*.gradle.kts' || true)"
if [[ -z "$kotlin_plugin_usages" ]]; then
  echo 'No base Kotlin plugin version usages found.' >&2
  exit 1
fi

failures=0
while IFS= read -r usage; do
  [[ -n "$usage" ]] || continue
  file="${usage%%:*}"
  location_and_source="${usage#*:}"
  line_number="${location_and_source%%:*}"
  source="${location_and_source#*:}"

  if [[ ! "$source" =~ $version_value_pattern ]]; then
    echo "Could not read the Kotlin version at $file:$line_number" >&2
    failures=1
    continue
  fi
  actual_version="${BASH_REMATCH[1]}"

  expected_version="$kotlin_version"
  if [[ "$file" == "$docs_prefix"* ]]; then
    expected_version="$kotlin_version_template"
  fi

  if [[ "$actual_version" != "$expected_version" ]]; then
    echo "$file:$line_number uses Kotlin '$actual_version'. Expected '$expected_version'." >&2
    failures=1
  fi
done <<< "$kotlin_plugin_usages"

readonly kotlin_reference_usages="$(git grep -n -E "$kotlin_reference_usage_pattern" -- '*.md' '*.mdx' || true)"
if [[ -z "$kotlin_reference_usages" ]]; then
  echo 'No base Kotlin version references found.' >&2
  exit 1
fi

while IFS= read -r usage; do
  [[ -n "$usage" ]] || continue
  file="${usage%%:*}"
  location_and_source="${usage#*:}"
  line_number="${location_and_source%%:*}"
  source="${location_and_source#*:}"

  if [[ ! "$source" =~ $kotlin_reference_value_pattern ]]; then
    echo "Could not read the Kotlin version reference at $file:$line_number" >&2
    failures=1
    continue
  fi
  actual_version="${BASH_REMATCH[1]}"

  expected_version="$kotlin_version"
  if [[ "$file" == "$docs_prefix"* ]]; then
    expected_version="$kotlin_version_template"
  fi

  if [[ "$actual_version" != "$expected_version" ]]; then
    echo "$file:$line_number references Kotlin '$actual_version'. Expected '$expected_version'." >&2
    failures=1
  fi
done <<< "$kotlin_reference_usages"

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi

echo "All base Kotlin version usages align with $kotlin_version."
