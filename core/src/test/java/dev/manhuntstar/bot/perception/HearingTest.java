package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.FakeWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What the bots hear: short range, farther when the player sprints, and wool muffles it. */
class HearingTest {

    private static final Vec3 EAR = new Vec3(0.5, 65.6, 0.5);

    private static Vec3 at(double x) {
        return new Vec3(x, 64.1, 0.5);
    }

    @Test
    void shortRangeAndFartherWhenSprinting() {
        FakeWorld w = Terrain.flat();
        assertTrue(Hearing.hears(w, EAR, at(7.5), false));
        assertFalse(Hearing.hears(w, EAR, at(9.5), false));
        assertTrue(Hearing.hears(w, EAR, at(15.5), true));
        assertFalse(Hearing.hears(w, EAR, at(17.5), true));
    }

    @Test
    void woolInBetweenStopsIt() {
        FakeWorld w = Terrain.flat();
        for (int y = 63; y <= 67; y++) {
            for (int z = -2; z <= 2; z++) {
                w.set(new Pos(3, y, z), Terrain.WOOL);
            }
        }
        assertFalse(Hearing.hears(w, EAR, at(6.5), false));
        // stone doesn't stop vibrations
        for (int y = 63; y <= 67; y++) {
            for (int z = -2; z <= 2; z++) {
                w.set(new Pos(3, y, z), Terrain.STONE);
            }
        }
        assertTrue(Hearing.hears(w, EAR, at(6.5), false));
    }

    @Test
    void theWoolTheSoundComesFromDoesNotCount() {
        FakeWorld w = Terrain.flat();
        w.set(new Pos(6, 64, 0), Terrain.WOOL);
        assertTrue(Hearing.hears(w, EAR, at(6.5), false));
    }

    @Test
    void oneSoundPerPlayerEveryFewTicks() {
        Hearing.Throttle t = new Hearing.Throttle();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(t.allow(a, 100));
        assertFalse(t.allow(a, 100 + PerceptionTuning.SOUND_INTERVAL_TICKS - 1));
        assertTrue(t.allow(b, 101), "each player has their own");
        assertTrue(t.allow(a, 100 + PerceptionTuning.SOUND_INTERVAL_TICKS));
    }
}
