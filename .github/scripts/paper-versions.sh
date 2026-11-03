#!/usr/bin/env bash
# Prints the supported Paper versions from gradle/libs.versions.toml: "26.2-26.3", or "26.3" when the
# oldest and the newest are the same.
set -euo pipefail

catalog="${1:-gradle/libs.versions.toml}"
minor() {
    sed -n "s/^$1 *= *\"\([0-9]*\.[0-9]*\).*/\1/p" "$catalog"
}
oldest=$(minor paper-api-min)
newest=$(minor paper-api)
if [[ -z "$oldest" || "$oldest" == "$newest" ]]; then
    echo "$newest"
else
    echo "$oldest-$newest"
fi
