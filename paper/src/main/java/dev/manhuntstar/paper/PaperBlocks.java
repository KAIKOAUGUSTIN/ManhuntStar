package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/**
 * Registers the game's blocks in core ({@link BlockType}), with the Bukkit {@link Material} number as
 * the type number, and converts between the two.
 */
public final class PaperBlocks {

    private static final BlockType[] TYPES;

    static {
        Material[] all = Material.values();
        TYPES = new BlockType[all.length];
        for (Material m : all) {
            if (m.isBlock() && !m.isLegacy()) {
                TYPES[m.ordinal()] = BlockType.register(m.ordinal(), m.name(), m.getHardness(), traits(m));
            }
        }
    }

    private PaperBlocks() {
    }

    /** Registers the block types in core (before the pathfinding starts). */
    public static void init() {
        // the work is done by the static block, once
    }

    private static BlockTrait[] traits(Material m) {
        List<BlockTrait> out = new ArrayList<>();
        if (m.isAir()) {
            out.add(BlockTrait.AIR);
        }
        if (m.isSolid()) {
            out.add(BlockTrait.SOLID);
        }
        try {
            BlockData d = m.createBlockData();
            if (d.isReplaceable()) {
                out.add(BlockTrait.REPLACEABLE);
            }
            if (d instanceof Waterlogged) {
                out.add(BlockTrait.WATERLOGGABLE);
            }
        } catch (RuntimeException ignored) {
            // no block data: neither replaceable nor waterloggable
        }
        if (Tag.DOORS.isTagged(m)) {
            out.add(BlockTrait.DOOR);
        }
        if (Tag.FENCE_GATES.isTagged(m)) {
            out.add(BlockTrait.FENCE_GATE);
        }
        if (Tag.TRAPDOORS.isTagged(m)) {
            out.add(BlockTrait.TRAPDOOR);
        }
        return out.toArray(BlockTrait[]::new);
    }

    /** The core type of a block material (null for materials that aren't blocks). */
    public static BlockType of(Material m) {
        return TYPES[m.ordinal()];
    }
}
