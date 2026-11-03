#!/usr/bin/env bash
# Posts a dev build to a Discord channel as a Components V2 card: the pushed commits, the commit, the
# last release, the supported Paper versions, buttons for the run and the artifact, the jar (when the
# build passed) and a role mention at the bottom.
#
# Environment:
#   DISCORD_WEBHOOK_URL  the channel's webhook (Server Settings > Integrations > Webhooks); nothing
#                        is sent when it's empty
#   DISCORD_ROLE_ID      role mentioned at the bottom of the card (optional)
#   STATUS               the job status: success or failure
#   JAR                  the built jar (attached on success, when it exists)
#   ARTIFACT_URL         link to the run's artifact (optional)
#   GITHUB_*             set by GitHub Actions (repository, sha, ref name, run id, event file)
set -euo pipefail

if [[ -z "${DISCORD_WEBHOOK_URL:-}" ]]; then
    echo "DISCORD_WEBHOOK_URL is not set; skipping the Discord notification."
    exit 0
fi

event="${GITHUB_EVENT_PATH:?}"
server="${GITHUB_SERVER_URL:-https://github.com}"
repo="${GITHUB_REPOSITORY:?}"
sha="${GITHUB_SHA:?}"
short="${sha:0:7}"
branch="${GITHUB_REF_NAME:-dev}"
run_url="$server/$repo/actions/runs/${GITHUB_RUN_ID:-0}"
status="${STATUS:-success}"
role="${DISCORD_ROLE_ID:-}"
tmp="${RUNNER_TEMP:-/tmp}"

# The release workflow's own pushes (the version commit brought into dev) aren't new builds.
head_author=$(jq -r '.head_commit.author.name // ""' "$event")
if [[ "$head_author" == "github-actions[bot]" ]]; then
    echo "Push made by the release workflow; skipping the Discord notification."
    exit 0
fi

version=$(sed -n 's/^version=//p' gradle.properties)
paper=$(bash "$(dirname "$0")/paper-versions.sh")

if [[ "$status" == "success" ]]; then
    title="✅ New build on $branch"
    color=3066993   # green
    artifact="${ARTIFACT_URL:-}"
else
    title="❌ Build failed on $branch"
    color=15158332  # red
    artifact=""
fi

jar="${JAR:-}"
file=""
if [[ "$status" == "success" && -n "$jar" && -f "$jar" ]]; then
    # Discord webhooks take files up to 10 MB
    if (( $(stat -c %s "$jar") < 10 * 1024 * 1024 )); then
        file="ManhuntStar-$branch-$short.jar"
    fi
fi

# with_components: webhooks only read "components" with it; wait: report errors in the response
url="$DISCORD_WEBHOOK_URL"
if [[ "$url" == *\?* ]]; then url+="&"; else url+="?"; fi
url+="with_components=true&wait=true"

# The card; $1 = name of the attached jar shown in it, or empty for no file
card() {
    jq -n \
        --slurpfile event "$event" \
        --arg file "$1" \
        --arg title "$title" \
        --argjson color "$color" \
        --arg run_url "$run_url" \
        --arg artifact "$artifact" \
        --arg repo "$repo" \
        --arg short "$short" \
        --arg commit_url "$server/$repo/commit/$sha" \
        --arg version "$version" \
        --arg paper "$paper" \
        --arg role "$role" \
        --argjson now "$(date +%s)" \
        '
        ($event[0].commits // [] | map(select(.author.name != "github-actions[bot]"))) as $commits
        | ($commits | length) as $count
        | ($commits | .[-10:] | map(
            "- [`\(.id[0:7])`](\(.url)) \(.message | split("\n")[0] | if length > 100 then .[0:97] + "..." else . end) · \(.author.name)"
          ) | join("\n")) as $lines
        | (
            [
              { type: 10, content: "### \($title)" },
              { type: 10, content: (
                  (if $lines == "" then "No commit details." else $lines end)
                  + (if $count > 10 then "\n-# ...and \($count - 10) more" else "" end)
              ) },
              { type: 14, divider: true, spacing: 1 },
              { type: 10, content: "**Commit** [`\($short)`](\($commit_url))  ·  **Last release** v\($version)  ·  **Paper** \($paper)" }
            ]
            + (if $file != "" then [{ type: 13, file: { url: "attachment://\($file)" } }] else [] end)
            + [{ type: 1, components: (
                  [{ type: 2, style: 5, label: "View run", url: $run_url }]
                  + (if $artifact != "" then [{ type: 2, style: 5, label: "Download artifact", url: $artifact }] else [] end)
              ) }]
            + [
              { type: 14, divider: true, spacing: 1 },
              { type: 10, content: "-# \(if $role != "" then "<@&\($role)> · " else "" end)\($repo) · <t:\($now):f>" }
            ]
          ) as $inner
        | {
            flags: 32768,
            components: [{ type: 17, accent_color: $color, components: $inner }],
            allowed_mentions: (if $role != "" then { roles: [$role] } else { parse: [] } end)
          }
          + (if $file != "" then { attachments: [{ id: 0, filename: $file }] } else {} end)
        '
}

# post <payload file> [<file to upload> <its name>]; prints the HTTP status, the body goes to $tmp/discord-response.json
post() {
    local args=(-sS --retry 3 -o "$tmp/discord-response.json" -w '%{http_code}')
    if [[ $# -ge 3 ]]; then
        args+=(-F "payload_json=<$1;type=application/json" -F "files[0]=@$2;filename=$3;type=application/java-archive")
    else
        args+=(-H "Content-Type: application/json" --data-binary "@$1")
    fi
    curl "${args[@]}" "$url" || echo 000
}

ok() {
    [[ "$1" == 2* ]]
}

# 1. The card with the jar inside it
if [[ -n "$file" ]]; then
    card "$file" > "$tmp/discord.json"
    code=$(post "$tmp/discord.json" "$jar" "$file")
    if ok "$code"; then
        echo "Discord notification sent (with the jar)."
        exit 0
    fi
    echo "Discord refused the card with the jar (HTTP $code): $(head -c 500 "$tmp/discord-response.json" 2> /dev/null)"
    echo "Sending the card and the jar as separate messages."
fi

# 2. The card alone, then the jar in a plain message under it
card "" > "$tmp/discord.json"
code=$(post "$tmp/discord.json")
if ! ok "$code"; then
    echo "::warning::Could not send the Discord notification (HTTP $code): $(head -c 500 "$tmp/discord-response.json" 2> /dev/null)"
    exit 0
fi
if [[ -n "$file" ]]; then
    echo '{"allowed_mentions":{"parse":[]}}' > "$tmp/discord-file.json"
    code=$(post "$tmp/discord-file.json" "$jar" "$file")
    if ! ok "$code"; then
        echo "::warning::Discord notification sent, but not the jar (HTTP $code): $(head -c 500 "$tmp/discord-response.json" 2> /dev/null)"
        exit 0
    fi
fi
echo "Discord notification sent."
