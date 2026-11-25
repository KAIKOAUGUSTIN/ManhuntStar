package dev.manhuntstar.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTrailTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final Object OVERWORLD = "overworld";
    private static final Object NETHER = "nether";

    private static Vec3 at(double x) {
        return new Vec3(x, 64, 0);
    }

    @Test
    void givesThePositionOfEachEarlierTick() {
        PlayerTrail trail = new PlayerTrail();
        for (int tick = 100; tick <= 110; tick++) {
            trail.record(PLAYER, OVERWORLD, at(tick), tick);
        }
        assertEquals(at(110), trail.at(PLAYER, 0, 110));
        assertEquals(at(107), trail.at(PLAYER, 3, 110));
        assertEquals(at(110 - PlayerTrail.LENGTH + 1), trail.at(PLAYER, PlayerTrail.LENGTH - 1, 110));
        assertNull(trail.at(PLAYER, PlayerTrail.LENGTH, 110), "older than what is kept");
    }

    @Test
    void aPlayerJustSeenHasNoEarlierPosition() {
        PlayerTrail trail = new PlayerTrail();
        trail.record(PLAYER, OVERWORLD, at(0), 50);
        trail.record(PLAYER, OVERWORLD, at(1), 51);
        assertEquals(at(0), trail.at(PLAYER, 1, 51));
        assertNull(trail.at(PLAYER, 3, 51), "only 2 ticks recorded");
        assertNull(trail.at(UUID.randomUUID(), 0, 51), "never recorded");
    }

    @Test
    void aMissedTickOrAnotherWorldStartsOver() {
        PlayerTrail trail = new PlayerTrail();
        for (int tick = 0; tick < 5; tick++) {
            trail.record(PLAYER, OVERWORLD, at(tick), tick);
        }
        trail.record(PLAYER, OVERWORLD, at(9), 9);
        assertNull(trail.at(PLAYER, 1, 9), "ticks 5 to 8 weren't recorded");
        trail.record(PLAYER, NETHER, at(10), 10);
        assertNull(trail.at(PLAYER, 1, 10), "the position before was in another world");
        assertEquals(at(10), trail.at(PLAYER, 0, 10));
    }

    @Test
    void onlyTheCurrentTickCounts() {
        PlayerTrail trail = new PlayerTrail();
        for (int tick = 0; tick < 5; tick++) {
            trail.record(PLAYER, OVERWORLD, at(tick), tick);
        }
        assertNull(trail.at(PLAYER, 0, 5), "tick 5 wasn't recorded yet");
        trail.record(PLAYER, OVERWORLD, at(4.5), 4);
        assertEquals(at(4.5), trail.at(PLAYER, 0, 4), "recorded twice on one tick: the newest stands");
        assertEquals(at(1), trail.at(PLAYER, 3, 4));
    }
}
