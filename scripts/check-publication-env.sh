#!/usr/bin/env bash

set -euo pipefail

case "${1:-}" in
  maven-central)
    required=(
      ORG_GRADLE_PROJECT_mavenCentralUsername
      ORG_GRADLE_PROJECT_mavenCentralPassword
      ORG_GRADLE_PROJECT_signingInMemoryKey
      ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
    )
    ;;
  gradle-plugin-portal)
    required=(GRADLE_PUBLISH_KEY GRADLE_PUBLISH_SECRET)
    ;;
  *)
    echo 'Usage: scripts/check-publication-env.sh maven-central|gradle-plugin-portal' >&2
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
