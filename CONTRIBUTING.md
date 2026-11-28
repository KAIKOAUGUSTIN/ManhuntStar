# Contributing to ManhuntStar

Thanks for wanting to help! Bug reports, ideas, translations and code are all welcome.

## Before you start

- **Bugs and ideas**: open an issue with the **Bug report** or **Feature request** form. Search the
  existing issues first.
- **Questions**: ask on the [Discord server](https://discord.gg/X6P447F7vx).
- **Security problems**: never in a public issue. See [SECURITY.md](SECURITY.md).
- **Bigger changes** (a new feature, a new option, a change in how the bots play): open an issue or ask
  on Discord first, so the idea is agreed on before you spend time on it.

## Branches

- `dev` is where the work happens. `main` only receives from `dev`, and every merge into `main` makes a
  release (new version, changelog, Modrinth, Hangar, CurseForge and Discord). **Pull requests go to
  `dev`**, never to `main`.
- Start from an up-to-date `dev`, with one branch per feature or fix, named in kebab-case:
  `fix-<description>` for a fix (for example `fix-menu-tower-reach-shield`) and `<description>` for a
  feature (for example `match-end`).
- From a fork it's the same: a branch on your fork, and the pull request into `dev` here.

## Building and testing

You need JDK 25. Run:

```bash
./gradlew build
```

It compiles everything, runs the tests and puts the jars in `build/libs/`: the Paper plugin
(`ManhuntStar-<version>.jar`) and the Fabric and NeoForge mods, one per Minecraft version
(`ManhuntStar-fabric-<version>+26.3.jar` and the like).

Then try the change in game, on every platform it touches:

- **Paper**: the plugin in a Paper 26.2 or 26.3 server's `plugins/` folder.
- **Fabric or NeoForge**: the mod in a server's or your own client's `mods/` folder (singleplayer and
  LAN). Fabric also needs [Fabric API](https://modrinth.com/mod/fabric-api).

Local builds have no FastStats token, so they send no statistics and no error reports. There's nothing
to set up for that.

## Where the code goes

| Module | What's in it |
|---|---|
| `core/` | The bots' AI, pathfinding, the hunt rules, commands, the menu, `config.yml` and the languages. Paper and Minecraft aren't on its classpath: it talks to the game through the interfaces in `dev.manhuntstar.platform`, and platform code there doesn't compile. |
| `paper/` | The Paper plugin (one jar for every supported version) and the API for other plugins (`dev.manhuntstar.api`). |
| `mod-common/` | The mods' code that only uses Minecraft itself, the same for Fabric and NeoForge (mixins included). |
| `fabric/` · `neoforge/` | Each loader's entry point. |

The mods are built once per Minecraft version listed in `minecraftVersions` (`gradle.properties`).
What changes between Minecraft versions goes in `<module>/versions/<version>/src/`, for example
`mod-common/versions/26.3/src/main/java/dev/manhuntstar/mod/MinecraftCompat.java`.

Tests that every platform runs against its own implementation are in `core/src/testFixtures/`.

## Code

- Follow the style of the file you're editing.
- Comments, Javadoc, log messages and test failure messages are in English.
- The build warns about deprecated and removed APIs (`-Xlint:deprecation`, `-Xlint:removal`): don't
  add new warnings.
- Add tests for what can be tested without the game: `core` has them for pathfinding, combat timing,
  the hunt rules, `config.yml` and the languages.
- The texts players see (messages, titles, bars, the menu) go in the language files, not in the code.
- Follow [ACCESSIBILITY.md](ACCESSIBILITY.md): nothing the player needs to know is shown only by a
  color or only by a sound.

## New options and texts

`config.yml` and the language files have a version, so servers that update ManhuntStar get the new
options and texts and keep their own values.

A new option:

1. Add it to `core/src/main/java/dev/manhuntstar/config/Settings.java`, with its path in `config.yml`,
   `null` for the old Portuguese name (only options from 1.5 and older have one), its command key, its
   category and its default value.
2. Add it to `core/src/main/resources/config.yml`, with a comment and the command key in square
   brackets, like the others.
3. Give it `options.<command key>.name` and `options.<command key>.description` in every language file.
4. Raise `config-version` in `config.yml` and add a step to `ConfigUpgrade.VERSIONS`
   (`core/src/main/java/dev/manhuntstar/config/upgrade/`): `FileVersions.nothing()` when there are only
   new options, or a step that moves the owner's value when an option is renamed or changes meaning.

New or changed texts:

1. Add them to `en.yml` and to every other file in `core/src/main/resources/lang/`, with the same keys
   and the same placeholders (`<bot>`, `<player>`, `<amount>`...). `LanguageFilesTest` fails otherwise.
   If you can't translate a text, ask in the pull request.
2. Raise `version` in every language file (they all have the same one) and add a step to
   `LanguageUpgrade.VERSIONS`. A text whose meaning changed so much that the owner's edited copy no
   longer fits goes in `LanguageUpgrade.RESET`.

## Translations

- **Fixing a translation**: edit its file in `core/src/main/resources/lang/`.
- **A new language**: copy `en.yml` to a file named after the language (for example `es.yml`) and
  translate the texts between quotes. Keep the tags in `<...>`: the placeholders are filled in by the
  plugin, and the colors follow the [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
  format. Add the language to `Messages.BUNDLED_LANGUAGES`
  (`core/src/main/java/dev/manhuntstar/config/Messages.java`) and to the list in the README.

## Dependencies

Versions live in `gradle/libs.versions.toml`, and Gradle checks every download against the checksums
in `gradle/verification-metadata.xml`. After adding or updating a dependency or a Gradle plugin, write
them again and commit the file:

```bash
./gradlew --write-verification-metadata sha256 build
```

Writing the file doesn't check anything: it records whatever the repositories served. Before
committing, compare the new checksums with an independent source where there is one, such as the
checksums the project publishes with its release.

## Commit messages

The release notes and `CHANGELOG.md` are built from the first line of each commit, by its prefix:

| Prefix | Goes to | For |
|---|---|---|
| `New:` | New | Something that wasn't possible before. |
| `Changed:` | Changed | A change in behavior. Commits without a prefix go here too. |
| `Fixed:` | Fixed | A bug fix. |
| `Updated:` | Updated | A dependency update (Dependabot's commits go here on their own). |
| `Removed:` | Removed | Something taken out. |
| `Internal:` | Left out | CI, the build setup, the release notes, review follow-ups and refactors that change nothing for players or server owners. |

`Add:`, `Change:`, `Fix:`, `Update:` and `Remove:` work too. Write the line in English, for server
owners: what changes for them, not how the code does it. For example:

```text
Fixed: a bot's shield blocks only once raised for a moment, and an axe takes it down, as a player's
```

Fixes to something that isn't released yet use `Internal:`, so the release lists the feature once.

Don't change `version` in `gradle.properties` or edit `CHANGELOG.md`: the Release workflow writes both.

## Pull requests

- Open it into `dev` and fill in the template.
- One feature or fix per pull request.
- The **Build** workflow has to pass: it compiles, runs the tests and also compiles against the newest
  Paper API. SonarQube Cloud analyzes it too (pull requests from forks skip that), and CodeRabbit
  reviews it: answer or fix their comments.
- Update the README when players or server owners see the change (commands, options, features).
- The `minor`, `major` and `skip-release` labels are for the pull requests from `dev` into `main`; the
  maintainer sets them.

## License

ManhuntStar is under the [GNU General Public License v3](LICENSE.md). By contributing, you agree that
your contribution is released under the same license.
