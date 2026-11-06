package dev.manhuntstar.platform;

/**
 * A read-only copy of a chunk's blocks, taken on the server thread and readable from any thread
 * (the pathfinding threads). Coordinates: x and z inside the chunk (0-15), y in the world.
 */
public interface BlockSnapshot {

    /** The {@link BlockType#id()} of the block. */
    int typeId(int x, int y, int z);

    /** The block holds water (only asked for {@link BlockTrait#WATERLOGGABLE} types). */
    boolean waterlogged(int x, int y, int z);
}
