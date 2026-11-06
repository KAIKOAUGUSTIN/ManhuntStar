package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.LateJoin;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.bot.Modes.PlayerListMode;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.bot.Modes.TargetMode;
import dev.manhuntstar.platform.ConfigNode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Options: keys, ranges and text conversion. */
class SettingsTest {

    @Test
    void defaultsMatchTheMod() {
        Settings s = new Settings();
        assertEquals(2500, s.nodeCap.value());
        assertEquals(1.5, s.greediness.value());
        assertEquals(20.0, s.botHealth.value());
        assertEquals(200.0, s.redistDistance.value());
        assertEquals(2.0, s.targetPriority.value());
        assertEquals(0.12, s.meleeMissChance.value());
        assertSame(TargetMode.EQUAL_BOTS_PER_PLAYER, s.targetMode.get());
        assertSame(PearlDimension.NETHER_ONLY, s.pearlDimension.get());
        assertFalse(s.useElytra.on());
        assertTrue(s.allowTowering.on());
    }

    @Test
    void keysAndAliasesResolve() {
        Settings s = new Settings();
        assertSame(s.nodeCap, s.byKey("pathnodes"));
        assertSame(s.mobsAttackBots, s.byKey("MobsAttackBots"));
        assertSame(s.minNetherDelay, s.byKey("minnetherdelay"));
        assertSame(s.useElytra, s.byKey("allowelytra"));
        assertSame(s.startCountdown, s.byKey("countdown"));
        assertSame(s.glowHunters, s.byKey("glowhunters"));
        assertSame(s.glowEscapersColor, s.byKey("glowescaperscolor"));
        assertSame(s.countdownOnLeave, s.byKey("countdownonleave"));
        assertSame(s.ringRadius, s.byKey("ringradius"));
        assertSame(s.ringCenter, s.byKey("ringcenter"));
        assertSame(s.lateJoin, s.byKey("latejoin"));
        assertSame(s.playerListMode, s.byKey("playerlistmode"));
        assertSame(s.allowHorse, s.byKey("allowhorse"));
    }

    /** The old option names (from the mod and from earlier versions) no longer work in /manhunt settings. */
    @Test
    void oldOptionNamesAreGone() {
        Settings s = new Settings();
        assertNull(s.byKey("nodes"));
        assertNull(s.byKey("MobTarget"));
        assertNull(s.byKey("min_sec_range_nether_entering"));
        assertNull(s.byKey("botuseelytra"));
        assertNull(s.byKey("starttime"));
        assertNull(s.byKey("glowing"));
        assertNull(s.byKey("listmode"));
        assertNull(s.byKey("allowhorseriding"));
        for (Setting<?> each : s.all()) {
            assertFalse(each.key().contains("_"), "key out of format: " + each.key());
        }
    }

    /** The list uses off/allowlist/blocklist. */
    @Test
    void playerListModeEnglishWords() {
        Settings s = new Settings();
        s.playerListMode.setFromText("allowlist");
        assertSame(PlayerListMode.ALLOWLIST, s.playerListMode.get());
        s.playerListMode.setFromText("blocklist");
        assertSame(PlayerListMode.BLOCKLIST, s.playerListMode.get());
        s.playerListMode.setFromText("off");
        assertSame(PlayerListMode.OFF, s.playerListMode.get());
    }

    @Test
    void ringAndHuntDefaults() {
        Settings s = new Settings();
        assertEquals(0.0, s.ringRadius.value());
        assertSame(RingCenter.AUTO, s.ringCenter.get());
        assertSame(LateJoin.PROTECTED, s.lateJoin.get());
        assertSame(PlayerListMode.OFF, s.playerListMode.get());
        s.lateJoin.setFromText("hunted");
        assertSame(LateJoin.HUNTED, s.lateJoin.get());
        s.playerListMode.setFromText("blacklist");
        assertSame(PlayerListMode.BLOCKLIST, s.playerListMode.get());
        s.ringRadius.setFromText("200");
        assertEquals(64.0, s.ringRadius.value());
    }

