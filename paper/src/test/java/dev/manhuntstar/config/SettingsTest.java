package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.LateJoin;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.bot.Modes.PlayerListMode;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.bot.Modes.TargetMode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opções: chaves e apelidos do mod, faixas e conversão de texto. */
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
        assertNotNull(s.byKey("allowhorseriding"));
    }

    /** Os nomes antigos (do mod e das versões anteriores) continuam valendo, fora do Tab. */
    @Test
    void oldKeysStillWork() {
        Settings s = new Settings();
        assertSame(s.nodeCap, s.byKey("nodes"));
        assertSame(s.mobsAttackBots, s.byKey("MobTarget"));
        assertSame(s.minNetherDelay, s.byKey("min_sec_range_nether_entering"));
        assertSame(s.useElytra, s.byKey("botuseelytra"));
        assertSame(s.startCountdown, s.byKey("starttime"));
        assertSame(s.glowHunters, s.byKey("glowing"));
        assertSame(s.playerListMode, s.byKey("listmode"));
        for (Setting<?> each : s.all()) {
            assertFalse(each.key().contains("_"), "chave fora do padrão: " + each.key());
        }
    }

    /** A lista usa off/allowlist/blocklist; os nomes em português das versões antigas ainda valem. */
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
        s.lateJoin.setFromText("caçado");
        assertSame(LateJoin.HUNTED, s.lateJoin.get());
        s.playerListMode.setFromText("blacklist");
        assertSame(PlayerListMode.BLOCKLIST, s.playerListMode.get());
        s.ringRadius.setFromText("200");
        assertEquals(64.0, s.ringRadius.value());
    }

    /** Permitidos: só os da lista; bloqueados: todos menos os da lista; nomes sem diferenciar maiúsculas. */
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
        s.allowPearl.setFromText("não");
        assertFalse(s.allowPearl.on());
        s.allowPearl.setFromText("sim");
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

    /** Toda opção do Settings está no config.yml do plugin, com o mesmo valor padrão. */
    @Test
    void everyOptionIsInTheBundledConfig() throws Exception {
        Settings s = new Settings();
        YamlConfiguration cfg;
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml não encontrado");
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (Setting<?> each : s.all()) {
            assertTrue(cfg.contains(each.path()), "falta no config.yml: " + each.path());
            Object def = each.get();
            each.setFromText(String.valueOf(cfg.get(each.path())));
            assertEquals(def, each.get(), "padrão diferente do config.yml: " + each.path());
        }
    }

    /** Um config.yml das versões com chaves em português é lido pelos caminhos antigos. */
    @Test
    void legacyConfigIsMigrated(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                geral:
                  tab-list: false
                bots:
                  vida-maxima: 40
                  mobs-que-nao-atacam-bots: [CREEPER]
                combate:
                  modo-alvo: closest
                manhunt:
                  centro-roda: manual
                  quem-entra-no-meio: cacado
                  lista-modo: permitidos
                  lista-jogadores: [Steve]
                  ponto-roda: {mundo: world, x: 1.0, y: 64.0, z: -2.0}
                """);
        assertTrue(Settings.isLegacy(file.toFile()));
        Settings s = new Settings();
        s.loadLegacy(file.toFile(), Logger.getAnonymousLogger());
        assertFalse(s.tabList.on());
        assertEquals(40.0, s.botHealth.value());
        assertSame(TargetMode.CLOSEST_PLAYER, s.targetMode.get());
        assertSame(RingCenter.MANUAL, s.ringCenter.get());
        assertSame(LateJoin.HUNTED, s.lateJoin.get());
        assertSame(PlayerListMode.ALLOWLIST, s.playerListMode.get());
        assertTrue(s.isListed("steve"));
        assertTrue(s.excludedMobs().contains(EntityType.CREEPER));
        assertEquals(new Settings.RingPoint("world", 1.0, 64.0, -2.0), s.ringPoint());
        assertEquals("pt-br", s.language.get());

        Path migrated = dir.resolve("new.yml");
        s.save(migrated.toFile(), Logger.getAnonymousLogger());
        assertFalse(Settings.isLegacy(migrated.toFile()));
        Settings again = new Settings();
        again.load(migrated.toFile(), Logger.getAnonymousLogger());
        assertEquals(40.0, again.botHealth.value());
        assertSame(PlayerListMode.ALLOWLIST, again.playerListMode.get());
        assertEquals(new Settings.RingPoint("world", 1.0, 64.0, -2.0), again.ringPoint());
        assertTrue(again.excludedMobs().contains(EntityType.CREEPER));
        assertEquals("pt-br", again.language.get());
    }

    /** O idioma só aceita um dos arquivos que existem, sem diferenciar "PT_BR" de "pt-br". */
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

    @Test
    void difficultyAliases() {
        assertSame(BotDifficulty.HARD, BotDifficulty.parse("difficulty.hard"));
        assertSame(BotDifficulty.EASY, BotDifficulty.parse("fácil"));
        assertSame(BotDifficulty.TERMINATOR, BotDifficulty.parse("TERMINATOR"));
        assertEquals(8, BotDifficulty.TERMINATOR.startPhase());
        assertEquals(600 * 20, BotDifficulty.TERMINATOR.phaseDurationTicks());
    }
}
