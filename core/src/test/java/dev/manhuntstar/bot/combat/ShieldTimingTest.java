package dev.manhuntstar.bot.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShieldTimingTest {

    /** An axe's weapon component: disable_blocking_for_seconds 5. */
    private static final double AXE = 5.0;

    @Test
    void aRaisedShieldBlocksOnlyAfterAQuarterOfASecond() {
        ShieldTiming shield = new ShieldTiming();
        shield.raised(1000);
        for (long tick = 1000; tick < 1000 + ShieldTiming.BLOCK_DELAY_TICKS; tick++) {
            assertFalse(shield.blocks(tick), "blocked " + (tick - 1000) + " ticks after being raised");
        }
        assertTrue(shield.blocks(1005), "5 ticks after being raised");
        assertTrue(shield.blocks(1200));
        shield.raised(1300);
        assertFalse(shield.blocks(1301), "raised again: the delay starts over");
    }

    @Test
    void anAxeTakesTheShieldDownForFiveSeconds() {
        ShieldTiming shield = new ShieldTiming();
        assertFalse(shield.disabled(0));
        assertTrue(shield.disable(500, AXE));
        for (long tick = 500; tick < 600; tick++) {
            assertTrue(shield.disabled(tick), "raised again " + (tick - 500) + " ticks after the axe");
        }
        assertFalse(shield.disabled(600), "100 ticks later it can be raised");
    }

    @Test
    void aWeaponThatDoesNotDisableShieldsLeavesItUp() {
        ShieldTiming shield = new ShieldTiming();
        assertFalse(shield.disable(500, 0.0), "a sword");
        assertFalse(shield.disabled(500));
        assertFalse(shield.disable(500, 0.01), "less than a tick rounds to nothing, as in the game");
        assertFalse(shield.disabled(500));
    }

    @Test
    void anotherAxeHitNeverShortensIt() {
        ShieldTiming shield = new ShieldTiming();
        shield.disable(500, AXE);
        shield.disable(520, 1.0);
        assertTrue(shield.disabled(599), "a shorter disable later doesn't end the first one early");
        shield.disable(590, AXE);
        assertTrue(shield.disabled(689), "a new axe hit takes it down for its own 5 s");
        assertFalse(shield.disabled(690));
    }
}
