package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.perception.Sighting.Kind;
import dev.manhuntstar.platform.FakeWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a bot sees of a player: the body, the name through the blocks, the particles, and only in its field of view. */
class VisionTest {

    /** The bot's eyes, on the ground at the origin; yaw 0 looks along +z. */
    private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);

    private static PlayerView player(FakeWorld w, double z, boolean sneaking, boolean invisible, boolean armor, boolean particles) {
        return new PlayerView(UUID.randomUUID(), w, new Vec3(0.5, 64, z), 1.8, 1.62, sneaking, false, invisible, armor, particles);
    }

    private static PlayerView standing(FakeWorld w, double z) {
        return player(w, z, false, false, false, false);
    }

    /** A wall across the line of sight at z = 5, from the floor up to y = 67. */
    private static void wall(FakeWorld w) {
        for (int x = -3; x <= 3; x++) {
            for (int y = 64; y <= 67; y++) {
                w.set(new Pos(x, y, 5), Terrain.STONE);
            }
        }
    }

    @Test
    void seesTheBodyInTheOpen() {
        FakeWorld w = Terrain.flat();
        assertEquals(Kind.BODY, Vision.look(w, EYE, 0, 0, standing(w, 10.5)));
    }

    @Test
    void aWallHidesTheBodyButNotTheNameOfAStandingPlayer() {
        FakeWorld w = Terrain.flat();
        wall(w);
        assertEquals(Kind.NAMETAG, Vision.look(w, EYE, 0, 0, standing(w, 20.5)));
    }

    @Test
    void theNameShowsUpTo64Blocks() {
        FakeWorld w = Terrain.flat();
        wall(w);
        assertEquals(Kind.NAMETAG, Vision.look(w, EYE, 0, 0, standing(w, 60.5)));
        assertNull(Vision.look(w, EYE, 0, 0, standing(w, 70.5)));
    }

    @Test
    void sneakingBehindBlocksLeavesOnlyTheCompass() {
        FakeWorld w = Terrain.flat();
        wall(w);
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 20.5, true, false, false, false)));
    }

    @Test
    void theBodyNeedsFeetChestAndEyesInSight() {
        FakeWorld w = Terrain.flat();
        // one block on the floor halfway: it covers the line to the feet only
        w.set(new Pos(0, 64, 5), Terrain.STONE);
        Kind k = Vision.look(w, EYE, 0, 0, standing(w, 10.5));
        assertNotEquals(Kind.BODY, k);
        assertEquals(Kind.NAMETAG, k);
    }

    @Test
    void invisibilityHidesTheBodyAndTheName() {
        FakeWorld w = Terrain.flat();
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 10.5, false, true, false, false)));
    }

    @Test
    void armorStillShowsOnAnInvisiblePlayerButTheNameDoesNot() {
        FakeWorld w = Terrain.flat();
        assertEquals(Kind.BODY, Vision.look(w, EYE, 0, 0, player(w, 10.5, false, true, true, false)));
        wall(w);
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 20.5, false, true, true, false)), "no name through the wall");
    }

    @Test
    void theInvisibilityParticlesShowUpTo32BlocksWithNothingInTheWay() {
        FakeWorld w = Terrain.flat();
        assertEquals(Kind.PARTICLES, Vision.look(w, EYE, 0, 0, player(w, 20.5, false, true, false, true)));
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 40.5, false, true, false, true)), "too far for the particles");
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 20.5, false, true, false, false)), "an effect given without particles");
        wall(w);
        assertNull(Vision.look(w, EYE, 0, 0, player(w, 20.5, false, true, false, true)), "particles don't show through blocks");
    }

    @Test
    void onlyWhatIsInTheFieldOfView() {
        FakeWorld w = Terrain.flat();
        // looking the other way (yaw 180 is -z)
        assertNull(Vision.look(w, EYE, 180, 0, standing(w, 10.5)));
        assertNull(Vision.look(w, EYE, 0, 0, new PlayerView(UUID.randomUUID(), w, new Vec3(12.5, 64, 2.5), 1.8, 1.62,
                false, false, false, false, false)), "far to the side");
        // right next to the bot it doesn't matter where it looks
        assertEquals(Kind.BODY, Vision.look(w, EYE, 180, 0, standing(w, 1.5)));
    }

    @Test
    void theGuessFromTheNameIsOffByAtMostItsError() {
        Vec3 feet = new Vec3(0.5, 64, 40.5);
        double d = feet.subtract(EYE).length();
        for (double[] n : new double[][]{{1, 1, 1}, {-1, -1, -1}, {0.3, -0.7, 0}}) {
            Vec3 guess = Vision.estimate(EYE, feet, Kind.NAMETAG, n);
            assertTrue(guess.distanceTo(feet) <= PerceptionTuning.NAMETAG_DEPTH_ERROR * d + PerceptionTuning.NAMETAG_SIDE_ERROR + 1.0E-9);
        }
        double[] same = {0.5, 0.5, 0.5};
        assertEquals(Vision.estimate(EYE, feet, Kind.NAMETAG, same), Vision.estimate(EYE, feet, Kind.NAMETAG, same),
                "the same noise, the same guess: it doesn't jump around");
        assertEquals(feet, Vision.estimate(EYE, feet, Kind.BODY, same), "the body is the real position");
    }
}
