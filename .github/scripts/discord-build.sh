#!/usr/bin/env bash
# Posts a dev build to a Discord channel: an embed with the pushed commits, a link to the run and,
# when the build passed, the jar attached.
#
# Environment:
#   DISCORD_WEBHOOK_URL  the channel's webhook (Server Settings > Integrations > Webhooks); nothing
#                        is sent when it's empty
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

# The release workflow's own pushes (the version commit brought into dev) aren't new builds.
head_author=$(jq -r '.head_commit.author.name // ""' "$event")
if [[ "$head_author" == "github-actions[bot]" ]]; then
    echo "Push made by the release workflow; skipping the Discord notification."
    exit 0
fi

version=$(sed -n 's/^version=//p' gradle.properties)
paper=$(bash "$(dirname "$0")/paper-versions.sh")

if [[ "$status" == "success" ]]; then
    title="New build on $branch"
    color=3066993   # green
else
    title="Build failed on $branch"
    color=15158332  # red
fi

jar="${JAR:-}"
attach=""
if [[ "$status" == "success" && -n "$jar" && -f "$jar" ]]; then
    # Discord webhooks take files up to 10 MB
    if (( $(stat -c %s "$jar") < 10 * 1024 * 1024 )); then
        attach="ManhuntStar-$branch-$short.jar"
    fi
fi

download="[Run]($run_url)"
if [[ -n "${ARTIFACT_URL:-}" && "$status" == "success" ]]; then
    download="[Artifact](${ARTIFACT_URL}) (GitHub login)"
fi
if [[ -n "$attach" ]]; then
    download="\`$attach\` attached below · $download"
fi

jq -n \
    --slurpfile event "$event" \
    --arg title "$title" \
    --argjson color "$color" \
    --arg run_url "$run_url" \
    --arg repo "$repo" \
    --arg branch "$branch" \
    --arg short "$short" \
    --arg commit_url "$server/$repo/commit/$sha" \
    --arg version "$version" \
    --arg paper "$paper" \
    --arg download "$download" \
    --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '
    ($event[0].commits // []) as $commits
    | ($commits | map(select(.author.name != "github-actions[bot]"))) as $mine
    | ($mine | length) as $count
    | ($mine | .[-10:] | map(
        "[`\(.id[0:7])`](\(.url)) \(.message | split("\n")[0] | if length > 100 then .[0:97] + "..." else . end) - \(.author.name)"
      ) | join("\n")) as $lines
    | {
        embeds: [{
          title: $title,
          url: $run_url,
          color: $color,
          description: (
            (if $lines == "" then "No commit details." else $lines end)
            + (if $count > 10 then "\n...and \($count - 10) more" else "" end)
          ),
          fields: [
            { name: "Commit", value: "[`\($short)`](\($commit_url))", inline: true },
            { name: "Last release", value: "v\($version)", inline: true },
            { name: "Paper", value: $paper, inline: true },
            { name: "Download", value: $download, inline: false }
          ],
          footer: { text: $repo },
          timestamp: $timestamp
        }]
      }
    ' > "${RUNNER_TEMP:-/tmp}/discord.json"

args=(-sS --fail-with-body --retry 3 -F "payload_json=<${RUNNER_TEMP:-/tmp}/discord.json;type=application/json")
if [[ -n "$attach" ]]; then
    args+=(-F "files[0]=@$jar;filename=$attach;type=application/java-archive")
fi

if curl "${args[@]}" "$DISCORD_WEBHOOK_URL" > /dev/null; then
    echo "Discord notification sent."
else
    echo "::warning::Could not send the Discord notification."
fi
