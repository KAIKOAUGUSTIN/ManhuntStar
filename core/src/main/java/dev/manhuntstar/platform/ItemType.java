package dev.manhuntstar.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A type of item ({@code DIAMOND_SWORD}, {@code SHIELD}...), named like the game's id in capitals. There's
 * one instance per name, so they can be compared with {@code ==}. When it starts, the platform registers
 * every item type of the game with a number of its own ({@link #id()}) and its {@link ItemTrait traits}.
 */
public final class ItemType {

    private static final Map<String, ItemType> BY_NAME = new ConcurrentHashMap<>();
    private static volatile ItemType[] byId = new ItemType[0];

    private final String name;
    private volatile int id = -1;
    private volatile long traits;
    private volatile int maxDurability;

    private ItemType(String name) {
        this.name = name;
    }

    /** The type with this name (registered or not). */
    public static ItemType of(String name) {
        return BY_NAME.computeIfAbsent(name, ItemType::new);
    }

    /** The item of a block that is also an item (cobblestone, oak leaves, TNT...): the same name. */
    public static ItemType of(BlockType block) {
        return of(block.name());
    }

    /** The type with exactly this name, if the game has it; null otherwise. */
    public static ItemType find(String name) {
        ItemType type = BY_NAME.get(name);
        return type != null && type.id >= 0 ? type : null;
    }

    /** The registered type with this number, or null. */
    public static ItemType byId(int id) {
        ItemType[] table = byId;
        return id >= 0 && id < table.length ? table[id] : null;
    }

    /**
     * Registers an item type of the game (called by the platform when it starts).
     *
     * @param maxDurability the uses before it breaks; 0 for items that don't wear out
     */
    public static synchronized ItemType register(int id, String name, int maxDurability, ItemTrait... traits) {
        ItemType type = of(name);
        long bits = 0;
        for (ItemTrait t : traits) {
            bits |= t.bit();
        }
        type.traits = bits;
        type.maxDurability = maxDurability;
        type.id = id;
        ItemType[] table = byId;
        if (id >= table.length) {
            ItemType[] bigger = new ItemType[Math.max(id + 1, table.length * 2)];
            System.arraycopy(table, 0, bigger, 0, table.length);
            table = bigger;
        }
        table[id] = type;
        byId = table;
        return type;
    }

    public String name() {
        return name;
    }

    /** The platform's number for this type; -1 if the game doesn't have it. */
    public int id() {
        return id;
    }

    public boolean is(ItemTrait trait) {
        return (traits & trait.bit()) != 0;
    }

    public int maxDurability() {
        return maxDurability;
    }

    @Override
    public String toString() {
        return name;
    }
}
