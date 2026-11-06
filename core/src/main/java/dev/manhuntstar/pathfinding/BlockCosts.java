package dev.manhuntstar.pathfinding;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import java.util.Set;

/**
 * Cost table per block type, built once on the main thread and read by the pathfinding threads.
 */
public final class BlockCosts {

    private static final Set<String> HAZARDS = Set.of("LAVA", "FIRE", "SOUL_FIRE", "SWEET_BERRY_BUSH",
            "POWDER_SNOW", "CACTUS", "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "WITHER_ROSE");
    private static final Set<String> WATERS = Set.of("WATER", "BUBBLE_COLUMN", "KELP", "KELP_PLANT",
            "SEAGRASS", "TALL_SEAGRASS");

    private static byte[] costs;
    private static boolean[] waterloggable;

    private BlockCosts() {
    }

    /** Builds the table (call once when the plugin starts, after the platform registered the block types). */
    public static synchronized void init() {
        if (costs != null) {
            return;
        }
        int size = BlockType.idLimit();
        byte[] c = new byte[size];
        boolean[] wl = new boolean[size];
        for (BlockType t : BlockType.all()) {
            c[t.id()] = classify(t);
            wl[t.id()] = t.is(BlockTrait.WATERLOGGABLE);
        }
        waterloggable = wl;
        costs = c;
    }

    static byte classify(BlockType t) {
        if (t.is(BlockTrait.AIR)) {
            return CostProvider.AIR;
        }
        String name = t.name();
        if (HAZARDS.contains(name)) {
            return CostProvider.AVOID;
        }
        if (WATERS.contains(name)) {
            return CostProvider.WATER;
        }
        boolean openable = (t.is(BlockTrait.DOOR) || t.is(BlockTrait.FENCE_GATE) || t.is(BlockTrait.TRAPDOOR))
                && !name.equals("IRON_DOOR") && !name.equals("IRON_TRAPDOOR");
        if (t.is(BlockTrait.REPLACEABLE) || openable || !t.is(BlockTrait.SOLID)) {
            return CostProvider.AIR;
        }
        float hardness = t.hardness();
        if (hardness < 0) {
            return CostProvider.UNBREAKABLE;
        }
        int cost = (int) (4f + hardness * 2.0f);
        // a byte only goes up to 127: above that the cost became negative and the block counted as air
        return (byte) Math.min(Byte.MAX_VALUE, Math.max(1, cost));
    }

    /** Cost of a block type by its {@link BlockType#id()}; types that aren't blocks count as air. */
    public static byte cost(int typeId) {
        byte[] c = costs;
        return typeId >= 0 && typeId < c.length ? c[typeId] : CostProvider.AIR;
    }

    public static boolean waterloggable(int typeId) {
        boolean[] wl = waterloggable;
        return typeId >= 0 && typeId < wl.length && wl[typeId];
    }
}