    /** Allowlist: only those on the list; blocklist: everyone except those on the list; names are case-insensitive. */
    @Test
    void playerListAllowsAndBlocks() {
        Settings s = new Settings();
        assertTrue(s.addListedPlayer("Steve"));
        assertFalse(s.addListedPlayer("steve"));
        assertTrue(s.listAllows("Alex"));
        s.playerListMode.set(PlayerListMode.ALLOWLIST);
        assertTrue(s.listAllows("STEVE"));
        assertFalse(s.listAllows("Alex"));
        s.playerListMode.set(PlayerListMode.BLOCKLIST);
        assertFalse(s.listAllows("steve"));
        assertTrue(s.listAllows("Alex"));
        assertTrue(s.removeListedPlayer("STEVE"));
        assertFalse(s.removeListedPlayer("Steve"));
        assertTrue(s.listAllows("Steve"));
    }

    @Test
    void parsingAndClamping() {
        Settings s = new Settings();
        s.allowPearl.setFromText("off");
        assertFalse(s.allowPearl.on());
        s.allowPearl.setFromText("yes");
        assertTrue(s.allowPearl.on());
        s.nodeCap.setFromText("999999");
        assertEquals(5000, s.nodeCap.value());
        s.greediness.setFromText("0,5");
        assertEquals(1.0, s.greediness.value());
        s.targetMode.setFromText("closest");
        assertSame(TargetMode.CLOSEST_PLAYER, s.targetMode.get());
        s.pearlDimension.setFromText("1");
        assertSame(PearlDimension.BOTH, s.pearlDimension.get());
        Setting.InvalidValue e = assertThrows(Setting.InvalidValue.class, () -> s.respawn.setFromText("talvez"));
        assertEquals("settings.error.boolean", e.getMessage());
    }

    /** Every option in Settings is in the plugin's config.yml, with the same default value. */
    @Test
    void everyOptionIsInTheBundledConfig() throws Exception {
        Settings s = new Settings();
        ConfigNode cfg;
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml not found");
            cfg = new SnakeYamlFiles(Logger.getAnonymousLogger()).load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (Setting<?> each : s.all()) {
            assertTrue(cfg.contains(each.path()), "falta no config.yml: " + each.path());
            Object def = each.get();
            each.setFromText(String.valueOf(cfg.get(each.path())));
            assertEquals(def, each.get(), "default differs from config.yml: " + each.path());
        }
    }

    /** The language only takes one of the files that exist, treating "PT_BR" and "pt-br" the same. */
    @Test
    void languageMustExist() {
        Settings s = new Settings();
        assertEquals("en", s.language.get());
        s.language.setFromText("PT_BR");
        assertEquals("pt-br", s.language.get());
        Setting.InvalidValue e = assertThrows(Setting.InvalidValue.class, () -> s.language.setFromText("xx"));
        assertEquals("settings.error.choice", e.getMessage());
        s.setLanguages(() -> java.util.List.of("en", "de"));
        s.language.setFromText("de");
        assertEquals("de", s.language.get());
    }

    /** Commands only take English values: the Portuguese ones from old versions are refused. */
    @Test
    void portugueseValuesAreRefused() {
        Settings s = new Settings();
        assertThrows(Setting.InvalidValue.class, () -> s.allowPearl.setFromText("não"));
        assertThrows(Setting.InvalidValue.class, () -> s.allowPearl.setFromText("sim"));
        assertThrows(Setting.InvalidValue.class, () -> s.lateJoin.setFromText("caçado"));
        assertThrows(Setting.InvalidValue.class, () -> s.ringCenter.setFromText("automático"));
        assertThrows(Setting.InvalidValue.class, () -> s.playerListMode.setFromText("permitidos"));
        assertThrows(Setting.InvalidValue.class, () -> s.targetMode.setFromText("perto"));
        assertThrows(Setting.InvalidValue.class, () -> s.pearlDimension.setFromText("ambas"));
        assertThrows(Setting.InvalidValue.class, () -> s.defaultDifficulty.setFromText("difícil"));
        assertNull(BotDifficulty.parse("fácil"));
    }

    @Test
    void difficultyAliases() {
        assertSame(BotDifficulty.HARD, BotDifficulty.parse("difficulty.hard"));
        assertSame(BotDifficulty.EASY, BotDifficulty.parse("easy"));
        assertSame(BotDifficulty.TERMINATOR, BotDifficulty.parse("TERMINATOR"));
        assertEquals(8, BotDifficulty.TERMINATOR.startPhase());
        assertEquals(600 * 20, BotDifficulty.TERMINATOR.phaseDurationTicks());
    }
}
