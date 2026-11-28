package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.world.PlayerBlockTracker;
import dev.manhuntstar.platform.FakeWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A tall tower of player blocks is recognized, and seen from afar in the compass's direction. */
class TowerSpotterTest {

    private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);

    /** A pillar of {@code height} player blocks on the ground at (x, z). */
    private static void pillar(FakeWorld w, PlayerBlockTracker t, int x, int z, int height) {
        for (int y = 64; y < 64 + height; y++) {
            Pos p = new Pos(x, y, z);
            w.set(p, Terrain.STONE);
            t.markPlaced(w, p);
        }
    }

    @Test
    void onlyATallColumnIsATower() {
        FakeWorld w = Terrain.flat();
        PlayerBlockTracker t = new PlayerBlockTracker();
        pillar(w, t, 0, 100, PerceptionTuning.TOWER_MIN_BLOCKS - 1);
        assertTrue(t.towers(w).isEmpty());
        t.markPlaced(w, new Pos(0, 64 + PerceptionTuning.TOWER_MIN_BLOCKS - 1, 100));
        assertEquals(List.of(new Pos(0, 64 + PerceptionTuning.TOWER_MIN_BLOCKS - 1, 100)), List.copyOf(t.towers(w)));
        t.unmark(w, new Pos(0, 66, 100));
        assertTrue(t.towers(w).isEmpty(), "broken below the top: not one on top of the other anymore");
    }

    @Test
    void seenFromAfarInTheCompassDirection() {
        FakeWorld w = Terrain.flat();
        PlayerBlockTracker t = new PlayerBlockTracker();
        pillar(w, t, 0, 100, 30);
        Vec3 ahead = new Vec3(0, 0, 1);
        Pos top = new Pos(0, 93, 100);
        assertEquals(top, TowerSpotter.spot(w, EYE, 0, -10, ahead, t.towers(w)));
        assertNull(TowerSpotter.spot(w, EYE, 0, -10, new Vec3(1, 0, 0), t.towers(w)), "the compass points elsewhere");
        assertNull(TowerSpotter.spot(w, EYE, 180, -10, ahead, t.towers(w)), "looking the other way");
    }

    @Test
    void notBehindAHillNorTooFar() {
        FakeWorld w = Terrain.flat();
        PlayerBlockTracker t = new PlayerBlockTracker();
        pillar(w, t, 0, 100, 30);
        for (int x = -3; x <= 3; x++) {
            for (int y = 64; y <= 110; y++) {
                w.set(new Pos(x, y, 10), Terrain.STONE);
            }
        }
        assertNull(TowerSpotter.spot(w, EYE, 0, -10, new Vec3(0, 0, 1), t.towers(w)));
        FakeWorld open = Terrain.flat();
        PlayerBlockTracker far = new PlayerBlockTracker();
        pillar(open, far, 0, 200, 30);
        assertNull(TowerSpotter.spot(open, EYE, 0, -5, new Vec3(0, 0, 1), far.towers(open)));
    }
}
