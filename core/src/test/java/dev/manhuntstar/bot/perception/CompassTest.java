package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.manhuntstar.platform.FakeWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The compass gives only the horizontal direction, and in another world points at the portal the player took. */
class CompassTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void onlyTheHorizontalDirection() {
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 up = Compass.bearing(from, new Vec3(10, 120, 0));
        Vec3 down = Compass.bearing(from, new Vec3(10, -40, 0));
        assertEquals(new Vec3(1, 0, 0), up);
        assertEquals(up, down, "a skybase or a cave, the compass is the same");
        assertEquals(Vec3.ZERO, Compass.bearing(from, new Vec3(0.1, 20, 0.1)), "right above or below");
    }

    @Test
    void inAnotherWorldItPointsAtThePortalThePlayerTook() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        overworld.set(new Pos(30, 64, 0), Terrain.NETHER_PORTAL);
        PlayerTravel travel = new PlayerTravel();
        travel.record(PLAYER, overworld, new Vec3(30.5, 64, 0.5), false, 10);
        travel.record(PLAYER, nether, new Vec3(4.5, 70, 0.5), false, 11);

        Vec3 here = Compass.pointsAt(nether.id(), PLAYER, nether.id(), new Vec3(4.5, 70, 0.5), travel);
        assertEquals(new Vec3(4.5, 70, 0.5), here, "same world: the player");
        Vec3 there = Compass.pointsAt(overworld.id(), PLAYER, nether.id(), new Vec3(4.5, 70, 0.5), travel);
        assertEquals(Vec3.bottomCenter(new Pos(30, 64, 0)), there, "the portal they went through");
    }

    @Test
    void withoutAPortalItSpins() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        PlayerTravel travel = new PlayerTravel();
        // died in the overworld and came back in the Nether (no portal there)
        travel.record(PLAYER, overworld, new Vec3(30.5, 64, 0.5), false, 10);
        travel.record(PLAYER, nether, new Vec3(4.5, 70, 0.5), false, 11);
        assertNull(Compass.pointsAt(overworld.id(), PLAYER, nether.id(), new Vec3(4.5, 70, 0.5), travel));
        assertNull(Compass.pointsAt(UUID.randomUUID(), PLAYER, nether.id(), new Vec3(4.5, 70, 0.5), travel), "never was there");
    }
}
