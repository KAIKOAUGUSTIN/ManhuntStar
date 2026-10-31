# Manhunt*

**ManHunt** hunter bots for **Paper 26.3** servers.

The bots are **Mannequin** entities (the game's "player-skinned dummy") driven by the plugin to act like
players: they run, jump, swim, dig, build towers and bridges, fight with weapon cooldowns, crits and shields,
eat, level up through phases, use ender pearls, crossbows, tridents, lava buckets, horses (with spear charges),
boats and elytra, and follow the player to the Nether and the End.

## Installation

1. Download `ManhuntStar-<version>.jar` from the [latest release](https://github.com/KAIKOAUGUSTIN/ManhuntStar/releases/latest)
   and put it in the `plugins/` folder of a **Paper 26.3** server. For Paper 26.2, use version 1.5.3.
2. Start the server. It creates `plugins/ManhuntStar/config.yml`, the `lang/` folder and the `skins/` folder.
3. Join as an operator and use `/manhunt create Hunter 3` near you, or open `/manhunt menu`.

Requirements: Java 25 (the same as Paper 26.x). No other plugins needed.

## Languages

Every message and the whole menu come from `plugins/ManhuntStar/lang/<language>.yml`. The plugin ships
with **English** (`en`, the default), **Brazilian Portuguese** (`pt-br`), **French** (`fr`), **Russian** (`ru`)
and **Simplified Chinese** (`zh-cn`).

- Pick the language with `general.language` in `config.yml`, with `/manhunt settings language pt-br`, or in
  `/manhunt menu` (Settings → General). It switches right away.
- The files use the [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format and can be edited.
  Tags such as `<bot>`, `<player>` and `<amount>` are filled in by the plugin. Any text missing from a file
  falls back to English.
- To add a language, copy `en.yml` to `lang/<name>.yml`, translate it and set `language: <name>`.
- When the default texts change in a new version, the old file is moved to `lang/old/` and replaced.

Commands and `config.yml` keys are always in English, whatever the language.

## Commands

Everything lives under `/manhunt` and needs the `manhuntstar.admin` permission (operators have it by default).
The verbs are the same everywhere: `create`/`remove` for bots and groups, `add`/`remove` for lists and `clear`
to erase a value. Positions accept `~ ~ ~`; with no position, your own position is used.

**Match**

| Command | What it does |
|---|---|
| `/manhunt start` | Starts the hunt right away (releases every ring and skips the countdown). |
| `/manhunt pause` · `/manhunt resume` | Pause: freezes each bot where it is (even in the air, mounted or in a boat), invincible and not thinking, and stops the ring countdown, the late-joiner protection, the phase timer and respawning. The pause survives a server restart. |
| `/manhunt target <player>` · `/manhunt target clear` | Makes every bot chase one player / back to normal. |
| `/manhunt ring` · `/manhunt ring point [x y z \| clear]` | Shows the ring; sets a fixed point for the rings (manual center) or clears it (back to automatic). |
| `/manhunt players` · `players add\|remove <player>` · `players mode <off\|allowlist\|blocklist>` | List of players who can (or can't) be hunted and trigger the ring. |

**Bots**

| Command | What it does |
|---|---|
| `/manhunt create <name> [amount] [difficulty] [skin <player>] [at <x y z>] [phase] [aggression]` | Creates bots where you are (or at the ring point, with a manual center), or at `at x y z`. In MANHUNT mode they spawn in a ring. |
| `/manhunt remove <bot>` · `/manhunt remove all confirm` | Removes the bots with that name / deletes them all, including the save. |
| `/manhunt list` · `/manhunt info <bot>` | Lists the bots (health, phase, target, location, what the AI is doing). |
| `/manhunt tp <bot\|all> [player]` | Brings bots to a player (or to you). |
| `/manhunt skin <bot> <player>` | Changes the skin. |
| `/manhunt spawnpoint <bot> [x y z \| clear]` | A bot's respawn point. |
| `/manhunt equip item <item>` · `/manhunt equip armor <tier>` | Gives an item / armor to every bot. |
| `/manhunt test <tnt-craft\|bow-burst\|firework-crossbow>` | Tests a special ability against you. |

**Groups** (the group name comes before the action)

| Command | What it does |
|---|---|
| `/manhunt group <group> role <hunter\|escape>` | The group's role (hunter or escaper). |
| `/manhunt group <group> enemy add\|remove <other>` | Enemy groups fight each other. |
| `/manhunt group <group> spawnpoint [x y z \| clear]` | The group's respawn point. |
| `/manhunt group <group> phases <true\|false>` · `chaseplayers <true\|false>` | Phases and chasing players. |
| `/manhunt group <group> remove` | Removes the group and its bots. |

**Settings and menu**

| Command | What it does |
|---|---|
| `/manhunt settings [option] [value]` | Shows or changes options (`language`, `pathmode`, `pathnodes`, `allowtowering`, `targetmode`, `glowhunters true red`, `countdown 20`, `ringradius auto`...). |
| `/manhunt reload` | Reloads `config.yml` and the language files. |
| `/manhunt menu` | Chest menu in four sections: Match, Hunt rules, Bots and Settings. |

Difficulties: `easy`, `medium`, `hard`, `terminator`.

Names with `%`, accents or spaces go in quotes in commands, for example `/manhunt create "Hunter%" 5` and
`/manhunt group "Hunter%" role escape`. Tab completion already adds the quotes.

The old option names (`starttime`, `glowing`, `nodes`, `min_sec_range_nether_entering`, `botuseelytra`...)
still work in `/manhunt settings`, but don't show up in Tab completion.

## What you can configure

Everything is commented in `config.yml` and can be changed in game. Highlights:

- **Ring and countdown before the hunt** (`manhunt.countdown-seconds`, default 20): the ring has no owner. The
  bots wait in it until a player who was inside leaves (the first one counts); then the head-start countdown
  begins, with a bar at the top and 3-2-1 titles, and only then do they start hunting. A ring created far from
  everyone waits for someone to walk in and out (or for `/manhunt start`). Each ring has its own countdown, and
  the ring is saved if the server restarts. With `manhunt.countdown-on-ring-leave: false` the countdown starts as
  soon as the bots are created; `0` = the bots are released as soon as someone leaves the ring.
- **Ring radius and center**: `manhunt.ring-radius` (0 = automatic, grows with the number of bots) and
  `manhunt.ring-center` (`auto` = where the ring is created; `manual` = at the point set with `/manhunt ring point`).
- **Late joiners** (`manhunt.late-join`): `protected` (default) isn't a target for the ring's countdown time, at
  least 5 s; `hunted` becomes a target right away; `out` isn't hunted until the next hunt. Someone who was
  already in the hunt, drops and comes back is still hunted.
- **Player list** (`manhunt.player-list-mode` and `/manhunt players`): `allowlist` = only the listed players are
  hunted and trigger the ring; `blocklist` = the listed players aren't hunted and don't trigger the ring.
  `/manhunt target` overrides it.
- **Glow** on the hunters (`appearance.glow-hunters`, with a color) and, separately, on the escapers. Off by
  default; turn it on and change the color in `/manhunt menu` (Settings) or with `/manhunt settings glowhunters true red`.
- **Player fidelity**: bots in TAB with skin and ping, counted in the server list's player count and in `/list`,
  "joined/left the game" messages and vanilla death messages.
- **Hostile mobs attack the bots** (zombies, skeletons, creepers, spiders in the dark, slimes on contact,
  piglins ignore whoever wears gold...), with an exception list (`bots.mobs-that-ignore-bots`).
- Combat, mobility, pathfinding (A* or straight), tactics, persistence, default difficulty...

Upgrading from 1.5.x or older: the old `config.yml` with Portuguese keys is converted to the new English keys
on the first start (values kept, language set to `pt-br`) and saved as `config-old.yml`. The old `messages.yml`
is no longer used and is renamed to `messages-old.yml`; texts now live in `lang/`.

## Limitations

- Other plugins that look for "players" (for example another ManHunt plugin's compass) don't see the bots:
  they show up in TAB, in `/list` and in the count, but they aren't players to the server API.
- Bot and group names in the menu are typed in chat.
- The A* debug lines are particles (`/manhunt settings pathdebug true`), visible to whoever has `manhuntstar.admin`.
- The TAB list uses the server's internal packets; if a future Paper version changes that packet, only that
  feature turns off (with a console warning) and the rest of the plugin keeps working.

## Plugin details

- **Aim**: crossbow, bow, trident and firework crossbow compute the angle from the server's real projectile
  physics and lead the target by the flight time.
- **Bow burst**: the bot draws the bow as much as it needs, like in the game (the arrow's power depends on the
  draw time, up to 3.0 blocks/tick at full draw, which is a critical arrow). It picks the draw that hits soonest,
  adding the draw and flight times, and only shoots if the arrow reaches the player without hitting a block;
  from a hilltop the arrow goes much farther.
- **Firework crossbow**: the rocket leaves at 1.6 blocks/tick, like the game's crossbow, with flight duration 3
  (explodes between 40 and 51 ticks, about 64 blocks). The bot only shoots if the rocket reaches the player before
  exploding and with no block in the way; with the crossbow loaded, it waits up to 1 s for the player to get in
  range and, if they don't, gives up without shooting.
- **Horse**: in the game only one player controls a horse; with a bot mounted, the horse's AI is turned off while
  it rides (the horse turns where the bot looks, gallops, jumps obstacles and gaps, floats on water) and back on
  when it dismounts.
- **Mounted spear**: on horseback, the bot holds the phase's spear and does the game's charge against the target
  (the damage comes from the horse's speed, and at a gallop it knocks riders off). It rides past the target, keeps
  going, turns around and charges again; after 3 passes without a hit, it dismounts and fights with the sword. On
  foot it doesn't use the spear (`combat.mounted-spear`).
- **Boat on the path**: A* compares crossing a lake by boat with walking around it. When the crossing pays off,
  the bot places a boat at the shore, rows to the other side and breaks the boat when it gets off (`mobility.boats`).
- **Bots far from players** keep moving: the plugin keeps the chunks around each bot loaded (with counting, so
  one bot doesn't unload another's chunk) and stops Paper from making the bot "inactive" for being far from players.
- **Skins**: from the Mojang account of the name, from a `.png` in `plugins/ManhuntStar/skins/` or the default robot skin.
- **Persistence**: bots, groups and gear are saved in `bots.yml` and restored on restart.

## Performance

A single tick for every bot; paths computed off the main thread on chunk snapshots shared between bots; heavy
work spread across ticks. In testing, 40 bots fighting each other kept the server at ~3 ms per tick (the limit is 50 ms).

## API for other plugins

`dev.manhuntstar.api.ManhuntStarAPI` (list the bots, find the bot of an entity) and the events
`BotEvents.BotDamageByPlayerEvent`, `BotFallDamageEvent`, `BotDeathEvent` and `BotKilledByPlayerEvent`.

## Building

Needs JDK 25.

```bash
./gradlew build
```

The jar ends up in `build/libs/ManhuntStar-<version>.jar`. `build` also runs the tests, including one that checks
every language file has the same keys and placeholders as `en.yml`.

Test server diagnostics (JVM flags): `-Dmanhuntstar.teste.mira=true` logs whether each bot projectile hit or missed;
`-Dmanhuntstar.teste.cavalo=true` shows horse details in `/manhunt info`.

## Versions and releases

Work goes to the `dev` branch (Dependabot's PRs too). When a PR is merged into `main`, the `Release` workflow
bumps the version in `gradle.properties`, builds and tests, updates `CHANGELOG.md` with the PR's commits, commits
that to `main`, creates the `vX.Y.Z` tag and the release with the jar, and brings `main` back into `dev`.

The version goes up by a patch (1.5.3 → 1.5.4). To go up more, add a label to the PR before merging: `minor`
(1.5.3 → 1.6.0) or `major` (1.5.3 → 2.0.0). With the `sem-release` label the merge doesn't make a release.

The workflow pushes with the `RELEASE_TOKEN` secret: a token from someone in the bypass list of the `main` and
`dev` rulesets, with **Contents: Read and write** permission on this repository.

## Credits

Based on the Fabric mod **T\* (Terminator Star)** by **OpaleMC**, CC0 license: the behavior was studied in the mod
and rewritten from scratch for the Paper API, with fixes and optimizations.
