package dev.manhuntstar.platform;

import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;

/**
 * A point in a world with a facing, like Bukkit's Location. It can't be changed: {@link #add} and the
 * {@code with} methods give a new one. The world may be null (a world that isn't loaded).
 */
public record Place(GameWorld world, double x, double y, double z, float yaw, float pitch) {

    public Place(GameWorld world, Vec3 pos, float yaw, float pitch) {
        this(world, pos.x(), pos.y(), pos.z(), yaw, pitch);
    }

    /** A point facing south and level (yaw and pitch 0). */
    public Place(GameWorld world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    public Place(GameWorld world, Vec3 pos) {
        this(world, pos.x(), pos.y(), pos.z(), 0f, 0f);
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    /** The block the point is in. */
    public Pos blockPos() {
        return new Pos(blockX(), blockY(), blockZ());
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public Place add(double dx, double dy, double dz) {
        return new Place(world, x + dx, y + dy, z + dz, yaw, pitch);
    }

    public Place withY(double newY) {
        return new Place(world, x, newY, z, yaw, pitch);
    }

    public Place withYaw(float newYaw) {
        return new Place(world, x, y, z, newYaw, pitch);
    }

    /** Distance to another point; like Bukkit, both must be in the same (loaded) world. */
    public double distance(Place o) {
        if (o == null) {
            throw new IllegalArgumentException("Cannot measure distance to a null location");
        }
        if (o.world == null || world == null) {
            throw new IllegalArgumentException("Cannot measure distance to a null world");
        }
        if (o.world != world) {
            throw new IllegalArgumentException("Cannot measure distance between " + world.name() + " and " + o.world.name());
        }
        double dx = x - o.x;
        double dy = y - o.y;
        double dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
