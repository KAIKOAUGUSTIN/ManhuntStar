#!/usr/bin/env bash
# Writes the dependency snapshot sent to GitHub (dependency graph and Dependabot alerts) with only what
# goes inside the jars, plus the Gradle version:
#   the Paper plugin   runtimeClasspath of :paper (FastStats; core is our own code)
#   the Fabric mods    includeInternal of :fabric:<minecraft> (the jar-in-jar libraries)
#   the NeoForge mods  jarJar of :neoforge:<minecraft> (the same)
# Everything else the build resolves (Minecraft and Paper and their libraries, which come with the game
# or the server, and the build tools) stays out, so Dependabot doesn't warn about libraries ManhuntStar
# doesn't ship. Each project needs its own configuration, so Gradle runs twice (the filters can't pair
# a project with a configuration) and the two reports become one snapshot, for one manifest
# (settings.gradle.kts), as GitHub shows only one snapshot per manifest.
#
# Usage: dependency-graph.sh <snapshot .json>
# Environment (set by GitHub Actions; local defaults otherwise): GITHUB_SHA, GITHUB_REF, GITHUB_RUN_ID
set -euo pipefail

out="${1:?usage: dependency-graph.sh <snapshot .json>}"
plugin=1.5.1 # org.gradle:github-dependency-graph-gradle-plugin, what gradle/actions uses to do the same
correlator=manhuntstar-jars # the same every time, so each snapshot replaces the last one

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/init.gradle" << EOF
initscript {
    repositories { gradlePluginPortal() }
    dependencies { classpath("org.gradle:github-dependency-graph-gradle-plugin:$plugin") }
}
apply plugin: org.gradle.github.GitHubDependencyGraphPlugin
EOF

# report <name> <projects> <configurations>: the dependencies those configurations of those projects
# resolve (both regular expressions) into $tmp/<name>/<name>.json
report() {
    env GITHUB_DEPENDENCY_GRAPH_ENABLED=true \
        GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR="$1" \
        GITHUB_DEPENDENCY_GRAPH_JOB_ID="${GITHUB_RUN_ID:-0}" \
        GITHUB_DEPENDENCY_GRAPH_REF="${GITHUB_REF:-refs/heads/$(git branch --show-current)}" \
        GITHUB_DEPENDENCY_GRAPH_SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}" \
        GITHUB_DEPENDENCY_GRAPH_WORKSPACE="$PWD" \
        DEPENDENCY_GRAPH_REPORT_DIR="$tmp/$1" \
        DEPENDENCY_GRAPH_INCLUDE_PROJECTS="$2" \
        DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS="$3" \
        ./gradlew --quiet --no-configuration-cache --init-script "$tmp/init.gradle" \
        :ForceDependencyResolutionPlugin_resolveAllDependencies
    [[ -s "$tmp/$1/$1.json" ]] || { echo "::error::No dependency report for $1."; exit 1; }
}

report paper '^:paper$' '^runtimeClasspath$'
report mods '^:(fabric|neoforge):[^:]+$' '^(includeInternal|jarJar)$'

jq -s --arg correlator "$correlator" '
    (map(.manifests[].resolved) | add) as $resolved
    | .[0]
    | .job.correlator = $correlator
    | .manifests = { ($correlator): {
        name: $correlator,
        file: { source_location: "settings.gradle.kts" },
        resolved: $resolved
    } }' "$tmp/paper/paper.json" "$tmp/mods/mods.json" > "$out"

echo "Dependencies in the jars:"
jq -r '.manifests[].resolved[].package_url | sub("\\?.*"; "")' "$out" | sort
