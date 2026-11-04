package dev.manhuntstar.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Posição de bloco imutável. */
public record Pos(int x, int y, int z) {

    public static final BlockFace[] HORIZONTALS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static Pos of(Location l) {
        return new Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static Pos of(Block b) {
        return new Pos(b.getX(), b.getY(), b.getZ());
    }

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    public Pos above() {
        return new Pos(x, y + 1, z);
    }

    public Pos above(int n) {
        return new Pos(x, y + n, z);
    }

    public Pos below() {
        return new Pos(x, y - 1, z);
    }

    public Pos below(int n) {
        return new Pos(x, y - n, z);
    }

    public Pos relative(BlockFace face) {
        return new Pos(x + face.getModX(), y + face.getModY(), z + face.getModZ());
    }

    public Pos relative(BlockFace face, int n) {
        return new Pos(x + face.getModX() * n, y + face.getModY() * n, z + face.getModZ() * n);
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public double distSqr(Pos o) {
        double dx = x - o.x;
        double dy = y - o.y;
        double dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int manhattan(Pos o) {
        return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
    }

    public long asLong() {
        return pack(x, y, z);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static Pos unpack(long v) {
        int x = (int) (v >> 38);
        int y = (int) (v << 52 >> 52);
        int z = (int) (v << 26 >> 38);
        return new Pos(x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Direção horizontal (N/E/S/W) mais próxima de um yaw em graus. */
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
