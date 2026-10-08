package dev.manhuntstar.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A* em mundos sintéticos (sem servidor). */
class PathfindingTest {

    private static final byte STONE = 7;

    /** Mundo de teste: chão de pedra em y=63, ar acima, AVOID fora de -5 ≤ x ≤ 30 e |z| ≤ limite. */
    private static final class Grid implements CostProvider {
        final Map<Long, Byte> overrides = new HashMap<>();
        final int limit;

        Grid(int limit) {
            this.limit = limit;
        }

        void set(int x, int y, int z, byte c) {
            overrides.put(Pos.pack(x, y, z), c);
        }

        @Override
        public byte cost(int x, int y, int z) {
            if (x < -5 || x > 30 || Math.abs(z) > limit || y < 0) {
                return AVOID;
            }
            Byte o = overrides.get(Pos.pack(x, y, z));
            if (o != null) {
                return o;
            }
            return y <= 63 ? STONE : AIR;
        }
    }

    private static PathResult find(CostProvider g, Pos from, Pos to, boolean place) {
        return find(g, from, to, place, false);
    }

    private static PathResult find(CostProvider g, Pos from, Pos to, boolean place, boolean boat) {
        return AStar.get().findPath(g, from, to, 20_000, 2_000, 1.5, place, boat);
    }

    /** Lago de 2 blocos de fundo, com a superfície em y=63. */
    private static void lake(Grid g, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                g.set(x, 63, z, CostProvider.WATER);
                g.set(x, 62, z, CostProvider.WATER);
            }
        }
    }

    @Test
    void walksAcrossFlatGround() {
        Grid g = new Grid(20);
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(10, 64, 0), false);
        assertTrue(r.complete());
        assertEquals(new Pos(0, 64, 0), r.path().getFirst());
        Pos last = r.path().getLast();
        assertTrue(Math.abs(last.x() - 10) <= 1 && last.y() == 64);
        assertTrue(r.toMine().isEmpty());
        assertTrue(r.toPlace().isEmpty());
    }

    @Test
    void goesAroundUnbreakableWall() {
        Grid g = new Grid(20);
        for (int z = -5; z <= 5; z++) {
            for (int y = 64; y <= 67; y++) {
                g.set(5, y, z, CostProvider.UNBREAKABLE);
            }
        }
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(10, 64, 0), false);
        assertTrue(r.complete());
        assertTrue(r.toMine().isEmpty());
        assertTrue(r.path().stream().anyMatch(p -> Math.abs(p.z()) >= 6));
    }

    @Test
    void minesThroughBreakableWallWhenEnclosed() {
        Grid g = new Grid(3);
        for (int z = -3; z <= 3; z++) {
            for (int y = 64; y <= 70; y++) {
                g.set(5, y, z, STONE);
            }
        }
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(10, 64, 0), false);
        assertTrue(r.complete());
        assertFalse(r.toMine().isEmpty());
        assertTrue(r.toMine().stream().allMatch(p -> p.x() == 5), () -> "cavar " + r.toMine() + " caminho " + r.path());
    }

    @Test
    void climbsOneBlockStep() {
        Grid g = new Grid(20);
        for (int x = 5; x <= 12; x++) {
            for (int z = -20; z <= 20; z++) {
                g.set(x, 64, z, STONE);
            }
        }
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(10, 65, 0), false);
        assertTrue(r.complete());
        assertTrue(r.path().stream().anyMatch(p -> p.y() == 65));
        assertTrue(r.toMine().isEmpty());
    }

    @Test
    void placesBlocksOverGapOnlyWhenAllowed() {
        Grid g = new Grid(2);
        for (int z = -2; z <= 2; z++) {
            for (int y = 0; y <= 63; y++) {
                for (int x = 4; x <= 8; x++) {
                    g.set(x, y, z, CostProvider.AIR);
                }
            }
        }
        PathResult without = find(g, new Pos(0, 64, 0), new Pos(12, 64, 0), false);
        assertFalse(without.complete());
        PathResult with = find(g, new Pos(0, 64, 0), new Pos(12, 64, 0), true);
        assertTrue(with.complete(), () -> "caminho " + with.path() + " colocar " + with.toPlace() + " nós " + with.nodesExplored());
        assertFalse(with.toPlace().isEmpty());
    }

    @Test
    void doesNotWalkIntoLava() {
        Grid g = new Grid(20);
        for (int z = -20; z <= 20; z++) {
            g.set(5, 63, z, CostProvider.AVOID);
            g.set(6, 63, z, CostProvider.AVOID);
            g.set(7, 63, z, CostProvider.AVOID);
            g.set(8, 63, z, CostProvider.AVOID);
        }
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(12, 64, 0), false);
        for (Pos p : r.path()) {
            assertFalse(p.x() >= 5 && p.x() <= 8 && p.y() == 64, "pisou na lava em " + p);
        }
    }

    @Test
    void crossesLakeByBoatAndWalksAroundWithout() {
        Grid g = new Grid(20);
        // 21 blocos de travessia; dar a volta pela ponta (|z| >= 9) custa menos que nadar e mais que remar
        lake(g, 4, 24, -8, 8);
        PathResult boat = find(g, new Pos(0, 64, 0), new Pos(28, 64, 0), false, true);
        assertTrue(boat.complete());
        assertTrue(boat.path().stream().filter(p -> p.y() == 63).count() >= 15, () -> "de barco " + boat.path());
        assertTrue(boat.path().stream().allMatch(p -> Math.abs(p.z()) < 9), () -> "de barco " + boat.path());
        PathResult walk = find(g, new Pos(0, 64, 0), new Pos(28, 64, 0), false, false);
        assertTrue(walk.complete());
        assertTrue(walk.path().stream().anyMatch(p -> Math.abs(p.z()) >= 9), () -> "a pé " + walk.path());
        assertTrue(walk.path().stream().noneMatch(p -> p.y() == 63), () -> "a pé " + walk.path());
    }

    @Test
    void boatKeepsOffTheShore() {
        Grid g = new Grid(20);
        // já de barco ao lado da margem (z = 0 é a última fileira de água), alvo em terra lá na frente
        lake(g, -5, 30, 0, 12);
        PathResult r = find(g, new Pos(2, 63, 1), new Pos(28, 64, -2), false, true);
        assertTrue(r.complete(), () -> "caminho " + r.path());
        assertTrue(r.path().stream().filter(p -> p.y() == 63 && p.z() == 0).count() <= 2, () -> "rente à margem " + r.path());
    }

    @Test
    void walksAroundSmallPondEvenWithBoat() {
        Grid g = new Grid(20);
        lake(g, 5, 8, -2, 2);
        PathResult r = find(g, new Pos(0, 64, 0), new Pos(14, 64, 0), false, true);
        assertTrue(r.complete());
        assertTrue(r.path().stream().noneMatch(p -> p.y() == 63), () -> "caminho " + r.path());
    }

    @Test
    void packUnpackRoundTrip() {
        for (Pos p : new Pos[]{new Pos(0, 0, 0), new Pos(-30_000_000, -64, 29_999_999), new Pos(123, 319, -456)}) {
            assertEquals(p, Pos.unpack(p.asLong()));
        }
    }
}
