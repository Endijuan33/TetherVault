#!/usr/bin/env bash
# Regenerates CHANGELOG.md from the full git history.
#
# Commits are grouped by conventional-commit type (feat -> Features,
# fix -> Fixes, everything else -> Changes). Tags of the form v* become
# version sections; commits after the newest tag land under Unreleased.
# The file is fully regenerated on every run, so it never drifts.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT="CHANGELOG.md"

# Emits one markdown section for a commit range, or nothing when the
# range has no (non-changelog) commits.
section() {
    local title="$1" range="$2"
    local body
    body="$(git log "$range" --no-merges --invert-grep --grep='\[skip ci\]' \
        --pretty=format:'%s' 2>/dev/null || true)"
    [ -z "$body" ] && return 0

    local feats fixes changes
    feats="$(printf '%s\n' "$body" \
        | grep -E '^(feat|feature)(\([^)]*\))?!?:' \
        | sed -E 's/^(feat|feature)(\([^)]*\))?!?: *//' || true)"
    fixes="$(printf '%s\n' "$body" \
        | grep -E '^fix(\([^)]*\))?!?:' \
        | sed -E 's/^fix(\([^)]*\))?!?: *//' || true)"
    changes="$(printf '%s\n' "$body" \
        | grep -vE '^(feat|feature|fix|chore|docs|ci|style|refactor|test|build|perf)(\([^)]*\))?!?:' \
        | grep -vE '^docs: update changelog' || true)"

    echo "## $title"
    if [ -n "$feats" ]; then
        echo
        echo "### Features"
        printf '%s\n' "$feats" | sed 's/^/- /'
    fi
    if [ -n "$fixes" ]; then
        echo
        echo "### Fixes"
        printf '%s\n' "$fixes" | sed 's/^/- /'
    fi
    if [ -n "$changes" ]; then
        echo
        echo "### Changes"
        printf '%s\n' "$changes" | sed 's/^/- /'
    fi
    echo
}

mapfile -t tags < <(git tag --sort=-creatordate | grep -E '^v[0-9]+' || true)

tmp="$(mktemp)"
{
    echo "# Changelog"
    echo
    echo "_Generated automatically from commit messages. Do not edit by hand._"
    echo

    if [ "${#tags[@]}" -gt 0 ]; then
        section "Unreleased" "${tags[0]}..HEAD"
        for i in "${!tags[@]}"; do
            tag="${tags[$i]}"
            prev="${tags[$((i + 1))]:-}"
            range="${prev:+$prev..}$tag"
            date="$(git log -1 --pretty=format:%ad --date=short "$tag")"
            section "[$tag] - $date" "$range"
        done
    else
        section "Unreleased" "HEAD"
    fi
} > "$tmp"

mv "$tmp" "$OUT"
echo "CHANGELOG.md regenerated: $(grep -c '^## ' "$OUT") section(s)."
