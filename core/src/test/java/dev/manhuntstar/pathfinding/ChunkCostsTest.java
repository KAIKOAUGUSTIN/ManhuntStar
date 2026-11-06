package dev.manhuntstar.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.BlockSnapshot;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Block costs from the types the platform registers, and the chunk copies used by the A*. */
class ChunkCostsTest {

    private static BlockType air;
    private static BlockType stone;
    private static BlockType sign;

    @BeforeAll
    static void registerTypes() {
        air = BlockType.register(0, "AIR", 0, BlockTrait.AIR, BlockTrait.REPLACEABLE);
        stone = BlockType.register(1, "STONE", 1.5f, BlockTrait.SOLID);
        BlockType.register(2, "LAVA", 100, BlockTrait.REPLACEABLE);
        BlockType.register(3, "WATER", 100, BlockTrait.REPLACEABLE);
        BlockType.register(4, "OAK_DOOR", 3, BlockTrait.SOLID, BlockTrait.DOOR);
        BlockType.register(5, "IRON_DOOR", 5, BlockTrait.SOLID, BlockTrait.DOOR);
        BlockType.register(6, "SHORT_GRASS", 0, BlockTrait.REPLACEABLE);
        BlockType.register(7, "BEDROCK", -1, BlockTrait.SOLID);
        BlockType.register(8, "OBSIDIAN", 50, BlockTrait.SOLID);
        BlockType.register(9, "REINFORCED_DEEPSLATE", 55, BlockTrait.SOLID);
        sign = BlockType.register(10, "OAK_SIGN", 1, BlockTrait.WATERLOGGABLE);
        BlockType.register(11, "OAK_FENCE_GATE", 2, BlockTrait.SOLID, BlockTrait.FENCE_GATE);
        BlockType.register(12, "IRON_TRAPDOOR", 5, BlockTrait.SOLID, BlockTrait.TRAPDOOR);
        BlockCosts.init();
    }

    private static byte cost(String name) {
        return BlockCosts.cost(BlockType.of(name).id());
    }

    @Test
    void classification() {
        assertEquals(CostProvider.AIR, cost("AIR"));
        assertEquals(CostProvider.AVOID, cost("LAVA"));
        assertEquals(CostProvider.WATER, cost("WATER"));
        assertEquals(CostProvider.AIR, cost("OAK_DOOR"), "doors open");
        assertEquals(CostProvider.AIR, cost("OAK_FENCE_GATE"), "gates open");
        assertEquals(14, cost("IRON_DOOR"), "iron doors don't open: 4 + 5·2");
        assertEquals(14, cost("IRON_TRAPDOOR"));
        assertEquals(CostProvider.AIR, cost("SHORT_GRASS"));
        assertEquals(7, cost("STONE"), "4 + 1.5·2");
        assertEquals(CostProvider.UNBREAKABLE, cost("BEDROCK"));
        assertEquals(104, cost("OBSIDIAN"));
        assertEquals(114, cost("REINFORCED_DEEPSLATE"));
        assertEquals(CostProvider.AIR, BlockCosts.cost(999), "not a block");
        assertEquals(CostProvider.AIR, BlockCosts.cost(-1));
        assertTrue(BlockCosts.waterloggable(sign.id()));
    }

    /** A chunk: stone up to y=63, air above, and a waterlogged sign at (3, 64, 3). */
    private static final class Chunk implements BlockSnapshot {
        @Override
        public int typeId(int x, int y, int z) {
            if (x == 3 && y == 64 && z == 3) {
                return sign.id();
            }
            return y <= 63 ? stone.id() : air.id();
        }

        @Override
        public boolean waterlogged(int x, int y, int z) {
            return x == 3 && y == 64 && z == 3;
        }
    }

    private static final class World implements GameWorld {
        final UUID id = UUID.randomUUID();
        final Set<Long> loaded = new HashSet<>();
        final Map<Long, Integer> copies = new HashMap<>();

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public int minHeight() {
            return 0;
        }

        @Override
        public int maxHeight() {
            return 256;
        }

        @Override
        public boolean isChunkLoaded(int cx, int cz) {
            return loaded.contains(Pos.pack(cx, 0, cz));
        }

        @Override
        public BlockSnapshot chunkSnapshot(int cx, int cz) {
            copies.merge(Pos.pack(cx, 0, cz), 1, Integer::sum);
            return new Chunk();
        }
    }

    private static final class Server implements GameServer {
        int tick;
        GameWorld world;

        @Override
        public int currentTick() {
            return tick;
        }

        @Override
        public GameWorld world(UUID id) {
            return world != null && world.id().equals(id) ? world : null;
        }
    }

    @Test
    void gridReadsCopiesAndReusesThem() {
        World world = new World();
        world.loaded.add(Pos.pack(0, 0, 0));
        Server server = new Server();
        server.world = world;
        ChunkSnapshotCache cache = new ChunkSnapshotCache(server);

        SnapshotGrid grid = cache.capture(world, 8, 8, 4);
        assertEquals(7, grid.cost(5, 63, 5));
        assertEquals(CostProvider.AIR, grid.cost(5, 64, 5));
        assertEquals(CostProvider.WATER, grid.cost(3, 64, 3), "waterlogged sign");
        assertEquals(CostProvider.AVOID, grid.cost(-1, 64, 5), "outside the copied area");
        assertEquals(CostProvider.AVOID, grid.cost(5, -1, 5), "below the world");
        assertEquals(CostProvider.AIR, grid.cost(5, 300, 5), "above the world");

        // a second bot nearby reuses the copy; it's taken again once it's too old or a block changed
        cache.capture(world, 8, 8, 4);
        assertEquals(1, world.copies.get(Pos.pack(0, 0, 0)));
        server.tick = 41;
        cache.capture(world, 8, 8, 4);
        assertEquals(2, world.copies.get(Pos.pack(0, 0, 0)));
        cache.invalidate(world, 5, 5);
        cache.capture(world, 8, 8, 4);
        assertEquals(3, world.copies.get(Pos.pack(0, 0, 0)));
    }

    @Test
    void unloadedChunksAreAvoided() {
        World world = new World();
        world.loaded.add(Pos.pack(0, 0, 0));
        Server server = new Server();
        server.world = world;
        ChunkSnapshotCache cache = new ChunkSnapshotCache(server);
        SnapshotGrid grid = cache.capture(world, 15, 8, 4);
        assertEquals(7, grid.cost(15, 63, 8));
        assertEquals(CostProvider.AVOID, grid.cost(16, 63, 8), "chunk (1, 0) isn't loaded");
        assertNotNull(grid);
    }

    @Test
    void purgeDropsWorldsThatAreGone() {
        World world = new World();
        world.loaded.add(Pos.pack(0, 0, 0));
        Server server = new Server();
        server.world = world;
        ChunkSnapshotCache cache = new ChunkSnapshotCache(server);
        cache.capture(world, 8, 8, 4);
        server.world = null;
        cache.purge();
        server.world = world;
        cache.capture(world, 8, 8, 4);
        assertEquals(2, world.copies.get(Pos.pack(0, 0, 0)), "the old copy was thrown away with the world");
    }
}
