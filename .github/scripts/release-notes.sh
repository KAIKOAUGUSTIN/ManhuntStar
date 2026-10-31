#!/usr/bin/env bash
# Builds a version's release notes from the commits of the PR merged into main and puts the version
# at the top of CHANGELOG.md.
#
# Usage: release-notes.sh <version> <previous version> <commits.tsv> <output.md>
# commits.tsv: one line per commit, "sha<TAB>author login<TAB>first line of the message".
set -euo pipefail

version="$1"
previous="$2"
commits="$3"
output="$4"
repo="${GITHUB_REPOSITORY:-}"

changes=""
dependencies=""
declare -A seen=()
while IFS=$'\t' read -r sha author message; do
    [[ -z "$sha" || -z "$message" ]] && continue
    # the workflow's own version commits are left out
    [[ "$author" == "github-actions[bot]" ]] && continue
    [[ -n "${seen[$message]:-}" ]] && continue
    seen[$message]=1
    line="- ${message} (${sha:0:7})"
    if [[ "$author" == "dependabot[bot]" || "$message" == Bump\ * ]]; then
        dependencies+="${line}"$'\n'
    else
        changes+="${line}"$'\n'
    fi
done < "$commits"

paper=$(sed -n "s/^api-version: *'\{0,1\}\([^']*\)'\{0,1\}$/\1/p" src/main/resources/plugin.yml)

{
    if [[ -n "$changes" ]]; then
        printf '### Changes\n\n%s\n' "$changes"
    fi
    if [[ -n "$dependencies" ]]; then
        printf '### Dependencies\n\n%s\n' "$dependencies"
    fi
    if [[ -z "$changes" && -z "$dependencies" ]]; then
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
