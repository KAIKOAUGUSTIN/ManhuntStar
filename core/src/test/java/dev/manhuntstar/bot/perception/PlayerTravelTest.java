package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.FakeWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The players' trips between worlds: through a portal or not, where they left and where they came out. */
class PlayerTravelTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void aTripThroughAPortal() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        overworld.set(new Pos(10, 64, 3), Terrain.NETHER_PORTAL);
        PlayerTravel travel = new PlayerTravel();
        assertNull(travel.record(PLAYER, overworld, new Vec3(10.4, 64, 3.6), 1));
        PlayerTravel.Departure d = travel.record(PLAYER, nether, new Vec3(1.5, 70, 0.5), 2);
        assertNotNull(d);
        assertTrue(d.viaPortal());
        assertEquals(new Pos(10, 64, 3), d.portal());
        assertEquals(d, travel.departureFrom(PLAYER, overworld.id()));
        assertEquals(d, travel.arrivalIn(PLAYER, nether.id()));
        assertNull(travel.departureFrom(PLAYER, nether.id()));
    }

    @Test
    void aTripWithoutAPortal() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        PlayerTravel travel = new PlayerTravel();
        travel.record(PLAYER, nether, new Vec3(0.5, 40, 0.5), 1);
        PlayerTravel.Departure d = travel.record(PLAYER, overworld, new Vec3(100.5, 70, 0.5), 2);
        assertFalse(d.viaPortal(), "died in the Nether and came back at their bed");
    }

    @Test
    void aNewHuntStartsWithoutThePositionsOfTheLastOne() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        overworld.set(new Pos(10, 64, 3), Terrain.NETHER_PORTAL);
        PlayerTravel travel = new PlayerTravel();
        travel.record(PLAYER, overworld, new Vec3(10.5, 64, 3.5), 1);
        travel.clear();
        assertNull(travel.record(PLAYER, nether, new Vec3(1.5, 70, 0.5), 2), "sent to the new hunt's world");
    }

    @Test
    void aGapInTheTicksIsNoTrip() {
        FakeWorld overworld = Terrain.flat();
        FakeWorld nether = Terrain.flat();
        PlayerTravel travel = new PlayerTravel();
        travel.record(PLAYER, overworld, new Vec3(0.5, 64, 0.5), 1);
        assertNull(travel.record(PLAYER, nether, new Vec3(0.5, 64, 0.5), 5), "left and joined again elsewhere");
    }

    @Test
    void theFirstTripOfTheMatchStandsInForTheAdvancement() {
        FirstTrips trips = new FirstTrips();
        assertTrue(trips.first(PLAYER, Dimension.NETHER));
        assertFalse(trips.first(PLAYER, Dimension.NETHER), "only the first one");
        assertTrue(trips.first(PLAYER, Dimension.THE_END));
        FirstTrips loaded = new FirstTrips();
        loaded.load(trips.save());
        assertFalse(loaded.first(PLAYER, Dimension.NETHER), "kept across a server restart");
        loaded.clear();
        assertTrue(loaded.first(PLAYER, Dimension.NETHER), "a new match");
    }
}
