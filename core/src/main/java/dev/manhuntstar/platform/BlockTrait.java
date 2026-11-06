package dev.manhuntstar.platform;

/** Properties of a block type that the AI asks about, filled in by the platform (see {@link BlockType}). */
public enum BlockTrait {
    /** Air (normal, cave or void). */
    AIR,
    /** Has a solid shape (the game's "solid" material). */
    SOLID,
    /** Can be replaced by placing a block on it (grass, snow layer, water...). */
    REPLACEABLE,
    /** Can hold water ("waterlogged"). */
    WATERLOGGABLE,
    DOOR,
    FENCE_GATE,
    TRAPDOOR;

    final long bit() {
        return 1L << ordinal();
    }
}
