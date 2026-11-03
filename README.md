<p align="center">
  <img src=".github/assets/icon.png" alt="ManhuntStar icon" width="160">
</p>

# 🏹 ManhuntStar

**ManHunt** hunter bots for Paper servers.

<p align="center">
  <a href="https://modrinth.com/plugin/manhuntstar"><img src="https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Download on Modrinth"></a>
  <a href="https://hangar.papermc.io/KAIKOAUGUSTIN/ManhuntStar"><img src="https://img.shields.io/badge/Hangar-Download-3371ED?style=for-the-badge" alt="Download on Hangar"></a>
  <a href="https://discord.gg/SmPcGTbgUM"><img src="https://img.shields.io/badge/Discord-Join%20the%20server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Discord server"></a>
</p>

No friends online? Spawn a pack of hunters that play like real players and try to stop you from beating the game. They run, jump, swim, dig, build towers and bridges, fight with weapon cooldowns, crits and shields, eat, get stronger through phases, use ender pearls, crossbows, tridents, lava buckets, horses (with spear charges), boats and elytra, and follow you to the Nether and the End.

## ⭐ Support the project

If you like ManhuntStar, consider giving the repository a ⭐ on GitHub! It helps the project gain visibility and motivates further development.

## 📦 Installation

1. Download `ManhuntStar-<version>.jar` from the [latest release](https://github.com/KAIKOAUGUSTIN/ManhuntStar/releases/latest).
2. Put it in your server's `plugins/` folder and start the server.

Requirements: Paper 26.2 or 26.3 and Java 25. No other plugins needed.

## 🎯 Quick start

1. Join as an operator and run `/manhunt create Hunter 3` (or open `/manhunt menu`). Three hunters appear in a ring around you.
2. Walk out of the ring. A countdown starts, with a bar at the top of the screen and 3-2-1 titles.
3. When it ends, the hunters come after you. Run!

Use `/manhunt pause` and `/manhunt resume` to stop and continue the hunt, and `/manhunt remove all confirm` to clear every bot.

## ⚙️ Commands

Everything lives under `/manhunt` and needs the `manhuntstar.admin` permission (operators have it by default). The verbs are the same everywhere: `create`/`remove` for bots and groups, `add`/`remove` for lists and `clear` to erase a value. Positions accept `~ ~ ~`; with no position, your own position is used.

### 🎮 Match

| Command | What it does |
|---|---|
| `/manhunt start` | Starts the hunt right away (releases every ring and skips the countdown). |
| `/manhunt pause` · `/manhunt resume` | Pause: freezes each bot where it is (even in the air, mounted or in a boat), invincible and not thinking, and stops the ring countdown, the late-joiner protection, the phase timer and respawning. The pause survives a server restart. |
| `/manhunt target <player>` · `/manhunt target clear` | Makes every bot chase one player / back to normal. |
| `/manhunt ring` · `/manhunt ring point [x y z \| clear]` | Shows the ring; sets a fixed point for the rings (manual center) or clears it (back to automatic). |
| `/manhunt players` · `players add\|remove <player>` · `players mode <off\|allowlist\|blocklist>` | List of players who can (or can't) be hunted and trigger the ring. |

### 🤖 Bots

| Command | What it does |
|---|---|
| `/manhunt create <name> [amount] [difficulty] [skin <player>] [at <x y z>] [phase] [aggression]` | Creates bots where you are (or at the ring point, with a manual center), or at `at x y z`. In MANHUNT mode they spawn in a ring. |
| `/manhunt remove <bot>` · `/manhunt remove all confirm` | Removes the bots with that name / deletes them all, including the save. |
| `/manhunt list` · `/manhunt info <bot>` | Lists the bots (health, phase, target, location, what each one is doing). |
| `/manhunt tp <bot\|all> [player]` | Brings bots to a player (or to you). |
| `/manhunt skin <bot> <player>` | Changes the skin. |
| `/manhunt spawnpoint <bot> [x y z \| clear]` | A bot's respawn point. |
| `/manhunt equip item <item>` · `/manhunt equip armor <tier>` | Gives an item / armor to every bot. |
| `/manhunt test <tnt-craft\|bow-burst\|firework-crossbow>` | Tests a special ability against you. |

### 👥 Groups

The group name comes before the action.

| Command | What it does |
|---|---|
| `/manhunt group <group> role <hunter\|escape>` | The group's role (hunter or escaper). |
| `/manhunt group <group> enemy add\|remove <other>` | Enemy groups fight each other. |
| `/manhunt group <group> spawnpoint [x y z \| clear]` | The group's respawn point. |
| `/manhunt group <group> phases <true\|false>` · `chaseplayers <true\|false>` | Phases and chasing players. |
| `/manhunt group <group> remove` | Removes the group and its bots. |

### 🛠️ Settings and menu

| Command | What it does |
|---|---|
| `/manhunt settings [option] [value]` | Shows or changes options (`language`, `pathmode`, `pathnodes`, `allowtowering`, `targetmode`, `glowhunters true red`, `countdown 20`, `ringradius auto`...). |
| `/manhunt reload` | Reloads `config.yml` and the language files. |
| `/manhunt menu` | Chest menu in four sections: Match, Hunt rules, Bots and Settings. |

Difficulties: `easy`, `medium`, `hard`, `terminator`.

Names with `%`, accents or spaces go in quotes in commands, for example `/manhunt create "Hunter%" 5` and `/manhunt group "Hunter%" role escape`. Tab completion already adds the quotes.

The old option names (`starttime`, `glowing`, `nodes`, `min_sec_range_nether_entering`, `botuseelytra`...) still work in `/manhunt settings`, but don't show up in Tab completion.

## 🔧 Configuration

Everything is commented in `plugins/ManhuntStar/config.yml` and can also be changed in game, with `/manhunt settings` or in `/manhunt menu`. Highlights:

- ⏱️ **Ring and countdown before the hunt** (`manhunt.countdown-seconds`, default 20): the bots wait in the ring until a player who was inside leaves; then the head-start countdown begins, and only then do they start hunting. A ring created far from everyone waits for someone to walk in and out (or for `/manhunt start`). With `manhunt.countdown-on-ring-leave: false` the countdown starts as soon as the bots are created; `0` = the bots are released as soon as someone leaves the ring.
- 📍 **Ring radius and center**: `manhunt.ring-radius` (`0` = automatic, grows with the number of bots) and `manhunt.ring-center` (`auto` = where the ring is created; `manual` = at the point set with `/manhunt ring point`).
- 🛡️ **Late joiners** (`manhunt.late-join`): `protected` (default) isn't a target for the ring's countdown time, at least 5 s; `hunted` becomes a target right away; `out` isn't hunted until the next hunt. Someone who was already in the hunt, drops and comes back is still hunted.
- 👤 **Player list** (`manhunt.player-list-mode` and `/manhunt players`): `allowlist` = only the listed players are hunted and trigger the ring; `blocklist` = the listed players aren't hunted and don't trigger the ring. `/manhunt target` overrides it.
- ✨ **Glow** on the hunters (`appearance.glow-hunters`, with a color) and, separately, on the escapers. Off by default; turn it on and change the color in `/manhunt menu` (Settings) or with `/manhunt settings glowhunters true red`.
- 👾 **Hostile mobs attack the bots** (zombies, skeletons, creepers, spiders in the dark, slimes on contact, piglins ignore whoever wears gold...), with an exception list (`bots.mobs-that-ignore-bots`).
- 🔔 **Update check** (`general.update-check`, on by default): looks for a new version when the server starts and every 6 hours. A new version is logged to the console and shown to admins when they join, with a download link.
- 📊 **Anonymous statistics** on [bStats](https://bstats.org) (server count, Paper version, number of bots, language and default difficulty). To turn them off for every plugin, set `enabled: false` in `plugins/bStats/config.yml`.
- Combat, mobility, pathfinding, tactics, saving, default difficulty...

Upgrading from 1.5.x or older: your old `config.yml` is converted on the first start (values kept, language set to `pt-br`) and the original is saved as `config-old.yml`. The old `messages.yml` is renamed to `messages-old.yml`; texts now live in the language files.

## ✨ Features

- 🎯 **Sharp aim**: bows, crossbows, tridents and firework crossbows lead moving targets, and the bots don't waste shots when a block is in the way.
- 🐎 **Horses and spears**: mounted bots gallop, jump gaps and charge with spears; on foot they switch to swords.
- 🚣 **Boats**: when crossing a lake is faster than walking around it, the bots place a boat and row across.
- 🧭 **No idle bots**: bots far from every player keep moving and chasing.
- 🎭 **They look like players**: shown in TAB with skin and ping, counted in the server list and in `/list`, with "joined/left the game" and vanilla death messages.
- 🎨 **Skins**: from any Mojang account, from a `.png` in `plugins/ManhuntStar/skins/` or the default robot skin.
- 💾 **Saved**: bots, groups and gear come back after a restart.
- ⚡ **Light on the server**: in testing, 40 bots fighting each other kept the server at about 3 ms per tick (the limit is 50 ms).

## 🌍 Languages

ManhuntStar comes in **English** (`en`, the default), **Brazilian Portuguese** (`pt-br`), **French** (`fr`), **Russian** (`ru`) and **Simplified Chinese** (`zh-cn`). Every message and the whole menu are translated; commands and `config.yml` keys stay in English.

Change the language with `/manhunt settings language pt-br`, in `/manhunt menu` (Settings → General) or with `general.language` in `config.yml`. It switches right away.

### Translating the plugin

1. Copy `plugins/ManhuntStar/lang/en.yml` to a new file in the same folder, named after your language, for example `es.yml`.
2. Translate the texts between quotes. Keep the tags in `<...>`: `<bot>`, `<player>`, `<amount>` and the like are filled in by the plugin, and color tags such as `<red>` or `<bold>` follow the [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format.
3. Run `/manhunt reload`, then `/manhunt settings language es`.

Anything you leave out falls back to English, so you can translate a little at a time. You can also edit the included files (`en.yml`, `pt-br.yml`...), but when an update changes their default texts, your copy is moved to `lang/old/` and replaced; a file with your own name is never replaced.

Want your translation in the plugin? Open a pull request adding it to `src/main/resources/lang/`.

## 🔌 API for other plugins

`dev.manhuntstar.api.ManhuntStarAPI` lists the bots and finds the bot behind an entity. Events: `BotEvents.BotDamageByPlayerEvent`, `BotFallDamageEvent`, `BotDeathEvent` and `BotKilledByPlayerEvent`.

## 🏗️ Building

Needs JDK 25. Run `./gradlew build`; the jar ends up in `build/libs/`.

## ⚠️ Limitations

- Other plugins that look for players (for example another ManHunt plugin's compass) don't see the bots: they show up in TAB, in `/list` and in the player count, but they aren't real players.
- Bot and group names in the menu are typed in chat.

## 🙏 Credits

Based on the Fabric mod **T\* (Terminator Star)** by **OpaleMC**, CC0 license: the behavior was studied in the mod and rewritten from scratch for the Paper API, with fixes and optimizations.
