<p align="center">
  <img src=".github/assets/icon.png" alt="ManhuntStar icon" width="160">
</p>

# 🏹 ManhuntStar

**ManHunt** hunter bots for Paper servers, and for Fabric and NeoForge on servers and on your own client (singleplayer and LAN).

<p align="center">
  <a href="https://modrinth.com/plugin/manhuntstar"><img src="https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Download on Modrinth"></a>
  <a href="https://hangar.papermc.io/KAIKOAUGUSTIN/ManhuntStar"><img src="https://img.shields.io/badge/Hangar-Download-3371ED?style=for-the-badge" alt="Download on Hangar"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/manhuntstar"><img src="https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="Download on CurseForge"></a>
  <a href="https://discord.gg/X6P447F7vx"><img src="https://img.shields.io/badge/Discord-Join%20the%20server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Discord server"></a>
</p>

No friends online? Spawn a pack of hunters that play like real players and try to stop you from beating the game. They run, jump, swim, dig, build towers and bridges, fight with weapon cooldowns, crits and shields, eat, get stronger through phases, use ender pearls, crossbows, tridents, lava buckets, horses (with spear charges), boats and elytra, and follow you to the Nether and the End.

## 🖥️ Where it runs

|  | Paper (plugin) | Fabric / NeoForge (mod) |
|---|---|---|
| **Dedicated server** | ✅ Install it on the server | ✅ Install it on the server |
| **Client (singleplayer / LAN)** | — | ✅ Install it on your own client |
| **Players who join** | Nothing to install (vanilla client) | Nothing to install (vanilla client) |

The Paper plugin runs on the server. The Fabric and NeoForge mods work both ways: put them on a dedicated server, or in your own game's `mods/` folder to play with the bots in singleplayer and in the LAN worlds you open.

Either way, whoever joins doesn't need to install anything: a vanilla client is enough. In a LAN world only the host needs the mod.

## ⭐ Support the project

If you like ManhuntStar, consider giving the repository a ⭐ on GitHub! It helps the project gain visibility and motivates further development.

## 📦 Installation

**Paper** (plugin):

