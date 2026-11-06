package dev.manhuntstar.pathfinding;

/**
 * Cost grid over a region of {@link CostChunk}s, made to be read off the main thread.
 * Unloaded chunks (or ones outside the captured region) count as {@link CostProvider#AVOID}:
 * the bot doesn't plan through places it doesn't know.
 */
public final class SnapshotGrid implements CostProvider {

    private final CostChunk[] chunks;
    private final int minChunkX;
    private final int minChunkZ;
    private final int sizeX;
    private final int sizeZ;
    private final int minY;
    private final int maxY;

    SnapshotGrid(CostChunk[] chunks, int minChunkX, int minChunkZ, int sizeX, int sizeZ, int minY, int maxY) {
        this.chunks = chunks;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.minY = minY;
        this.maxY = maxY;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    @Override
    public byte cost(int x, int y, int z) {
        if (y < minY || y >= maxY) {
            return y < minY ? AVOID : AIR;
        }
        int cx = (x >> 4) - minChunkX;
        int cz = (z >> 4) - minChunkZ;
        if (cx < 0 || cz < 0 || cx >= sizeX || cz >= sizeZ) {
            return AVOID;
        }
        CostChunk c = chunks[cx + cz * sizeX];
        if (c == null) {
            return AVOID;
        }
        return c.cost(x & 15, y, z & 15);
    }
}
