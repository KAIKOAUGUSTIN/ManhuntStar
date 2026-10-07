package dev.manhuntstar.pathfinding;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/**
 * Tabela de custo por material, calculada uma vez na thread principal e lida pelas threads de pathfinding.
 */
public final class BlockCosts {

    private static final Set<Material> HAZARDS = EnumSet.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.WITHER_ROSE);
    private static final Set<Material> WATERS = EnumSet.of(Material.WATER, Material.BUBBLE_COLUMN, Material.KELP, Material.KELP_PLANT,
            Material.SEAGRASS, Material.TALL_SEAGRASS);

    private static byte[] costs;
    private static boolean[] waterloggable;

    private BlockCosts() {
    }

    /** Monta a tabela (chamar uma vez ao ligar o plugin). */
    public static synchronized void init() {
        if (costs != null) {
            return;
        }
        Material[] all = Material.values();
        byte[] c = new byte[all.length];
        boolean[] wl = new boolean[all.length];
        for (Material m : all) {
            if (!m.isBlock() || m.isLegacy()) {
                c[m.ordinal()] = CostProvider.AIR;
                continue;
            }
            c[m.ordinal()] = classify(m);
            try {
                wl[m.ordinal()] = m.createBlockData() instanceof Waterlogged;
            } catch (RuntimeException ignored) {
                wl[m.ordinal()] = false;
            }
        }
        waterloggable = wl;
        costs = c;
    }

    private static byte classify(Material m) {
        if (m.isAir()) {
            return CostProvider.AIR;
        }
        if (HAZARDS.contains(m)) {
            return CostProvider.AVOID;
        }
        if (WATERS.contains(m)) {
            return CostProvider.WATER;
        }
        boolean openable = (Tag.DOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m) || Tag.TRAPDOORS.isTagged(m))
                && m != Material.IRON_DOOR && m != Material.IRON_TRAPDOOR;
        boolean replaceable;
        try {
            BlockData d = m.createBlockData();
            replaceable = d.isReplaceable();
        } catch (RuntimeException e) {
            replaceable = false;
        }
        if (replaceable || openable || !m.isSolid()) {
            return CostProvider.AIR;
        }
        float hardness = m.getHardness();
        if (hardness < 0) {
            return CostProvider.UNBREAKABLE;
        }
        int cost = (int) (4f + hardness * 2.0f);
        // byte vai só até 127: acima disso o custo virava negativo e o bloco contava como ar
        return (byte) Math.min(Byte.MAX_VALUE, Math.max(1, cost));
    }

    public static byte cost(Material m) {
        return costs[m.ordinal()];
    }

    public static boolean waterloggable(Material m) {
        return waterloggable[m.ordinal()];
    }
}
