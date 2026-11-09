package dev.manhuntstar.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A type of block ({@code STONE}, {@code OAK_DOOR}...), named like the game's id in capitals. There's one
 * instance per name, so they can be compared with {@code ==}. When it starts, the platform registers
 * every block type of the game with a number of its own ({@link #id()}, used in tables such as the
 * pathfinding costs) and its {@link BlockTrait traits}.
 */
public final class BlockType {

    private static final Map<String, BlockType> BY_NAME = new ConcurrentHashMap<>();
    private static volatile BlockType[] byId = new BlockType[0];

    private final String name;
    private volatile int id = -1;
    private volatile long traits;
    private volatile float hardness;
    private volatile float slipperiness = 0.6f;

    private BlockType(String name) {
        this.name = name;
    }

    /** The type with this name (registered or not). */
    public static BlockType of(String name) {
        return BY_NAME.computeIfAbsent(name, BlockType::new);
    }

    /** The registered type with this number, or null. */
    public static BlockType byId(int id) {
        BlockType[] table = byId;
        return id >= 0 && id < table.length ? table[id] : null;
    }

    /** Size of the table of numbers: every registered {@link #id()} is below it. */
    public static int idLimit() {
        return byId.length;
    }

    /** Every registered type. */
    public static List<BlockType> all() {
        List<BlockType> out = new ArrayList<>();
        for (BlockType t : byId) {
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    /** Registers a block type with the usual friction (see the other {@code register}). */
    public static BlockType register(int id, String name, float hardness, BlockTrait... traits) {
        return register(id, name, hardness, 0.6f, traits);
    }

    /**
     * Registers a block type of the game (called by the platform when it starts).
     *
     * @param hardness the game's hardness (time to break); negative = unbreakable
     * @param slipperiness the game's friction (0.6 for most blocks, 0.98 for ice)
     */
    public static synchronized BlockType register(int id, String name, float hardness, float slipperiness, BlockTrait... traits) {
        BlockType type = of(name);
        long bits = 0;
        for (BlockTrait t : traits) {
            bits |= t.bit();
        }
        type.traits = bits;
        type.hardness = hardness;
        type.slipperiness = slipperiness;
        type.id = id;
        BlockType[] table = byId;
        if (id >= table.length) {
            BlockType[] bigger = new BlockType[Math.max(id + 1, table.length * 2)];
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

    public boolean is(BlockTrait trait) {
        return (traits & trait.bit()) != 0;
    }

    public float hardness() {
        return hardness;
    }

    public float slipperiness() {
        return slipperiness;
    }

    @Override
    public String toString() {
        return name;
    }
}
