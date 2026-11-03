#!/usr/bin/env bash
# Builds a version's release notes from the commits of the PR merged into main and puts the version
# at the top of CHANGELOG.md. The same notes go to the GitHub release, Discord, Modrinth and Hangar.
#
# Each commit goes into a section by the prefix of its message, which is left out of the notes:
#   "New: ..."      -> New
#   "Changed: ..."  -> Changed (also commits with no prefix)
#   "Updated: ..."  -> Updated (also Dependabot's "Bump ..." commits)
#   "Removed: ..."  -> Removed
# Empty sections are left out. .github/release-notes-overrides.txt can give a commit another text
# (and so another section), for commits whose message can't be changed anymore.
#
# Usage: release-notes.sh <version> <previous version> <commits.tsv> <output.md>
# commits.tsv: one line per commit, "sha<TAB>author login<TAB>first line of the message".
set -euo pipefail

version="$1"
previous="$2"
commits="$3"
output="$4"
repo="${GITHUB_REPOSITORY:-}"

declare -A sections=([New]="" [Changed]="" [Updated]="" [Removed]="")
declare -A seen=()

# "<sha> <text>" lines from the overrides file
overrides_file="$(dirname "$0")/../release-notes-overrides.txt"
declare -A overrides=()
if [[ -f "$overrides_file" ]]; then
    while read -r key text; do
        [[ -z "$key" || "$key" == \#* || -z "$text" ]] && continue
        overrides[$key]="$text"
    done < "$overrides_file"
fi
override_for() {
    local key
    for key in "${!overrides[@]}"; do
        if [[ "$1" == "$key"* ]]; then
            echo "${overrides[$key]}"
            return
        fi
    done
}
prefix='^(new|changed|updated|removed):[[:space:]]*(.+)$'
while IFS=$'\t' read -r sha author message; do
    [[ -z "$sha" || -z "$message" ]] && continue
    # the workflow's own version commits are left out
    [[ "$author" == "github-actions[bot]" ]] && continue
    [[ -n "${seen[$message]:-}" ]] && continue
    seen[$message]=1
    replacement=$(override_for "$sha")
    [[ -n "$replacement" ]] && message="$replacement"
    type=Changed
    text="$message"
    shopt -s nocasematch
    if [[ "$message" =~ $prefix ]]; then
        type="${BASH_REMATCH[1],,}"
        type="${type^}"
        text="${BASH_REMATCH[2]}"
    elif [[ "$author" == "dependabot[bot]" || "$message" == Bump\ * ]]; then
        type=Updated
    fi
    shopt -u nocasematch
    # "update check..." becomes "Update check...", but names such as "bStats" or "iOS" keep their case
    first="${text%% *}"
    if [[ "$first" == "${first,,}" ]]; then
        text="${text^}"
    fi
    sections[$type]+="- ${text} (${sha:0:7})"$'\n'
done < "$commits"

paper=$(bash "$(dirname "$0")/paper-versions.sh")

{
    listed=0
    for type in New Changed Updated Removed; do
        if [[ -n "${sections[$type]}" ]]; then
            printf '### %s\n\n%s\n' "$type" "${sections[$type]}"
            listed=1
        fi
    done
    if [[ "$listed" == 0 ]]; then
        printf 'No changes listed.\n\n'
    fi
    printf '**Paper:** %s · **Java:** 25\n' "$paper"
    if [[ -n "$repo" ]] && git rev-parse -q --verify "refs/tags/v${previous}" > /dev/null; then
        printf '\n**Full changelog since %s:** https://github.com/%s/compare/v%s...v%s\n' "$previous" "$repo" "$previous" "$version"
    fi
} > "$output"

# CHANGELOG.md: the new version goes before the first "## " (the latest one stays on top)
section=$(printf '## %s - %s\n\n%s\n' "$version" "$(date -u +%Y-%m-%d)" "$(cat "$output")")
if [[ ! -f CHANGELOG.md ]]; then
    printf '# Changelog\n\nGenerated automatically on every merge into main.\n\n' > CHANGELOG.md
fi
SECTION="$section" awk '
    !done && /^## / { print ENVIRON["SECTION"]; print ""; done = 1 }
    { print }
    END { if (!done) { print ENVIRON["SECTION"] } }
' CHANGELOG.md > CHANGELOG.md.new
mv CHANGELOG.md.new CHANGELOG.md
