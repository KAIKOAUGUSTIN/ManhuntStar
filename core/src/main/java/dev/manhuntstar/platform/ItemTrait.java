package dev.manhuntstar.platform;

/** Properties of an item type that the AI asks about, filled in by the platform (see {@link ItemType}). */
public enum ItemTrait {
    SWORD,
    AXE,
    PICKAXE,
    SHOVEL,
    HOE,
    SPEAR;

    final long bit() {
        return 1L << ordinal();
    }
}
