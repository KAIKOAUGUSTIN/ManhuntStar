# Accessibility

ManhuntStar runs on the server, so it can't change the game's own options. What it does is tell players
everything they need to know in more than one way, and use the game's own sounds and texts, so the
accessibility options each player sets in Minecraft work with it.

This page lists what there is, what server owners can adjust, the known limits and how to report a
problem.

## For players

### Every warning comes in more than one way

| What happens | On screen | In the chat | Sound |
|---|---|---|---|
| The bots wait in the ring | Bar at the top: "Leave the ring to start" | When the ring is created | — |
| The countdown | Bar with the seconds left, and 3-2-1 titles | "*Player* left the ring! The hunt starts in *N* seconds." | At 10 seconds, and at 3, 2 and 1 |
| The hunt begins | "The hunt has begun!" title | "The hunt has begun!" | The Ender Dragon's growl |
| A pause | "Hunt paused" bar | When it pauses and when it resumes | — |
| Protection after joining late | Seconds left above the hotbar | When you join and when it ends | — |
| The end of the match | VICTORY or DEFEAT title | A summary that stays in the chat | The challenge-complete sound, or the Wither's death |

Messages say what happened in words. Their colors only highlight it.

### Game options that help

These are in Minecraft's own settings, and each player chooses their own:

- **Show Subtitles** (Options → Accessibility Settings, or Music & Sounds): the bots use the game's own
  sounds (hits, shields, bows, crossbows and tridents, ender pearls, eating, placing and breaking
  blocks, buckets, horses...), so they get subtitles like any other sound. The countdown's sounds do too.
- **Volume** (Options → Music & Sounds): the bots' sounds play under Players, Blocks, Friendly
  Creatures, Hostile Creatures or Ambient/Environment, like a real player's. The countdown, the end of
  the match and the menu's click play under Master Volume, so turning down Players doesn't hide them.
- **Narrator** (Options → Accessibility Settings, or Ctrl+B), set to read everything: it reads the chat,
  where the countdown, the pause and the end of the match are also announced.
- **Chat Settings**: text size, line spacing and background opacity make the messages easier to read.

### The menu and the commands

- Everything in `/manhunt menu` can also be done with commands, and each option in the menu shows its
  own `/manhunt settings` command. Commands have tab completion.
- An option's value is written in its description ("Value: on"), not only shown by the color of the
  icon.
- Names (bots, skins, players) are typed in the game's dialog box. Esc cancels it.

## For server owners

- **Language**: English, Brazilian Portuguese, French, Russian and Simplified Chinese come with the
  plugin, and you can add your own (see the README). Missing texts fall back to English.
- **Colors and texts**: every message, title, bar and menu text is in the files in `lang/`
  (`plugins/ManhuntStar/lang/` on Paper, `config/manhuntstar/lang/` on the mods). Gray text on the chat
  background has less contrast: you can change `<gray>` or `<dark_gray>` to `<white>`, or reword any
  text. Updates keep the texts you edited, except one whose meaning changed so much that the update
  replaces it.
- **Telling the bots apart**: the glow (`appearance.glow-hunters` and `appearance.glow-escapers`) draws
  an outline seen through walls, with a color for the hunters and another for the escapers, and
  `appearance.show-name-tag` shows each bot's name above it. If your players have trouble telling red
  from aqua (the default colors), yellow and blue are a pair most people with color vision deficiency
  can tell apart. Avoid red with green.
- **More time**: `manhunt.countdown-seconds` sets the head start (20 seconds by default), and
  `manhunt.late-join: protected` (the default) keeps the bots off whoever joins late for the
  countdown's length, at least 5 seconds.
  `/manhunt pause` freezes every bot and stops every timer until `/manhunt resume`, for a break that
  doesn't cost anyone the match.
- **How hard it is**: the difficulty (`easy`, `medium`, `hard` or `terminator`) and the number of bots.
- **Bright and loud effects**: the firework crossbow (`tactics.firework-crossbow`) and the TNT ambush
  (`tactics.auto-abilities`) are off by default. The bots' party and gloom at the end of the match are
  only movement, without fireworks or particles.

## Known limits

- The language is the same for every player on the server: it doesn't follow each player's own game
  language.
- The settings, the glow colors included, are the same for every player.
- The titles stay a short time (each countdown number about a second), and the Narrator reads the chat
  but not the bars or the titles. That's why the countdown and the end of the match are also in the
  chat and come with sounds.
- The chest menu is made of item icons. Each one has a name and a description, but when it's hard to
  use, the commands do the same.

## Reporting a problem

If something in ManhuntStar is hard to see, hear or use, open an issue with the **Bug report** form (or
**Feature request**, for an idea) and say that it's about accessibility, or tell us on the
[Discord server](https://discord.gg/X6P447F7vx). Saying what you use helps: the Narrator, subtitles,
the kind of color vision deficiency, a larger text size...

## For contributors

When you add something players see or hear:

- Don't tell it only by a color: say it in words too.
- Don't tell it only by a sound, or only in a title or a bar: important events go to the chat too, which
  the Narrator reads.
- Put the texts in the language files, so server owners can translate them and change their colors.
- Use the game's own sounds, in the right category, so subtitles and the volume sliders work.
- Put anything that flashes or is very loud behind an option.
- Keep the menu and the commands doing the same things.
- In the documentation, give images a text description (`alt`), and describe in words what a
  screenshot shows.

[CONTRIBUTING.md](CONTRIBUTING.md) has the rest of the contribution guide.
