#!/usr/bin/env bash
# Publishes a version on Modrinth: the jar, the release notes as the changelog, the Paper loader and
# every Minecraft release Modrinth lists for the supported versions (26.2, 26.2.1, 26.3...). A version
# that is already there is left alone, so a run can be repeated.
#
# Usage: publish-modrinth.sh <version> <release notes .md> <jar> [catalog]
# Environment:
#   MODRINTH_TOKEN    personal access token with the "Create versions" scope; nothing is published
#                     when it's empty
#   MODRINTH_PROJECT  project slug or ID (default: manhuntstar)
set -euo pipefail

if [[ -z "${MODRINTH_TOKEN:-}" ]]; then
    echo "MODRINTH_TOKEN is not set; skipping Modrinth."
    exit 0
fi

version="$1"
notes="$2"
jar="$3"
catalog="${4:-gradle/libs.versions.toml}"
project="${MODRINTH_PROJECT:-manhuntstar}"
api="${MODRINTH_API:-https://api.modrinth.com/v2}"
loaders='["paper"]'
tmp="${RUNNER_TEMP:-/tmp}"
response="$tmp/modrinth-response.json"

# Modrinth asks every client for a User-Agent that identifies it
request() {
    curl -sS --retry 3 -o "$response" -w '%{http_code}' \
        -H "Authorization: $MODRINTH_TOKEN" \
        -H "User-Agent: KAIKOAUGUSTIN/ManhuntStar (https://github.com/KAIKOAUGUSTIN/ManhuntStar)" \
        "$@" || echo 000
}

fail() {
    echo "::error::Modrinth: $1 $(head -c 500 "$response" 2> /dev/null)"
    exit 1
}

[[ -f "$jar" ]] || fail "the jar $jar doesn't exist."

code=$(request "$api/project/$project")
[[ "$code" == 200 ]] || fail "project '$project' not found or not accessible with the token (HTTP $code)."
project_id=$(jq -r '.id' "$response")

code=$(request "$api/project/$project_id/version")
if [[ "$code" == 200 ]] && jq -e --arg v "$version" 'any(.[]; .version_number == $v)' "$response" > /dev/null; then
    echo "Version $version is already on Modrinth; nothing to do."
    exit 0
fi

code=$(request "$api/tag/game_version")
[[ "$code" == 200 ]] || fail "could not read the game versions (HTTP $code)."
supported=$(bash "$(dirname "$0")/game-versions.sh" "$catalog" | jq -R . | jq -sc .)
game_versions=$(jq -c --argjson supported "$supported" '
    [.[] | select(.version_type == "release") | .version
         | select(. as $v | any($supported[]; . as $s | $v == $s or ($v | startswith($s + "."))))]
    | reverse' "$response")
[[ "$game_versions" != "[]" ]] || fail "none of the supported versions ($supported) exists on Modrinth yet."

jq -n \
    --arg version "$version" \
    --rawfile notes "$notes" \
    --arg project_id "$project_id" \
    --argjson game_versions "$game_versions" \
    --argjson loaders "$loaders" \
    '{
        name: "ManhuntStar \($version)",
        version_number: $version,
        changelog: $notes,
        dependencies: [],
        game_versions: $game_versions,
        version_type: "release",
        loaders: $loaders,
        featured: true,
        status: "listed",
        project_id: $project_id,
        file_parts: ["jar"],
        primary_file: "jar"
    }' > "$tmp/modrinth-version.json"

code=$(request -X POST \
    -F "data=<$tmp/modrinth-version.json;type=application/json" \
    -F "jar=@$jar;filename=ManhuntStar-$version.jar;type=application/java-archive" \
    "$api/version")
[[ "$code" == 200 ]] || fail "upload refused (HTTP $code)."

echo "Published ManhuntStar $version on Modrinth for $game_versions: https://modrinth.com/plugin/$project/version/$(jq -r '.id' "$response")"
