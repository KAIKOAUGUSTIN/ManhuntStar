package dev.manhuntstar.util;

/** One of the six directions of a block (the game's Direction, Bukkit's BlockFace). */
public enum Face {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);

    /** The horizontal directions, in the order north, east, south, west. */
    public static final Face[] HORIZONTALS = {NORTH, EAST, SOUTH, WEST};

    private final int modX;
    private final int modY;
    private final int modZ;

    Face(int modX, int modY, int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    public int modX() {
        return modX;
    }

    public int modY() {
        return modY;
    }

    public int modZ() {
        return modZ;
    }

    public Face opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    /** The horizontal direction (N/E/S/W) closest to a yaw in degrees. */
    public static Face fromYaw(float yaw) {
        int i = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (i) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }
}
