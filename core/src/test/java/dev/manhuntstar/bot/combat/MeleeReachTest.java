package dev.manhuntstar.bot.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Vec3;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MeleeReachTest {

    private static final double EYE = 1.62;
    private static final double REACH = MeleeReach.PLAYER_REACH;
    /** Grid of the brute-force search: points every this many blocks on the box's faces. */
    private static final int STEPS = 24;

    /** Hitboxes the bots hit: player standing, sneaking and swimming, spider, slimes, a big one. */
    private static final double[][] SIZES = {{0.6, 1.8}, {0.6, 1.5}, {0.6, 0.6}, {1.4, 0.9}, {0.52, 0.52}, {1.04, 1.04}, {2.08, 2.08}, {1.4, 2.7}};

    private static Box hitbox(double x, double y, double z, double width, double height) {
        return new Box(x - width / 2, y, z - width / 2, x + width / 2, y + height, z + width / 2);
    }

    /**
     * Independent of {@link Box#distanceTo}: the shortest distance from the point to a grid of points over the six
     * faces of the box (or 0 when the point is inside). It is never shorter than the real distance, and at most
     * {@link #gridError} longer.
     */
    private static double bruteForce(Vec3 p, Box b) {
        if (p.x() >= b.minX() && p.x() <= b.maxX() && p.y() >= b.minY() && p.y() <= b.maxY() && p.z() >= b.minZ() && p.z() <= b.maxZ()) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= STEPS; i++) {
            for (int j = 0; j <= STEPS; j++) {
                double u = (double) i / STEPS;
                double v = (double) j / STEPS;
                double x = b.minX() + u * (b.maxX() - b.minX());
                double y = b.minY() + v * (b.maxY() - b.minY());
                double y2 = b.minY() + u * (b.maxY() - b.minY());
                double z = b.minZ() + v * (b.maxZ() - b.minZ());
                double z2 = b.minZ() + u * (b.maxZ() - b.minZ());
                double x2 = b.minX() + v * (b.maxX() - b.minX());
                best = Math.min(best, dist(p, x, y, b.minZ()));
                best = Math.min(best, dist(p, x, y, b.maxZ()));
                best = Math.min(best, dist(p, b.minX(), y2, z));
                best = Math.min(best, dist(p, b.maxX(), y2, z));
                best = Math.min(best, dist(p, x2, b.minY(), z2));
                best = Math.min(best, dist(p, x2, b.maxY(), z2));
            }
        }
        return best;
    }

    private static double gridError(Box b) {
        double sx = (b.maxX() - b.minX()) / STEPS;
        double sy = (b.maxY() - b.minY()) / STEPS;
        double sz = (b.maxZ() - b.minZ()) / STEPS;
        return Math.sqrt(sx * sx + sy * sy + sz * sz) / 2 + 1e-9;
    }

    private static double dist(Vec3 p, double x, double y, double z) {
        double dx = p.x() - x;
        double dy = p.y() - y;
        double dz = p.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void distanceToTheHitboxMatchesABruteForceSearch() {
        Random r = new Random(1);
        for (int n = 0; n < 4000; n++) {
            double[] size = SIZES[r.nextInt(SIZES.length)];
            Box box = hitbox(r.nextDouble() * 10 - 5, r.nextDouble() * 6 - 3, r.nextDouble() * 10 - 5, size[0], size[1]);
            Vec3 eyes = new Vec3(r.nextDouble() * 4 - 2, r.nextDouble() * 4 - 2 + EYE, r.nextDouble() * 4 - 2);
            double d = box.distanceTo(eyes);
            double brute = bruteForce(eyes, box);
            assertTrue(d <= brute + 1e-9, "farther than a point of the box: " + d + " > " + brute);
            assertTrue(brute - d <= gridError(box), "no point of the box that close: " + d + " vs " + brute);
        }
    }

    @Test
    void reachesExactlyTheHitboxesThePlayerReaches() {
        Random r = new Random(2);
        int checked = 0;
        for (int n = 0; n < 6000; n++) {
            double[] size = SIZES[r.nextInt(SIZES.length)];
            // around the edge of the reach: up to 4.5 blocks away horizontally, 3 above or below
            double angle = r.nextDouble() * Math.PI * 2;
            double horizontal = r.nextDouble() * 4.5;
            Box box = hitbox(Math.cos(angle) * horizontal, r.nextDouble() * 6 - 3, Math.sin(angle) * horizontal, size[0], size[1]);
            Vec3 eyes = new Vec3(0, EYE, 0);
            double brute = bruteForce(eyes, box);
            double error = gridError(box);
            if (Math.abs(brute - REACH) <= error) {
                continue; // too close to the edge for the grid to tell
            }
            checked++;
            assertEquals(brute <= REACH, MeleeReach.reaches(eyes, box, REACH),
                    "the player's rule says " + (brute <= REACH) + " at " + brute + " from the eyes (" + size[0] + "x" + size[1] + ")");
        }
        assertTrue(checked > 5000, "too few cases checked: " + checked);
    }

    @Test
    void neverReachesFartherThanThePlayer() {
        Random r = new Random(3);
        for (int n = 0; n < 20000; n++) {
            double[] size = SIZES[r.nextInt(SIZES.length)];
            Box box = hitbox(r.nextDouble() * 12 - 6, r.nextDouble() * 8 - 4, r.nextDouble() * 12 - 6, size[0], size[1]);
            Vec3 eyes = new Vec3(r.nextDouble() - 0.5, EYE + r.nextDouble() - 0.5, r.nextDouble() - 0.5);
            Vec3 seenAt = new Vec3(r.nextDouble() * 4 - 2, r.nextDouble() * 2 - 1, r.nextDouble() * 4 - 2);
            Vec3 position = new Vec3((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
            double brute = bruteForce(eyes, box);
            if (MeleeReach.reaches(eyes, box, REACH)) {
                assertTrue(brute <= REACH + gridError(box), "reached a hitbox " + brute + " away");
            }
            if (MeleeReach.reachesPlayer(eyes, box, position, position.add(seenAt), REACH)) {
                assertTrue(MeleeReach.reaches(eyes, box, REACH), "the delay made the reach longer");
            }
        }
    }

    @Test
    void playersHitboxOnFlatGround() {
        Vec3 eyes = new Vec3(0, EYE, 0);
        // straight ahead the hitbox's face is 0.3 before its center: 3.3 between the centers
        assertTrue(MeleeReach.reaches(eyes, hitbox(3.3 - 1e-6, 0, 0, 0.6, 1.8), REACH));
        assertFalse(MeleeReach.reaches(eyes, hitbox(3.3 + 1e-6, 0, 0, 0.6, 1.8), REACH));
        // diagonally its corner is 0.42 before its center
        double diagonal = (3.0 + 0.3 * Math.sqrt(2)) / Math.sqrt(2);
        assertTrue(MeleeReach.reaches(eyes, hitbox(diagonal - 1e-6, 0, diagonal - 1e-6, 0.6, 1.8), REACH));
        assertFalse(MeleeReach.reaches(eyes, hitbox(diagonal + 1e-6, 0, diagonal + 1e-6, 0.6, 1.8), REACH));
        // with the target 2 blocks lower the top of its head is 1.82 below the eyes
        double below = Math.sqrt(9 - 1.82 * 1.82) + 0.3;
        assertTrue(MeleeReach.reaches(eyes, hitbox(below - 1e-6, -2, 0, 0.6, 1.8), REACH));
        assertFalse(MeleeReach.reaches(eyes, hitbox(below + 1e-6, -2, 0, 0.6, 1.8), REACH));
    }

    @Test
    void aPlayerStandingStillIsReachedAsAnyHitbox() {
        Vec3 eyes = new Vec3(0, EYE, 0);
        for (double x = 2.0; x <= 4.0; x += 0.05) {
            Box box = hitbox(x, 0, 0, 0.6, 1.8);
            Vec3 position = new Vec3(x, 0, 0);
            assertEquals(MeleeReach.reaches(eyes, box, REACH), MeleeReach.reachesPlayer(eyes, box, position, position, REACH), "at " + x);
        }
    }

    @Test
    void aPlayerRunningAtTheBotIsHitOnlyOnceSeenWithinReach() {
        Vec3 eyes = new Vec3(0, EYE, 0);
        double run = 0.28 * MeleeReach.VIEW_DELAY_TICKS;
        // within reach now, but 3 ticks before (what a person attacking would see) it was out of it
        Box now = hitbox(3.2, 0, 0, 0.6, 1.8);
        assertTrue(MeleeReach.reaches(eyes, now, REACH));
        assertFalse(MeleeReach.reachesPlayer(eyes, now, new Vec3(3.2, 0, 0), new Vec3(3.2 + run, 0, 0), REACH));
        // close enough that it was within reach 3 ticks before too
        Box closer = hitbox(3.3 - run - 0.01, 0, 0, 0.6, 1.8);
        assertTrue(MeleeReach.reachesPlayer(eyes, closer, new Vec3(3.3 - run - 0.01, 0, 0), new Vec3(3.3 - 0.01, 0, 0), REACH));
    }

    @Test
    void aPlayerRunningAwayIsNotReachedFarther() {
        Vec3 eyes = new Vec3(0, EYE, 0);
        double run = 0.28 * MeleeReach.VIEW_DELAY_TICKS;
        // 3 ticks before the player was close, but now they are out of reach
        Box gone = hitbox(3.4, 0, 0, 0.6, 1.8);
        assertFalse(MeleeReach.reachesPlayer(eyes, gone, new Vec3(3.4, 0, 0), new Vec3(3.4 - run, 0, 0), REACH));
    }

    @Test
    void anUnknownOrFarEarlierPositionIsNotHit() {
        Vec3 eyes = new Vec3(0, EYE, 0);
        Box box = hitbox(1.0, 0, 0, 0.6, 1.8);
        assertFalse(MeleeReach.reachesPlayer(eyes, box, new Vec3(1.0, 0, 0), null, REACH), "just appeared");
        assertFalse(MeleeReach.reachesPlayer(eyes, box, new Vec3(1.0, 0, 0), new Vec3(40, 0, 0), REACH), "just teleported next to the bot");
    }
}
