package dev.manhuntstar.util;

import io.papermc.paper.block.fluid.FluidData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Fluid;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.BlockSupport;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Block queries used by the AI (equivalent to the mod's BlockState checks). */
public final class WorldUtil {

    private WorldUtil() {
    }

    public static Block block(World w, Pos p) {
        return w.getBlockAt(p.x(), p.y(), p.z());
    }

    public static boolean isLoaded(World w, Pos p) {
        return w.isChunkLoaded(p.x() >> 4, p.z() >> 4);
    }

    public static boolean isAir(World w, Pos p) {
        return block(w, p).getType().isAir();
    }

    public static Fluid fluid(World w, int x, int y, int z) {
        // Air and full opaque blocks never have fluid. The shortcut avoids FluidData's registry conversion,
        // which dominated large block searches (cave ambush).
        Material m = w.getType(x, y, z);
        if (m.isAir() || m.isOccluding()) {
            return Fluid.EMPTY;
        }
        FluidData data = w.getFluidData(x, y, z);
        return data.getFluidType();
    }

    public static boolean isWater(World w, Pos p) {
        Fluid f = fluid(w, p.x(), p.y(), p.z());
        return f == Fluid.WATER || f == Fluid.FLOWING_WATER;
    }

    public static boolean isLava(World w, Pos p) {
        Fluid f = fluid(w, p.x(), p.y(), p.z());
        return f == Fluid.LAVA || f == Fluid.FLOWING_LAVA;
    }

    public static boolean hasFluid(World w, Pos p) {
        Fluid f = fluid(w, p.x(), p.y(), p.z());
        return f != Fluid.EMPTY;
    }

    /** A block that is neither air nor fluid (the mod's "isTrulySolid"). */
    public static boolean isTrulySolid(World w, Pos p) {
        if (!isLoaded(w, p)) {
            return true;
        }
        Block b = block(w, p);
        return !b.getType().isAir() && !hasFluid(w, p);
    }

    public static boolean canBeReplaced(World w, Pos p) {
        Block b = block(w, p);
        return b.isReplaceable();
    }

    public static boolean hasCollision(World w, Pos p) {
        return !block(w, p).isPassable();
    }

    public static boolean blocksMotion(World w, Pos p) {
        return block(w, p).getType().isSolid();
    }

    public static boolean isSturdyTop(World w, Pos p) {
        BlockData data = block(w, p).getBlockData();
        return data.isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    /** Feet and head free of collision and fluid. */
    public static boolean hasBodySpace(World w, Pos feet) {
        return !hasCollision(w, feet) && !hasCollision(w, feet.above());
    }

    public static int skyLight(World w, Pos p) {
        return block(w, p).getLightFromSky();
    }

    public static boolean isClimbable(World w, Pos p) {
        return Tag.CLIMBABLE.isTagged(block(w, p).getType());
    }

    public static boolean isIgniting(World w, Pos p) {
        Block b = block(w, p);
        Material m = b.getType();
        if (m == Material.FIRE || m == Material.SOUL_FIRE) {
            return true;
        }
        if ((m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) && b.getBlockData() instanceof Campfire c) {
            return c.isLit();
        }
        return false;
    }

    /** Ray that doesn't hit blocks with collision (ClipContext COLLIDER, fluid ignored). */
    public static boolean clearLine(World w, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) {
            return true;
        }
        Vector dir = Locations.vector(d.scale(1.0 / len));
        RayTraceResult r = w.rayTraceBlocks(Locations.location(from, w), dir, len, FluidCollisionMode.NEVER, true);
        return r == null || r.getHitBlock() == null;
    }

    public static RayTraceResult rayBlocks(World w, Vec3 from, Vec3 to, boolean outline) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) {
            return null;
        }
        return w.rayTraceBlocks(Locations.location(from, w), Locations.vector(d.scale(1.0 / len)), len, FluidCollisionMode.NEVER, !outline);
    }

    public static int highestY(World w, int x, int z) {
        return w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING);
    }

    public static int highestYNoLeaves(World w, int x, int z) {
        return w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /** First free Y above the terrain (the game's {@code getHeight}). */
    public static int surfaceY(World w, int x, int z) {
        return highestY(w, x, z) + 1;
    }

    public static int surfaceYNoLeaves(World w, int x, int z) {
        return highestYNoLeaves(w, x, z) + 1;
    }

    /** The block has a collision shape and isn't fluid (the mod's "non-empty getCollisionShape"). */
    public static boolean collides(World w, Pos p) {
        return isLoaded(w, p) && hasCollision(w, p);
    }

    /** A safe place to stand: solid support, feet and head free and no fluid/lava. */
    public static boolean isSafeStand(World w, Pos feet) {
        if (feet.y() <= w.getMinHeight() || feet.y() >= w.getMaxHeight() - 2) {
            return false;
        }
        Pos below = feet.below();
        Block belowB = block(w, below);
        if (belowB.getType().isAir() || hasFluid(w, below)) {
            return false;
        }
        if (!belowB.getType().isSolid() && !isSturdyTop(w, below)) {
            return false;
        }
        return !hasCollision(w, feet) && !hasFluid(w, feet) && !hasCollision(w, feet.above()) && !hasFluid(w, feet.above());
    }

    public static int countOpenSides(World w, Pos feet) {
        int open = 0;
        for (BlockFace f : Locations.HORIZONTALS) {
            Pos side = Locations.relative(feet, f);
            if (!hasCollision(w, side) && !hasFluid(w, side) && !hasCollision(w, side.above()) && !hasFluid(w, side.above())) {
                open++;
            }
        }
        return open;
    }
}
