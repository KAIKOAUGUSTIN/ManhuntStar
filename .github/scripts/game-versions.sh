#!/usr/bin/env bash
# Prints the supported Minecraft/Paper versions from gradle/libs.versions.toml, one per line, from the
# oldest to the newest: paper-api-min 26.2.x and paper-api 26.3.x give "26.2" and "26.3".
#
# Usage: game-versions.sh [catalog]
set -euo pipefail

catalog="${1:-gradle/libs.versions.toml}"
minor() {
    sed -n "s/^$1 *= *\"\([0-9]*\.[0-9]*\).*/\1/p" "$catalog"
}
newest=$(minor paper-api)
oldest=$(minor paper-api-min)
oldest="${oldest:-$newest}"

IFS=. read -r old_major old_minor <<< "$oldest"
IFS=. read -r new_major new_minor <<< "$newest"
if [[ "$old_major" == "$new_major" ]]; then
    for ((m = old_minor; m <= new_minor; m++)); do
        echo "$new_major.$m"
    done
else
    # different years: only the two ends are known
    echo "$oldest"
    echo "$newest"
fi
