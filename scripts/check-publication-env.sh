#!/usr/bin/env bash

set -euo pipefail

case "${1:-}" in
  gradle-plugin-portal)
    required=(GRADLE_PUBLISH_KEY GRADLE_PUBLISH_SECRET)
    ;;
  space)
    required=(KOTLIN_LIBRARY_SPACE_USERNAME KOTLIN_LIBRARY_SPACE_PASSWORD)
    ;;
  *)
    echo 'Usage: scripts/check-publication-env.sh gradle-plugin-portal|space' >&2
    exit 1
    ;;
esac

missing=0
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" || -z "${!variable//[[:space:]]/}" ]]; then
    echo "Missing publication environment variable: $variable. Set it before publication." >&2
    missing=1
  fi
done
exit "$missing"
