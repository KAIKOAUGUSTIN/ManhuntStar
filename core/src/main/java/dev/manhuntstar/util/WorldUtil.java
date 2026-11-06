package dev.manhuntstar.util;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Fluid;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.RayHit;

/** Block queries used by the AI (equivalent to the mod's BlockState checks). */
public final class WorldUtil {

    private WorldUtil() {
    }

    public static GameBlock block(GameWorld w, Pos p) {
        return w.block(p.x(), p.y(), p.z());
    }

    public static boolean isLoaded(GameWorld w, Pos p) {
        return w.isChunkLoaded(p.x() >> 4, p.z() >> 4);
    }

    public static boolean isAir(GameWorld w, Pos p) {
        return w.type(p.x(), p.y(), p.z()).is(BlockTrait.AIR);
    }

    public static Fluid fluid(GameWorld w, int x, int y, int z) {
        // Air and full opaque blocks never have fluid. The shortcut avoids the platform's fluid lookup,
        // which dominated large block searches (cave ambush) on Paper.
        BlockType t = w.type(x, y, z);
        if (t.is(BlockTrait.AIR) || t.is(BlockTrait.OCCLUDING)) {
            return Fluid.EMPTY;
        }
        return w.fluid(x, y, z);
    }

    public static boolean isWater(GameWorld w, Pos p) {
        return fluid(w, p.x(), p.y(), p.z()).isWater();
    }

    public static boolean isLava(GameWorld w, Pos p) {
        return fluid(w, p.x(), p.y(), p.z()).isLava();
    }

    public static boolean hasFluid(GameWorld w, Pos p) {
        return fluid(w, p.x(), p.y(), p.z()) != Fluid.EMPTY;
    }

    /** A block that is neither air nor fluid (the mod's "isTrulySolid"). */
    public static boolean isTrulySolid(GameWorld w, Pos p) {
        if (!isLoaded(w, p)) {
            return true;
        }
        return !isAir(w, p) && !hasFluid(w, p);
    }

    public static boolean canBeReplaced(GameWorld w, Pos p) {
        return block(w, p).isReplaceable();
    }

    public static boolean hasCollision(GameWorld w, Pos p) {
        return !block(w, p).isPassable();
    }

    public static boolean blocksMotion(GameWorld w, Pos p) {
        return w.type(p.x(), p.y(), p.z()).is(BlockTrait.SOLID);
    }

    public static boolean isSturdyTop(GameWorld w, Pos p) {
        return block(w, p).isTopSturdy();
    }

    /** Feet and head free of collision and fluid. */
    public static boolean hasBodySpace(GameWorld w, Pos feet) {
        return !hasCollision(w, feet) && !hasCollision(w, feet.above());
    }

    public static int skyLight(GameWorld w, Pos p) {
        return block(w, p).skyLight();
    }

    public static boolean isClimbable(GameWorld w, Pos p) {
        return w.type(p.x(), p.y(), p.z()).is(BlockTrait.CLIMBABLE);
    }

    public static boolean isIgniting(GameWorld w, Pos p) {
        String name = w.type(p.x(), p.y(), p.z()).name();
        if (name.equals("FIRE") || name.equals("SOUL_FIRE")) {
            return true;
        }
        if (name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")) {
            return block(w, p).isLit();
        }
        return false;
    }

    /** Ray that doesn't hit blocks with collision (ClipContext COLLIDER, fluid ignored). */
    public static boolean clearLine(GameWorld w, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) {
            return true;
        }
        return w.rayTraceBlocks(from, d.scale(1.0 / len), len, false, true) == null;
    }

    public static RayHit rayBlocks(GameWorld w, Vec3 from, Vec3 to, boolean outline) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4) {
            return null;
        }
        return w.rayTraceBlocks(from, d.scale(1.0 / len), len, false, !outline);
    }

    public static int highestY(GameWorld w, int x, int z) {
        return w.highestY(x, z, false);
    }

    public static int highestYNoLeaves(GameWorld w, int x, int z) {
        return w.highestY(x, z, true);
    }

    /** First free Y above the terrain (the game's {@code getHeight}). */
    public static int surfaceY(GameWorld w, int x, int z) {
        return highestY(w, x, z) + 1;
    }

    public static int surfaceYNoLeaves(GameWorld w, int x, int z) {
        return highestYNoLeaves(w, x, z) + 1;
    }

    /** The block has a collision shape and isn't fluid (the mod's "non-empty getCollisionShape"). */
    public static boolean collides(GameWorld w, Pos p) {
        return isLoaded(w, p) && hasCollision(w, p);
    }

    /** A safe place to stand: solid support, feet and head free and no fluid/lava. */
    public static boolean isSafeStand(GameWorld w, Pos feet) {
        if (feet.y() <= w.minHeight() || feet.y() >= w.maxHeight() - 2) {
            return false;
        }
        Pos below = feet.below();
        BlockType belowType = w.type(below.x(), below.y(), below.z());
        if (belowType.is(BlockTrait.AIR) || hasFluid(w, below)) {
            return false;
        }
        if (!belowType.is(BlockTrait.SOLID) && !isSturdyTop(w, below)) {
            return false;
        }
        return !hasCollision(w, feet) && !hasFluid(w, feet) && !hasCollision(w, feet.above()) && !hasFluid(w, feet.above());
    }

    public static int countOpenSides(GameWorld w, Pos feet) {
        int open = 0;
        for (Face f : Face.HORIZONTALS) {
            Pos side = feet.relative(f);
            if (!hasCollision(w, side) && !hasFluid(w, side) && !hasCollision(w, side.above()) && !hasFluid(w, side.above())) {
                open++;
            }
        }
        return open;
    }
}
