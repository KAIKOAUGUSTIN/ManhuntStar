#!/usr/bin/env bash
# Publishes one mod jar of a version on CurseForge, with the release notes as the changelog: the
# Minecraft releases of its version (26.2, 26.2.1...), its loader, Server and Java 25; Fabric's needs
# Fabric API. The jar is ManhuntStar-<loader>-<version>+<minecraft>.jar. The Paper plugin isn't sent
# (CurseForge keeps Bukkit plugins in a section of their own).
#
# CurseForge's upload API can't list a project's files, so every run uploads again: the release
# workflow sends each version once, and the Publish workflow only when asked to.
#
# Usage: publish-curseforge.sh <version> <release notes .md> <jar>
# Environment:
#   CURSEFORGE_TOKEN       API token (curseforge.com > My profile > API tokens); nothing is published
#                          while it's empty
#   CURSEFORGE_PROJECT_ID  the project's ID, the number in the project page's About section
#                          (default: 1793171, https://www.curseforge.com/minecraft/mc-mods/manhuntstar)
set -euo pipefail

if [[ -z "${CURSEFORGE_TOKEN:-}" ]]; then
    echo "CURSEFORGE_TOKEN is not set; skipping CurseForge."
    exit 0
fi

version="$1"
notes="$2"
jar="$3"
project="${CURSEFORGE_PROJECT_ID:-1793171}"
api="${CURSEFORGE_API:-https://minecraft.curseforge.com/api}"
tmp="${RUNNER_TEMP:-/tmp}"
response="$tmp/curseforge-response.json"
: > "$response"

request() {
    curl -sS --retry 3 -o "$response" -w '%{http_code}' \
        -H "X-Api-Token: $CURSEFORGE_TOKEN" \
        -H "User-Agent: KAIKOAUGUSTIN/ManhuntStar (https://github.com/KAIKOAUGUSTIN/ManhuntStar)" \
        "$@" || echo 000
}

fail() {
    echo "::error::CurseForge: $1 $(head -c 500 "$response" 2> /dev/null)"
    exit 1
}

[[ -f "$jar" ]] || fail "the jar $jar doesn't exist."

file=$(basename "$jar")
case "$file" in
    ManhuntStar-fabric-"$version"+*.jar) loader=Fabric ;;
    ManhuntStar-neoforge-"$version"+*.jar) loader=NeoForge ;;
    *) fail "$file isn't a mod jar of $version (ManhuntStar-<loader>-$version+<minecraft>.jar)." ;;
esac
minecraft="${file#*+}"
minecraft="${minecraft%.jar}"

code=$(request "$api/game/version-types")
[[ "$code" == 200 ]] || fail "could not read the version types (HTTP $code); check the token."
cp "$response" "$tmp/curseforge-types.json"
code=$(request "$api/game/versions")
[[ "$code" == 200 ]] || fail "could not read the game versions (HTTP $code)."

# Minecraft releases come in types named minecraft-<major>-<minor>; loader, side and Java have types of their own
ids() {
    jq -c --slurpfile types "$tmp/curseforge-types.json" --arg minecraft "$minecraft" --arg loader "$loader" '
        ($types[0] | map({ key: (.id | tostring), value: .slug }) | from_entries) as $slug
        | map(. + { type: ($slug[(.gameVersionTypeID | tostring)] // "") })
        | {
            minecraft: [.[] | select((.type | startswith("minecraft-")) and (.name == $minecraft or (.name | startswith($minecraft + ".")))) | .id],
            loader: [.[] | select(.type == "modloader" and .name == $loader) | .id],
            extra: [.[] | select((.type == "environment" and .name == "Server") or (.type == "java" and .name == "Java 25")) | .id]
          }' "$response"
}
found=$(ids)
[[ $(jq '.minecraft | length' <<< "$found") -gt 0 ]] || fail "Minecraft $minecraft isn't on CurseForge yet."
[[ $(jq '.loader | length' <<< "$found") -gt 0 ]] || fail "the $loader loader wasn't found among CurseForge's versions."
game_versions=$(jq -c '.minecraft + .loader + .extra' <<< "$found")

relations=null
if [[ "$loader" == Fabric ]]; then
    relations='{"projects":[{"slug":"fabric-api","type":"requiredDependency"}]}'
fi

jq -n \
    --arg name "ManhuntStar $version ($loader $minecraft)" \
    --rawfile notes "$notes" \
    --argjson game_versions "$game_versions" \
    --argjson relations "$relations" \
    '{
        changelog: $notes,
        changelogType: "markdown",
        displayName: $name,
        gameVersions: $game_versions,
        releaseType: "release"
    } + (if $relations then { relations: $relations } else {} end)' > "$tmp/curseforge-file.json"

code=$(request -X POST \
    -F "metadata=<$tmp/curseforge-file.json;type=application/json" \
    -F "file=@$jar;filename=$file;type=application/java-archive" \
    "$api/projects/$project/upload-file")
[[ "$code" == 200 ]] || fail "upload refused (HTTP $code)."

echo "Published ManhuntStar $version ($loader $minecraft) on CurseForge: file $(jq -r '.id' "$response")"