1. Download `ManhuntStar-<version>.jar` from the [latest release](https://github.com/KAIKOAUGUSTIN/ManhuntStar/releases/latest), Modrinth or Hangar.
2. Put it in your server's `plugins/` folder and start the server.

You need Paper 26.2 or 26.3 and Java 25. No other plugins are needed.

**Fabric or NeoForge** (mod):

1. Download the file for your loader and Minecraft version, for example `ManhuntStar-fabric-<version>+26.3.jar` or `ManhuntStar-neoforge-<version>+26.2.jar`, from the [latest release](https://github.com/KAIKOAUGUSTIN/ManhuntStar/releases/latest), Modrinth or CurseForge.
2. Put it in the `mods/` folder:
   - **On a server:** the server's `mods/` folder, then start the server. Players join with a vanilla client.
   - **For singleplayer or LAN:** your game's `mods/` folder, then open a world. Friends joining your LAN world don't need it.
3. On Fabric, you also need [Fabric API](https://modrinth.com/mod/fabric-api) in the same `mods/` folder.

You need Minecraft 26.2 or 26.3 and Java 25.

The mods do everything the plugin does. Their files (settings, languages, skins and saved bots) are in `config/manhuntstar/` instead of `plugins/ManhuntStar/`. Only operators can use `/manhunt`, unless you give the `manhuntstar.admin` permission to other players with a permissions mod such as LuckPerms.

## 🎯 Quick start

1. Join as an operator and run `/manhunt create Hunter 3` (or open `/manhunt menu`). Three hunters appear in a ring around you.
2. Walk out of the ring. A countdown starts, with a bar at the top of the screen and 3-2-1 titles.
3. When it ends, the hunters come after you. Run!
4. Kill the Ender Dragon to win. If the hunters get you, you lose. Everyone sees a victory or defeat title and a short summary in the chat, and the bots stay where they are, celebrating or moping, until the next hunt.

Use `/manhunt pause` and `/manhunt resume` to stop and continue the hunt, and `/manhunt remove all confirm` to remove every bot.

## ⚙️ Commands

Everything is under `/manhunt` and needs the `manhuntstar.admin` permission (operators have it by default). The words are the same everywhere: `create` and `remove` for bots and groups, `add` and `remove` for lists, and `clear` to erase something. Where a command asks for a position, you can use `~ ~ ~`; without one, your own position is used.

### 🎮 Match

| Command | What it does |
|---|---|
| `/manhunt start` | Starts the hunt right away, without waiting for the countdown. |
| `/manhunt pause` · `/manhunt resume` | Freezes every bot where it is (even in the air, on a horse or in a boat), where nothing can hurt it, and stops the countdown and the hunt's timers until you resume. A paused hunt stays paused after a server restart. |
| `/manhunt target <player>` · `/manhunt target clear` | Makes every bot chase one player / goes back to normal. |
| `/manhunt ring` · `/manhunt ring point [x y z \| clear]` | Shows the ring; sets a fixed spot for the ring, or goes back to placing it where the bots are created. |
| `/manhunt players` · `players add\|remove <player>` · `players mode <off\|allowlist\|blocklist>` | Chooses who can (or can't) be hunted. |

### 🤖 Bots

| Command | What it does |
|---|---|
| `/manhunt create <name> [amount] [difficulty] [skin <player>] [at <x y z>] [phase] [aggression]` | Creates bots where you are (or at the ring's fixed spot, if you set one), or at `at x y z`. In a hunt they appear in a ring. |
| `/manhunt remove <bot>` · `/manhunt remove all confirm` | Removes the bots with that name / removes all of them, for good. |
| `/manhunt list` · `/manhunt info <bot>` | Lists the bots: health, phase, target, location and what each one is doing. |
| `/manhunt tp <bot\|all> [player]` | Brings bots to a player (or to you). |
| `/manhunt skin <bot> <player>` | Changes a bot's skin. |
| `/manhunt spawnpoint <bot> [x y z \| clear]` | Sets where a bot respawns. |
| `/manhunt equip item <item>` · `/manhunt equip armor <tier>` | Gives an item or armor to every bot. |
| `/manhunt test <tnt-craft\|bow-burst\|firework-crossbow>` | Tries one of the bots' special moves on you. |

### 👥 Groups

Write the group's name before what you want to do.

| Command | What it does |
|---|---|
| `/manhunt group <group> role <hunter\|escape>` | Whether the group hunts or runs away. |
| `/manhunt group <group> enemy add\|remove <other>` | Enemy groups fight each other. |
| `/manhunt group <group> spawnpoint [x y z \| clear]` | Sets where the group respawns. |
| `/manhunt group <group> phases <true\|false>` · `chaseplayers <true\|false>` | Whether the group gets stronger through phases, and whether it chases players. |
| `/manhunt group <group> remove` | Removes the group and its bots. |

### 🛠️ Settings and menu

| Command | What it does |
|---|---|
| `/manhunt menu` | A chest menu in four sections: Match, Hunt rules, Bots and Settings. Names (of bots, skins, players) are typed in a text box. |
| `/manhunt settings [option] [value]` | Shows or changes an option from the chat, for example `/manhunt settings language pt-br` or `/manhunt settings countdown 20`. |
| `/manhunt reload` | Reloads the settings and the language files. |

Difficulties: `easy`, `medium`, `hard`, `terminator`.

Names with `%`, accents or spaces go in quotes, for example `/manhunt create "Hunter%" 5` and `/manhunt group "Hunter%" role escape`. Pressing Tab to complete a name adds the quotes for you.

## 🔧 Configuration

You can change everything in the game, in `/manhunt menu` or with `/manhunt settings`. Everything is also in `config.yml` (in `plugins/ManhuntStar/`, or `config/manhuntstar/` on the mods), where every option is explained. Some of what you can set:

- ⏱️ **Ring and head start**: before the hunt, the bots wait in a ring. When a player inside it walks out, a countdown gives everyone a head start (20 seconds by default), and only then do the bots start hunting. You can change how long it lasts, start it as soon as the bots are created, or skip it.
- 📍 **Ring size and place**: the ring grows with the number of bots, or has the size you choose. It appears where the bots are created, or on a fixed spot you set with `/manhunt ring point`.
- 🛡️ **Players who join late**: by default they get a head start too, as long as the ring's countdown (at least 5 seconds), before the bots go after them. You can also make them targets right away, or keep them out until the next hunt. Someone who was already playing, drops and comes back is still hunted.
- 🏆 **End of the match**: the hunted players win when the Ender Dragon dies. By default they lose once each of them has died once; you can make the first death end it, or let them keep going no matter how many times they die. When deaths count, whoever dies watches the rest as a spectator, and 15 seconds after the end everyone is back to normal. A summary shows who decided the match, how long the hunt lasted (without the pauses), the deaths on each side, the bots' highest phase and the bot that killed the most.
- 👤 **Who gets hunted**: everyone, only the players on a list, or everyone except them (`/manhunt players`). `/manhunt target` makes every bot chase one player, whatever the list says.
- ✨ **Glow**: the hunters, and separately the escapers, can glow in a color you choose. Off by default.
- 👾 **Hostile mobs attack the bots**: zombies, skeletons, creepers, spiders in the dark, slimes on contact... and piglins leave alone whoever wears gold. You can list mobs that should ignore the bots.
- 🔔 **Update check**: ManhuntStar looks for a new version when the server starts and every 6 hours, and lets admins know when they join, with a download link.
- 📊 **Anonymous statistics and error reports**: to help improve ManhuntStar, it sends anonymous statistics to [FastStats](https://faststats.dev) (how many servers use it, the Minecraft, loader and Java versions, the number of bots, the language and the default difficulty), and reports of its own errors, without IP addresses or user names. This happens on servers and on your client (singleplayer and LAN), and is on by default. You can turn either one off in `config.yml`, or every plugin's and mod's at once in FastStats' own settings (`faststats/config.properties`); in the game, also under Options → Telemetry Data → FastStats. The first time FastStats runs on a server or a computer, nothing is sent until the next restart. If you had turned bStats off for every plugin, these start off too.
- And much more: combat, movement, tactics, saving and the bots' default difficulty.

When you update ManhuntStar, your `config.yml` and language files are updated on the first start: new options and texts are added and explained, and your values and the texts you edited are kept. A copy of the old file is saved in `old/` (`lang/old/` for the languages).

Coming from version 1.5 or older? Your old settings are converted on the first start, with the language set to Brazilian Portuguese, and the original file is kept as `config-old.yml`. Your old `messages.yml` is kept as `messages-old.yml`: the texts are now in the language files.

## ✨ Features

- 🎯 **Sharp aim**: bows, crossbows, tridents and firework crossbows aim ahead of moving targets, and the bots don't waste shots when a block is in the way.
- 🐎 **Horses and spears**: mounted bots gallop, jump gaps and charge with spears; on foot they switch to swords.
- 🚣 **Boats**: when crossing a lake is faster than walking around it, the bots place a boat and row across.
- 🧭 **No idle bots**: bots far from every player keep moving and chasing.
- 🎭 **They look like players**: they show up in the player list (Tab) with skin and ping, count as online players, and get "joined/left the game" and the usual death messages.
- 🎨 **Skins**: from any Minecraft account, from a `.png` you put in `plugins/ManhuntStar/skins/`, or the default robot skin.
- 💾 **Saved**: bots, groups and gear come back after a restart.
- ⚡ **Light on the server**: in testing, 40 bots fighting each other used only a small part of what the server can handle (about 3 ms of the 50 ms it has each tick).

## 🌍 Languages

ManhuntStar comes in **English** (`en`, the default), **Brazilian Portuguese** (`pt-br`), **French** (`fr`), **Russian** (`ru`) and **Simplified Chinese** (`zh-cn`). Every message and the whole menu are translated; commands and the option names in `config.yml` stay in English.

Change the language with `/manhunt settings language pt-br`, in `/manhunt menu` (Settings → General) or in `config.yml`. It switches right away.

### Translating ManhuntStar

1. Copy `plugins/ManhuntStar/lang/en.yml` to a new file in the same folder, named after your language, for example `es.yml`.
2. Translate the texts between quotes. Leave everything between `<` and `>` as it is: names like `<bot>` and `<player>` are filled in by ManhuntStar, and tags like `<red>` or `<bold>` set the colors and styles.
3. Run `/manhunt reload`, then `/manhunt settings language es`.

Anything you leave out shows up in English, so you can translate a little at a time. You can also edit the files that come with ManhuntStar (`en.yml`, `pt-br.yml`...): when an update brings new texts, they are added on the first start and the texts you edited are kept (unless a text was removed, or changed so much that the update replaces it). A copy of your previous file is saved in `lang/old/`. A file you created yourself is never changed.

Want your translation to come with ManhuntStar? See [CONTRIBUTING.md](CONTRIBUTING.md).

## 🧑‍💻 For developers

Building ManhuntStar, its API for other plugins and how to send changes: see [CONTRIBUTING.md](CONTRIBUTING.md).

## ⚠️ Limitations

- Other plugins and mods that look for players (for example another ManHunt plugin's compass) don't see the bots: they show up in the player list and in the player count, but they aren't real players.

## 🙏 Credits

Based on the Fabric mod **T\* (Terminator Star)** by **OpaleMC**, CC0 license: its behavior was studied and rewritten from scratch, with fixes and optimizations.
