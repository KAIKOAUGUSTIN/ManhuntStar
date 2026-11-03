#!/usr/bin/env bash
# Publishes a version on Hangar (hangar.papermc.io): the jar for the Paper platform, the supported
# versions (26.2, 26.3...) and the release notes as the description, in the Release channel. A
# version that is already there is left alone, so a run can be repeated.
#
# Usage: publish-hangar.sh <version> <release notes .md> <jar> [catalog]
# Environment:
#   HANGAR_API_KEY  API key with the "create_version" permission; nothing is published when it's empty
#   HANGAR_PROJECT  project slug (default: ManhuntStar)
set -euo pipefail

if [[ -z "${HANGAR_API_KEY:-}" ]]; then
    echo "HANGAR_API_KEY is not set; skipping Hangar."
    exit 0
fi

version="$1"
notes="$2"
jar="$3"
catalog="${4:-gradle/libs.versions.toml}"
project="${HANGAR_PROJECT:-ManhuntStar}"
api="${HANGAR_API:-https://hangar.papermc.io/api/v1}"
tmp="${RUNNER_TEMP:-/tmp}"
response="$tmp/hangar-response.json"
auth=()

request() {
    curl -sS --retry 3 -o "$response" -w '%{http_code}' \
        -H "User-Agent: KAIKOAUGUSTIN/ManhuntStar (https://github.com/KAIKOAUGUSTIN/ManhuntStar)" \
        "${auth[@]}" "$@" || echo 000
}

fail() {
    echo "::error::Hangar: $1 $(head -c 500 "$response" 2> /dev/null)"
    exit 1
}

[[ -f "$jar" ]] || fail "the jar $jar doesn't exist."

# the API key is traded for a short-lived JWT, sent as "Authorization: HangarAuth <jwt>"
code=$(request -X POST --data-urlencode "apiKey=$HANGAR_API_KEY" -G "$api/authenticate")
[[ "$code" == 200 ]] || fail "the API key was refused (HTTP $code)."
auth=(-H "Authorization: HangarAuth $(jq -r '.token' "$response")")

code=$(request "$api/projects/$project/versions/$version")
if [[ "$code" == 200 ]]; then
    echo "Version $version is already on Hangar; nothing to do."
    exit 0
fi

code=$(request "$api/platforms/PAPER/versions")
[[ "$code" == 200 ]] || fail "could not read the Paper versions (HTTP $code)."
supported=$(bash "$(dirname "$0")/game-versions.sh" "$catalog" | jq -R . | jq -sc .)
paper_versions=$(jq -c --argjson supported "$supported" '
    [.[] | .version | select(. as $v | $supported | index($v))] | reverse' "$response")
[[ "$paper_versions" != "[]" ]] || fail "none of the supported versions ($supported) exists on Hangar yet."

jq -n \
    --arg version "$version" \
    --rawfile notes "$notes" \
    --argjson paper "$paper_versions" \
    '{
        version: $version,
        channel: "Release",
        description: $notes,
        platformDependencies: { PAPER: $paper },
        pluginDependencies: {},
        files: [{ platforms: ["PAPER"] }]
    }' > "$tmp/hangar-version.json"

code=$(request -X POST \
    -F "versionUpload=<$tmp/hangar-version.json;type=application/json" \
    -F "files=@$jar;filename=ManhuntStar-$version.jar;type=application/java-archive" \
    "$api/projects/$project/upload")
[[ "$code" == 200 || "$code" == 201 ]] || fail "upload refused (HTTP $code)."

echo "Published ManhuntStar $version on Hangar for Paper $paper_versions: $(jq -r '.url // empty' "$response")"
