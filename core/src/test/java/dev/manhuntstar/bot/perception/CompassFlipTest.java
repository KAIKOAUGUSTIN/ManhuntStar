package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.util.Vec3;
import org.junit.jupiter.api.Test;

/** The bot notices that it walked over the target when the compass turns around, and only then. */
class CompassFlipTest {

    /** Walks along +x at sprint speed from x = 0 to 40, with the target at (x, y, z); returns where it noticed passing. */
    private static Vec3 walkPast(double tx, double ty, double tz) {
        CompassFlip flip = new CompassFlip();
        Vec3 target = new Vec3(tx, ty, tz);
        Vec3 passed = null;
        for (int tick = 0; tick <= 145 && passed == null; tick++) {
            Vec3 pos = new Vec3(tick * 0.28, 64, 0);
            passed = flip.read(pos, Compass.bearing(pos, target), tick);
        }
        return passed;
    }

    @Test
    void walkingOverAPlayerUndergroundIsNoticed() {
        Vec3 passed = walkPast(20.3, 30, 0.6);
        assertNotNull(passed);
        assertTrue(Math.abs(passed.x() - 20.3) <= PerceptionTuning.FLIP_MAX_TRAVEL, "close to where they are: " + passed);
    }

    @Test
    void passingFarToTheSideIsNot() {
        assertNull(walkPast(20, 64, 15));
    }

    @Test
    void rightAboveIsRightThere() {
        CompassFlip flip = new CompassFlip();
        Vec3 pos = new Vec3(5, 64, 5);
        assertEquals(pos, flip.read(pos, Vec3.ZERO, 0));
        assertNull(flip.read(pos, null, 1), "the compass spins: nothing to compare");
    }
}
