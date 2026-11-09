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
    /** Blocks light fully (a full opaque block). */
    OCCLUDING,
    /** Can be climbed (ladder, vines, scaffolding...). */
    CLIMBABLE,
    LEAVES,
    DOOR,
    FENCE_GATE,
    TRAPDOOR,
    /** Breaks faster with a pickaxe (the game's mineable/pickaxe tag); the same for the others. */
    MINEABLE_PICKAXE,
    MINEABLE_AXE,
    MINEABLE_SHOVEL,
    MINEABLE_HOE;

    final long bit() {
        return 1L << ordinal();
    }
}
