package dev.manhuntstar.util;

import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Conversions between {@link Pos}/{@link Vec3}, which live in core and know nothing about the server,
 * and the Bukkit types ({@link Location}, {@link Block}, {@link Vector}, {@link BlockFace}).
 */
public final class Locations {

    public static final BlockFace[] HORIZONTALS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private Locations() {
    }

    public static Pos pos(Location l) {
        return new Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static Pos pos(Block b) {
        return new Pos(b.getX(), b.getY(), b.getZ());
    }

    public static Vec3 vec(Location l) {
        return new Vec3(l.getX(), l.getY(), l.getZ());
    }

    public static Vec3 vec(Vector v) {
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    public static Vec3 vec(Entity e) {
        Location l = e.getLocation();
        return new Vec3(l.getX(), l.getY(), l.getZ());
    }

    /** A Bukkit location as a core place (null stays null). */
    public static Place place(Location l) {
        return l == null ? null : new Place(PaperWorld.of(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** A core place as a Bukkit location (null stays null). */
    public static Location location(Place p) {
        return p == null ? null : new Location(PaperWorld.bukkit(p.world()), p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    public static Location location(Pos p, GameWorld world) {
        return location(p, PaperWorld.bukkit(world));
    }

    public static Location location(Vec3 v, GameWorld world) {
        return location(v, PaperWorld.bukkit(world));
    }

    public static Location location(Vec3 v, GameWorld world, float yaw, float pitch) {
        return location(v, PaperWorld.bukkit(world), yaw, pitch);
    }

    public static Block block(Pos p, GameWorld world) {
        return block(p, PaperWorld.bukkit(world));
    }

    public static Location location(Pos p, World world) {
        return new Location(world, p.x(), p.y(), p.z());
    }

    public static Location location(Vec3 v, World world) {
        return new Location(world, v.x(), v.y(), v.z());
    }

    public static Location location(Vec3 v, World world, float yaw, float pitch) {
        return new Location(world, v.x(), v.y(), v.z(), yaw, pitch);
    }

    public static Box box(BoundingBox b) {
        return new Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ());
    }

    public static BoundingBox boundingBox(Box b) {
        return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    public static Vector vector(Vec3 v) {
        return new Vector(v.x(), v.y(), v.z());
    }

    public static Block block(Pos p, World world) {
        return world.getBlockAt(p.x(), p.y(), p.z());
    }

    public static Pos relative(Pos p, BlockFace face) {
        return new Pos(p.x() + face.getModX(), p.y() + face.getModY(), p.z() + face.getModZ());
    }

    public static Pos relative(Pos p, BlockFace face, int n) {
        return new Pos(p.x() + face.getModX() * n, p.y() + face.getModY() * n, p.z() + face.getModZ() * n);
    }

    /** The horizontal direction (N/E/S/W) closest to a yaw in degrees. */
    public static BlockFace faceFromYaw(float yaw) {
        int i = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (i) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
