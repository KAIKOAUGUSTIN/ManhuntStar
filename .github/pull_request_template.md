<!--
Pull requests go to dev, never to main (every merge into main makes a release).
See CONTRIBUTING.md for the build, the commit prefixes and what to change with a new option or text.
-->

## What it does

<!-- What changes for players or server owners. Screenshots or videos help with anything visible. -->

## Why

<!-- The problem it solves. Link the issue: "Closes #123". -->

## Type

- [ ] New feature
- [ ] Change in behavior
- [ ] Bug fix
- [ ] Translation
- [ ] Dependencies
- [ ] Internal (CI, build, refactor, docs)

## Tested on

<!-- Check what you tried in game. Leave unchecked what you didn't try. -->

- [ ] Paper 26.2
- [ ] Paper 26.3
- [ ] Fabric (server)
- [ ] Fabric (singleplayer or LAN)
- [ ] NeoForge (server)
- [ ] NeoForge (singleplayer or LAN)

How it was tested:

<!-- Commands, settings and steps, so the reviewer can try it too. -->

## Checklist

<!-- Delete the lines that don't apply to this pull request. -->

- [ ] The base branch is `dev`.
- [ ] `./gradlew build` passes.
- [ ] Each commit starts with `New:`, `Changed:`, `Fixed:`, `Updated:`, `Removed:` or `Internal:`, written for server owners.
- [ ] New texts are in every language file, and `version` is raised in all of them (with a step in `LanguageUpgrade`).
- [ ] New options are in `config.yml` with a comment and their command key, and `config-version` is raised (with a step in `ConfigUpgrade`).
- [ ] `gradle/verification-metadata.xml` has the checksums of any new or updated dependency.
- [ ] The README is updated if players or server owners see the change.
- [ ] Nothing new is shown only by a color or only by a sound ([ACCESSIBILITY.md](https://github.com/KAIKOAUGUSTIN/ManhuntStar/blob/dev/ACCESSIBILITY.md)).
