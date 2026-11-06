package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.util.Face;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/**
 * Registers the game's blocks in core ({@link BlockType}), with the Bukkit {@link Material} number as
 * the type number, and converts between the two.
 */
public final class PaperBlocks {

    private static final BlockType[] TYPES;
    private static final Material[] MATERIALS;

    static {
        Material[] all = Material.values();
        MATERIALS = all;
        TYPES = new BlockType[all.length];
        for (Material m : all) {
            if (m.isBlock() && !m.isLegacy()) {
                TYPES[m.ordinal()] = BlockType.register(m.ordinal(), m.name(), m.getHardness(), m.getSlipperiness(), traits(m));
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
        if (m.isOccluding()) {
            out.add(BlockTrait.OCCLUDING);
        }
        if (Tag.CLIMBABLE.isTagged(m)) {
            out.add(BlockTrait.CLIMBABLE);
        }
        if (Tag.LEAVES.isTagged(m)) {
            out.add(BlockTrait.LEAVES);
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
        if (Tag.MINEABLE_PICKAXE.isTagged(m)) {
            out.add(BlockTrait.MINEABLE_PICKAXE);
        }
        if (Tag.MINEABLE_AXE.isTagged(m)) {
            out.add(BlockTrait.MINEABLE_AXE);
        }
        if (Tag.MINEABLE_SHOVEL.isTagged(m)) {
            out.add(BlockTrait.MINEABLE_SHOVEL);
        }
        if (Tag.MINEABLE_HOE.isTagged(m)) {
            out.add(BlockTrait.MINEABLE_HOE);
        }
        return out.toArray(BlockTrait[]::new);
    }

    /** The core type of a block material (null for materials that aren't blocks). */
    public static BlockType of(Material m) {
        return TYPES[m.ordinal()];
    }

    /** The Bukkit material of a core block type. */
    public static Material material(BlockType type) {
        return MATERIALS[type.id()];
    }

    public static BlockFace face(Face face) {
        return switch (face) {
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
        };
    }

    /** The core direction of a Bukkit face (null for the diagonal ones). */
    public static Face face(BlockFace face) {
        return switch (face) {
            case NORTH -> Face.NORTH;
            case EAST -> Face.EAST;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
            default -> null;
        };
    }
}
