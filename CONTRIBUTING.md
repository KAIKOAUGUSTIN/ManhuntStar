# Contributing to ManhuntStar

Thanks for helping! This page has what you need to build ManhuntStar, use it from your own plugin and send changes or translations. How to install and play is in the [README](README.md).

## 🏗️ Building

Needs JDK 25. Run `./gradlew build`; the jars end up in `build/libs/`: the Paper plugin and the Fabric and NeoForge mods, one per Minecraft version (the versions are in `gradle.properties`).

Gradle checks every dependency it downloads against the checksums in `gradle/verification-metadata.xml`. After adding or updating a dependency (or a Gradle plugin), write them again with `./gradlew --write-verification-metadata sha256 build` and commit the file. Writing the file doesn't check anything: it records the checksums of whatever the repositories (or Gradle's local cache) served, so before committing, compare the new checksums with an independent source where there is one (for example the checksums the project publishes with its release).

## 🔌 API for other plugins

On Paper, `dev.manhuntstar.api.ManhuntStarAPI` lists the bots and finds the bot behind an entity. Events: `BotEvents.BotDamageByPlayerEvent`, `BotFallDamageEvent`, `BotDeathEvent` and `BotKilledByPlayerEvent`.

## 🌍 Translations

To add a language to ManhuntStar, open a pull request adding its file to `core/src/main/resources/lang/` (how to write one is in the README, under Translating). Keep the tags in `<...>`: `<bot>`, `<player>`, `<amount>` and the like are filled in by the plugin, and color tags such as `<red>` or `<bold>` follow the [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format.

## 🔀 Pull requests

Pull requests go to the `dev` branch; `main` only receives from `dev`, and every merge into `main` becomes a release.
