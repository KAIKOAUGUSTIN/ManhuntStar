package dev.manhuntstar.pathfinding;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Cópias de chunk usadas pelo pathfinding (só na thread principal). Cada cópia vale por
 * {@link #TTL_TICKS} ticks ou até um bloco dela mudar; vários bots perto um do outro
 * reaproveitam as mesmas cópias.
 */
final class ChunkSnapshotCache {

    private static final int TTL_TICKS = 40;
    private static final int PURGE_AGE_TICKS = 200;

    private final Map<UUID, Map<Long, CostChunk>> worlds = new HashMap<>();

    /** Captura (ou reaproveita) os chunks carregados num raio em blocos em volta do centro. */
    SnapshotGrid capture(World world, int centerX, int centerZ, int radiusBlocks) {
        long now = Bukkit.getCurrentTick();
        Map<Long, CostChunk> cache = worlds.computeIfAbsent(world.getUID(), k -> new HashMap<>());
        int minCx = (centerX - radiusBlocks) >> 4;
        int maxCx = (centerX + radiusBlocks) >> 4;
        int minCz = (centerZ - radiusBlocks) >> 4;
        int maxCz = (centerZ + radiusBlocks) >> 4;
        int sizeX = maxCx - minCx + 1;
        int sizeZ = maxCz - minCz + 1;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
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
                    Chunk chunk = world.getChunkAt(cx, cz);
                    c = new CostChunk(chunk.getChunkSnapshot(false, false, false), minY, maxY, now);
                    cache.put(key, c);
                }
                grid[(cx - minCx) + (cz - minCz) * sizeX] = c;
            }
        }
        return new SnapshotGrid(grid, minCx, minCz, sizeX, sizeZ, minY, maxY);
    }

    void invalidate(World world, int blockX, int blockZ) {
        Map<Long, CostChunk> cache = worlds.get(world.getUID());
        if (cache != null) {
            cache.remove(key(blockX >> 4, blockZ >> 4));
        }
    }

    void purge() {
        long now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Map<Long, CostChunk>>> wit = worlds.entrySet().iterator();
        while (wit.hasNext()) {
            Map.Entry<UUID, Map<Long, CostChunk>> e = wit.next();
            if (Bukkit.getWorld(e.getKey()) == null) {
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
