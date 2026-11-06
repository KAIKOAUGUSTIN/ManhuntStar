package dev.manhuntstar.pathfinding;

import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Cópias de chunk usadas pelo pathfinding (só na thread principal). Cada cópia vale por
 * {@link #TTL_TICKS} ticks ou até um bloco dela mudar; vários bots perto um do outro
 * reaproveitam as mesmas cópias.
 */
final class ChunkSnapshotCache {

    private static final int TTL_TICKS = 40;
    private static final int PURGE_AGE_TICKS = 200;

    private final GameServer server;
    private final Map<UUID, Map<Long, CostChunk>> worlds = new HashMap<>();

    ChunkSnapshotCache(GameServer server) {
        this.server = server;
    }

    /** Captura (ou reaproveita) os chunks carregados num raio em blocos em volta do centro. */
    SnapshotGrid capture(GameWorld world, int centerX, int centerZ, int radiusBlocks) {
        long now = server.currentTick();
        Map<Long, CostChunk> cache = worlds.computeIfAbsent(world.id(), k -> new HashMap<>());
        int minCx = (centerX - radiusBlocks) >> 4;
        int maxCx = (centerX + radiusBlocks) >> 4;
        int minCz = (centerZ - radiusBlocks) >> 4;
        int maxCz = (centerZ + radiusBlocks) >> 4;
        int sizeX = maxCx - minCx + 1;
        int sizeZ = maxCz - minCz + 1;
        int minY = world.minHeight();
        int maxY = world.maxHeight();
        CostChunk[] grid = new CostChunk[sizeX * sizeZ];
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                long key = key(cx, cz);
                CostChunk c = cache.get(key);
                if (c == null || now - c.capturedTick > TTL_TICKS) {
                    if (!world.isChunkLoaded(cx, cz)) {
                        cache.remove(key);
                        continue;
                    }
                    c = new CostChunk(world.chunkSnapshot(cx, cz), minY, maxY, now);
                    cache.put(key, c);
                }
                grid[(cx - minCx) + (cz - minCz) * sizeX] = c;
            }
        }
        return new SnapshotGrid(grid, minCx, minCz, sizeX, sizeZ, minY, maxY);
    }

    void invalidate(GameWorld world, int blockX, int blockZ) {
        Map<Long, CostChunk> cache = worlds.get(world.id());
        if (cache != null) {
            cache.remove(key(blockX >> 4, blockZ >> 4));
        }
    }

    void purge() {
        long now = server.currentTick();
        Iterator<Map.Entry<UUID, Map<Long, CostChunk>>> wit = worlds.entrySet().iterator();
        while (wit.hasNext()) {
            Map.Entry<UUID, Map<Long, CostChunk>> e = wit.next();
            if (server.world(e.getKey()) == null) {
                wit.remove();
                continue;
            }
            e.getValue().values().removeIf(c -> now - c.capturedTick > PURGE_AGE_TICKS);
        }
    }

    void clear() {
        worlds.clear();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
