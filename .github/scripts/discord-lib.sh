#!/usr/bin/env bash
# Shared by discord-build.sh and discord-release.sh: sends a Components V2 card to the webhook in
# DISCORD_WEBHOOK_URL, with a jar inside it when there is one.
#
# Usage, after sourcing: discord_send <card function> <jar or ""> <attachment name or "">
# The card function gets the attachment name (or "") as $1 and prints the message JSON; when the name
# is set, the card must show the file with { type: 13, file: { url: "attachment://<name>" } } and list
# it in "attachments".

discord_tmp="${RUNNER_TEMP:-/tmp}"

# with_components: webhooks only read "components" with it; wait: report errors in the response
discord_url() {
    local url="$DISCORD_WEBHOOK_URL"
    if [[ "$url" == *\?* ]]; then url+="&"; else url+="?"; fi
    echo "${url}with_components=true&wait=true"
}

# discord_post <payload file> [<file to upload> <its name>]; prints the HTTP status
discord_post() {
    local args=(-sS --retry 3 -o "$discord_tmp/discord-response.json" -w '%{http_code}')
    if [[ $# -ge 3 ]]; then
        args+=(-F "payload_json=<$1;type=application/json" -F "files[0]=@$2;filename=$3;type=application/java-archive")
    else
        args+=(-H "Content-Type: application/json" --data-binary "@$1")
    fi
    curl "${args[@]}" "$(discord_url)" || echo 000
}

discord_response() {
    head -c 500 "$discord_tmp/discord-response.json" 2> /dev/null
}

discord_send() {
    local card_fn="$1" jar="$2" name="$3" code
    if [[ -z "$jar" || ! -f "$jar" ]] || (( $(stat -c %s "$jar") >= 10 * 1024 * 1024 )); then
        name=""   # no jar, or over the webhooks' 10 MB limit
    fi

    # 1. The card with the jar inside it
    if [[ -n "$name" ]]; then
        "$card_fn" "$name" > "$discord_tmp/discord.json"
        code=$(discord_post "$discord_tmp/discord.json" "$jar" "$name")
        if [[ "$code" == 2* ]]; then
            echo "Discord notification sent (with the jar)."
            return 0
        fi
        echo "Discord refused the card with the jar (HTTP $code): $(discord_response)"
        echo "Sending the card and the jar as separate messages."
    fi

    # 2. The card alone, then the jar in a plain message under it
    "$card_fn" "" > "$discord_tmp/discord.json"
    code=$(discord_post "$discord_tmp/discord.json")
    if [[ "$code" != 2* ]]; then
        echo "::warning::Could not send the Discord notification (HTTP $code): $(discord_response)"
        return 0
    fi
    if [[ -n "$name" ]]; then
        echo '{"allowed_mentions":{"parse":[]}}' > "$discord_tmp/discord-file.json"
        code=$(discord_post "$discord_tmp/discord-file.json" "$jar" "$name")
        if [[ "$code" != 2* ]]; then
            echo "::warning::Discord notification sent, but not the jar (HTTP $code): $(discord_response)"
            return 0
        fi
    fi
    echo "Discord notification sent."
}
