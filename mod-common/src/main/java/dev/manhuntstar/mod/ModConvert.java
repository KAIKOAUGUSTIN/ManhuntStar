package dev.manhuntstar.mod;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Conversions between core's geometry and Minecraft's. */
public final class ModConvert {

    private ModConvert() {
    }

    public static Vec3 vec(net.minecraft.world.phys.Vec3 v) {
        return new Vec3(v.x, v.y, v.z);
    }

    public static net.minecraft.world.phys.Vec3 vanilla(Vec3 v) {
        return new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z());
    }

    public static Pos pos(BlockPos p) {
        return new Pos(p.getX(), p.getY(), p.getZ());
    }

    public static BlockPos blockPos(Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    public static Box box(AABB b) {
        return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    public static AABB aabb(Box b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    public static Face face(Direction d) {
        return switch (d) {
            case NORTH -> Face.NORTH;
            case EAST -> Face.EAST;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
        };
    }

    public static Direction direction(Face f) {
        return switch (f) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    /** Yaw in -180..180, as Bukkit's Location.normalizeYaw. */
    public static float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw >= 180.0F) {
            yaw -= 360.0F;
        } else if (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    /** Pitch in -90..90, as Bukkit's Location.normalizePitch. */
    public static float normalizePitch(float pitch) {
        if (pitch > 90.0F) {
            return 90.0F;
        }
        return Math.max(pitch, -90.0F);
    }
}
