#!/usr/bin/env bash

# Commits the changes of the tracked files against HEAD to BRANCH on GitHub, and prints the hash of the commit. The
# parent of the commit is HEAD. The script creates BRANCH at HEAD, or force-moves BRANCH to HEAD.
#
# The main ruleset accepts only signed commits, and `git commit` on a runner makes an unsigned commit. GitHub signs a
# commit of the GraphQL `createCommitOnBranch` mutation. The mutation sets no file mode, so each changed file must be a
# regular file without the executable bit. Needs `gh` with `GH_TOKEN`, `GITHUB_REPOSITORY`, and `jq`.

set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/lib/project-version.sh"

if [[ "$#" -ne 2 || -z "$1" || -z "$2" ]]; then
  echo 'Usage: scripts/push-signed-commit.sh BRANCH MESSAGE' >&2
  exit 1
fi
branch="$1"
message="$2"
: "${GITHUB_REPOSITORY:?Set GITHUB_REPOSITORY to OWNER/REPOSITORY.}"

head="$(git -C "$watchdog_repository_dir" rev-parse HEAD)"
contents="$(mktemp)"
additions="$(mktemp)"
deletions="$(mktemp)"
payload="$(mktemp)"
trap 'rm -f "$contents" "$additions" "$deletions" "$payload"' EXIT

# With -z, each change is the line `:OLD_MODE NEW_MODE OLD_HASH NEW_HASH STATUS` and the path, each ends with NUL.
while IFS= read -r -d '' change && IFS= read -r -d '' path; do
  read -r _ mode _ _ status <<< "$change"
  if [[ "$status" == D ]]; then
    jq --null-input --arg path "$path" '{path: $path}' >> "$deletions"
  elif [[ "$mode" == 100644 ]]; then
    base64 < "$watchdog_repository_dir/$path" | tr -d '\n' > "$contents"
    jq --null-input --arg path "$path" --rawfile contents "$contents" '{path: $path, contents: $contents}' >> "$additions"
  else
    echo "$path has the file mode $mode. A commit of the GitHub API can only have regular files without the executable bit." >&2
    exit 1
  fi
done < <(git -C "$watchdog_repository_dir" diff --raw --no-renames -z HEAD)

if [[ ! -s "$additions" && ! -s "$deletions" ]]; then
  echo "The tracked files have no changes against HEAD ($head)." >&2
  exit 1
fi

jq --null-input \
  --arg repository "$GITHUB_REPOSITORY" --arg branch "$branch" --arg message "$message" --arg head "$head" \
  --slurpfile additions "$additions" --slurpfile deletions "$deletions" '{
    query: "mutation($input: CreateCommitOnBranchInput!) { createCommitOnBranch(input: $input) { commit { oid } } }",
    variables: {input: {
      branch: {repositoryNameWithOwner: $repository, branchName: $branch},
      message: {headline: $message},
      expectedHeadOid: $head,
      fileChanges: {additions: $additions, deletions: $deletions}
    }}
  }' > "$payload"

if gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/$branch" --silent 2> /dev/null; then
  gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/heads/$branch" -f "sha=$head" -F force=true --silent
else
  gh api --method POST "repos/$GITHUB_REPOSITORY/git/refs" -f "ref=refs/heads/$branch" -f "sha=$head" --silent
fi
gh api graphql --input "$payload" --jq .data.createCommitOnBranch.commit.oid
