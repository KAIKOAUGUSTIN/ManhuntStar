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
import java.util.Map;
import org.junit.jupiter.api.Test;

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

    /** O comando de lista usa off/allowlist/blocklist; o config.yml guarda os nomes em português. */
    @Test
    void playerListModeEnglishWords() {
        Settings s = new Settings();
        s.playerListMode.setFromText("allowlist");
        assertSame(PlayerListMode.PERMITIDOS, s.playerListMode.get());
        s.playerListMode.setFromText("blocklist");
        assertSame(PlayerListMode.BLOQUEADOS, s.playerListMode.get());
        s.playerListMode.setFromText("off");
        assertSame(PlayerListMode.DESLIGADA, s.playerListMode.get());
    }

    @Test
    void ringAndHuntDefaults() {
        Settings s = new Settings();
        assertEquals(0.0, s.ringRadius.value());
        assertSame(RingCenter.AUTOMATICO, s.ringCenter.get());
        assertSame(LateJoin.PROTEGIDO, s.lateJoin.get());
        assertSame(PlayerListMode.DESLIGADA, s.playerListMode.get());
        s.lateJoin.setFromText("caçado");
        assertSame(LateJoin.CACADO, s.lateJoin.get());
        s.playerListMode.setFromText("blacklist");
        assertSame(PlayerListMode.BLOQUEADOS, s.playerListMode.get());
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
        s.playerListMode.set(PlayerListMode.PERMITIDOS);
        assertTrue(s.listAllows("STEVE"));
        assertFalse(s.listAllows("Alex"));
        s.playerListMode.set(PlayerListMode.BLOQUEADOS);
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
        assertThrows(IllegalArgumentException.class, () -> s.respawn.setFromText("talvez"));
    }

    /** O menu mostra o nome e a descrição (comentário do config.yml) de cada opção. */
    @Test
    void everyOptionHasLabelAndDescription() throws Exception {
        Settings s = new Settings();
        Map<String, String> descriptions;
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml não encontrado");
            descriptions = s.descriptions(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (Setting<?> each : s.all()) {
            assertFalse(each.label().isBlank(), "sem nome: " + each.key());
            String d = descriptions.get(each.path());
            assertNotNull(d, "sem comentário no config.yml: " + each.path());
            assertFalse(d.contains("["), "chave de comando sobrou na descrição: " + d);
        }
        assertEquals("Brilho (contorno visível através das paredes) nos caçadores.", descriptions.get(s.glowHunters.path()));
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
