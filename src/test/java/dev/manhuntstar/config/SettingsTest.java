package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.bot.Modes.TargetMode;
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
        assertSame(s.nodeCap, s.byKey("nodes"));
        assertSame(s.nodeCap, s.byKey("astar nodes"));
        assertSame(s.mobsAttackBots, s.byKey("MobTarget"));
        assertSame(s.minNetherDelay, s.byKey("min_sec_range_nether_entering"));
        assertSame(s.startCountdown, s.byKey("starttime"));
        assertSame(s.glowHunters, s.byKey("glowing"));
        assertNotNull(s.byKey("allowhorseriding"));
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

    @Test
    void difficultyAliases() {
        assertSame(BotDifficulty.HARD, BotDifficulty.parse("difficulty.hard"));
        assertSame(BotDifficulty.EASY, BotDifficulty.parse("fácil"));
        assertSame(BotDifficulty.TERMINATOR, BotDifficulty.parse("TERMINATOR"));
        assertEquals(8, BotDifficulty.TERMINATOR.startPhase());
        assertEquals(600 * 20, BotDifficulty.TERMINATOR.phaseDurationTicks());
    }
}
