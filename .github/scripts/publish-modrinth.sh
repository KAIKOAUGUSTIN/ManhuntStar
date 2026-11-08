#!/usr/bin/env bash
# Publishes one jar of a version on Modrinth, with the release notes as the changelog. What the jar is
# comes from its name:
#   ManhuntStar-<version>.jar                       the Paper plugin: loader paper, version number
#                                                   <version>, every Minecraft release Modrinth lists for
#                                                   the supported Paper versions (26.2, 26.2.1, 26.3...)
#   ManhuntStar-<loader>-<version>+<minecraft>.jar  a mod (fabric or neoforge): version number
#                                                   <version>+<loader>-<minecraft>, the Minecraft releases
#                                                   of that version (26.2, 26.2.1...); Fabric's needs
#                                                   Fabric API
# A version number that is already there is left alone, so a run can be repeated.
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
tmp="${RUNNER_TEMP:-/tmp}"
response="$tmp/modrinth-response.json"
: > "$response"

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

file=$(basename "$jar")
dependencies='[]'
case "$file" in
    "ManhuntStar-$version.jar")
        loader=paper
        number="$version"
        name="ManhuntStar $version"
        supported=$(bash "$(dirname "$0")/game-versions.sh" "$catalog" | jq -R . | jq -sc .)
        ;;
    ManhuntStar-fabric-"$version"+*.jar | ManhuntStar-neoforge-"$version"+*.jar)
        rest="${file#ManhuntStar-}"
        loader="${rest%%-*}"
        minecraft="${rest#*+}"
        minecraft="${minecraft%.jar}"
        number="$version+$loader-$minecraft"
        if [[ "$loader" == fabric ]]; then
            name="ManhuntStar $version (Fabric $minecraft)"
            code=$(request "$api/project/fabric-api")
            [[ "$code" == 200 ]] || fail "could not find Fabric API on Modrinth (HTTP $code)."
            dependencies=$(jq -c '[{ project_id: .id, dependency_type: "required" }]' "$response")
        else
            name="ManhuntStar $version (NeoForge $minecraft)"
        fi
        supported=$(jq -nc --arg v "$minecraft" '[$v]')
        ;;
    *)
        fail "can't tell what $file is (expected ManhuntStar-$version.jar or ManhuntStar-<loader>-$version+<minecraft>.jar)."
        ;;
esac

code=$(request "$api/project/$project")
[[ "$code" == 200 ]] || fail "project '$project' not found or not accessible with the token (HTTP $code)."
project_id=$(jq -r '.id' "$response")

code=$(request "$api/project/$project_id/version")
if [[ "$code" == 200 ]] && jq -e --arg v "$number" 'any(.[]; .version_number == $v)' "$response" > /dev/null; then
    echo "Version $number is already on Modrinth; nothing to do."
    exit 0
fi

code=$(request "$api/tag/game_version")
[[ "$code" == 200 ]] || fail "could not read the game versions (HTTP $code)."
game_versions=$(jq -c --argjson supported "$supported" '
    [.[] | select(.version_type == "release") | .version
         | select(. as $v | any($supported[]; . as $s | $v == $s or ($v | startswith($s + "."))))]
    | reverse' "$response")
[[ "$game_versions" != "[]" ]] || fail "none of the supported versions ($supported) exists on Modrinth yet."

jq -n \
    --arg name "$name" \
    --arg number "$number" \
    --rawfile notes "$notes" \
    --arg project_id "$project_id" \
    --argjson game_versions "$game_versions" \
    --arg loader "$loader" \
    --argjson dependencies "$dependencies" \
    '{
        name: $name,
        version_number: $number,
        changelog: $notes,
        dependencies: $dependencies,
        game_versions: $game_versions,
        version_type: "release",
        loaders: [$loader],
        featured: true,
        status: "listed",
        project_id: $project_id,
        file_parts: ["jar"],
        primary_file: "jar"
    }' > "$tmp/modrinth-version.json"

code=$(request -X POST \
    -F "data=<$tmp/modrinth-version.json;type=application/json" \
    -F "jar=@$jar;filename=$file;type=application/java-archive" \
    "$api/version")
[[ "$code" == 200 ]] || fail "upload refused (HTTP $code)."

echo "Published $name on Modrinth for $game_versions: https://modrinth.com/project/$project/version/$(jq -r '.id' "$response")"
