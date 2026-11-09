# Changelog

Generated automatically on every merge into main.

## 2.0.0 - 2026-11-09

### New

- SonarQube Cloud analysis in the Build workflow (183f193)
- Fabric and NeoForge mods for Minecraft 26.2 and 26.3, server side only (players join with a vanilla client) (3b3337b)
- Releases carry the Fabric and NeoForge mods, also on Modrinth and CurseForge (5142b91)

### Changed

- The bots' AI and the hunt rules moved to a core shared by the plugin and the mods (the plugin plays the same) (5cb47d8)
- Modrinth versions also list Minecraft's patch releases (26.2.1 and the like) (15a6479)
- README with a CurseForge button, and the Discord invite that doesn't expire (f5cba75)
- Shorter release notes, with the Minecraft versions of the mods (d95cee5)
- CodeRabbit also reviews pull requests into dev, leaving out files that don't need a review (1ca4f44)
- Worlds that unload or close leave Manhunt*'s world cache, so they aren't kept in memory (30f4e57)

**Paper:** 26.2-26.3 · **Fabric and NeoForge:** 26.2, 26.3 · **Java:** 25

**Full changelog since 1.6.1:** https://github.com/KAIKOAUGUSTIN/ManhuntStar/compare/v1.6.1...v2.0.0

## 1.6.1 - 2026-11-05

### New

- SECURITY.md (6bf5a28)
- Modrinth, Hangar and Discord buttons in the README (20fd3de)

### Changed

- Update Discord role ID in release workflow (51214c0)
- Revise security reporting instructions (183769c)
- "Create SECURITY.md" listed as New in the release notes (06f4485)
- Release notes accept Add:, Change:, Update: and Remove: too (5a1a5c5)
- /manhunt create takes skin and at right after the name or the amount (53cddc3)
- Code comments in English in the files changed by these fixes and in the tests (aec10fc)
- Test failure messages in English (422524f)

### Updated

- Bump the actions group across 1 directory with 3 updates (b1e3afd)

### Removed

- Portuguese values and old option names in commands (33455a5)

**Paper:** 26.2-26.3 · **Java:** 25

**Full changelog since 1.6.0:** https://github.com/KAIKOAUGUSTIN/ManhuntStar/compare/v1.6.0...v1.6.1

## 1.6.0 - 2026-11-03

### New

- One jar for Paper 26.2 and 26.3 (17e7580)
- Discord notification for dev builds (1ce8986)
- Plugin icon in the README (65762f6)
- Discord announcement for every release (b0cd90f)
- Issue forms for bug reports and feature requests (3336297)
- Repository labels as code (a9c71cd)
- Update check that tells admins when a new version is out (38f0068)
- Anonymous statistics on bStats (4d8b0c1)
- Releases published on Modrinth and Hangar (41f4bcb)
- Release notes overrides for older commits (9f5fac7)

### Changed

- Clarify Paper server version in README (ddd6de4)
- Revise README formatting and enhance clarity (18bb77d)
- README: one jar for 26.2 and 26.3, supported versions and Discord notifications (b1771f9)
- README focused on using the plugin (c33c7c6)
- Discord build notifications as a Components V2 card with a role mention (938db4c)
- Release notes grouped into New, Changed, Updated and Removed (e9d72a8)
- bStats plugin ID 35100 (3c9e420)
- Release notes keep the case of names such as bStats (30b496e)

### Updated

- GitHub Actions on Node 24 (upload-artifact v7), kept up to date by Dependabot (124409d)

**Paper:** 26.2-26.3 · **Java:** 25

**Full changelog since 1.5.5:** https://github.com/KAIKOAUGUSTIN/ManhuntStar/compare/v1.5.5...v1.6.0

## 1.5.5 - 2026-10-31

### Changes

- Release faz os pushes com o RELEASE_TOKEN (499ba0b)
- Add GNU General Public License v3 to LICENSE.md (e2331fb)
- Sistema de idiomas, config e README em inglês (de303c2)
- Build workflow and GitHub files in English (0fc1c03)

**Paper:** 26.3 · **Java:** 25

**Full changelog since 1.5.4:** https://github.com/KAIKOAUGUSTIN/ManhuntStar/compare/v1.5.4...v1.5.5

## 1.5.4 - 2026-10-31

### Changes

- Paper 26.3 support (a807b94)
- Automatic release on every merge into main (c340437)

### Dependencies

- Bump io.papermc.paper:paper-api (3a31a3f)
- Bump gradle-wrapper from 9.8.0 to 9.8.2 (3af64c4)
- Bump org.junit:junit-bom (fa6ee77)

**Paper:** 26.3 · **Java:** 25
