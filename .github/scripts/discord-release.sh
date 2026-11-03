#!/usr/bin/env bash
# Announces a release on Discord as a Components V2 card: the version, its release notes, the jar,
# a button to the release page and a role mention at the bottom.
#
# Usage: discord-release.sh <version> <release notes .md> <jar>
# Environment:
#   DISCORD_WEBHOOK_URL  the channel's webhook; nothing is sent when it's empty
#   DISCORD_ROLE_ID      role mentioned at the bottom of the card (optional)
#   GITHUB_*             set by GitHub Actions (repository)
set -euo pipefail

source "$(dirname "$0")/discord-lib.sh"

if [[ -z "${DISCORD_WEBHOOK_URL:-}" ]]; then
    echo "DISCORD_WEBHOOK_URL is not set; skipping the Discord announcement."
    exit 0
fi

version="$1"
notes="$2"
jar="$3"
server="${GITHUB_SERVER_URL:-https://github.com}"
repo="${GITHUB_REPOSITORY:?}"
role="${DISCORD_ROLE_ID:-}"

card() {
    jq -n \
        --arg file "$1" \
        --rawfile notes "$notes" \
        --arg version "$version" \
        --arg release_url "$server/$repo/releases/tag/v$version" \
        --arg repo "$repo" \
        --arg role "$role" \
        --argjson now "$(date +%s)" \
        '
        # a message holds up to 4000 characters of text in total
        ($notes | gsub("\\s+$"; "") | if length > 3000 then .[0:3000] + "\n..." else . end) as $body
        | (
            [
              { type: 10, content: "## 🚀 ManhuntStar \($version)" },
              { type: 10, content: $body }
            ]
            + (if $file != "" then [{ type: 13, file: { url: "attachment://\($file)" } }] else [] end)
            + [
              { type: 1, components: [{ type: 2, style: 5, label: "View release", url: $release_url }] },
              { type: 14, divider: true, spacing: 1 },
              { type: 10, content: "-# \(if $role != "" then "<@&\($role)> · " else "" end)\($repo) · <t:\($now):f>" }
            ]
          ) as $inner
        | {
            flags: 32768,
            components: [{ type: 17, accent_color: 15844367, components: $inner }],
            allowed_mentions: (if $role != "" then { roles: [$role] } else { parse: [] } end)
          }
          + (if $file != "" then { attachments: [{ id: 0, filename: $file }] } else {} end)
        '
}

discord_send card "$jar" "ManhuntStar-$version.jar"
