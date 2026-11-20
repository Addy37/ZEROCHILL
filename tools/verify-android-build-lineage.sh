#!/usr/bin/env bash
set -euo pipefail

source_sha="$(git rev-parse HEAD)"
expected_sha="${ZEROCHILL_SOURCE_SHA:-$source_sha}"
if [[ "$source_sha" != "$expected_sha" ]]; then
    echo "The checkout ($source_sha) differs from the requested build source ($expected_sha)." >&2
    exit 1
fi

# Reject an explicitly targeted legacy base even if its source contains main.
if [[ "${GITHUB_EVENT_NAME:-}" == pull_request && "${GITHUB_BASE_REF:-}" != main ]]; then
    echo "Test APK pull requests must target main." >&2
    exit 1
fi

git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
base_sha="$(git rev-parse refs/remotes/origin/main)"
if ! git merge-base --is-ancestor "$base_sha" "$source_sha"; then
    echo "Build source $source_sha does not include current main $base_sha. Update the branch from main and rebuild." >&2
    exit 1
fi

branch="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-$(git branch --show-current)}}"
printf 'Branch %s\nBase (main) %s\nBuild %s\n' "$branch" "$base_sha" "$source_sha"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '### ZEROCHILL APK lineage\n\n- Branch `%s`\n- Base (main) `%s`\n- Build `%s`\n' \
        "$branch" "$base_sha" "$source_sha" >> "$GITHUB_STEP_SUMMARY"
fi
if [[ -n "${1:-}" ]]; then
    printf 'BRANCH=%s\nBASE_SHA=%s\nBUILD_SHA=%s\n' "$branch" "$base_sha" "$source_sha" > "$1"
fi
