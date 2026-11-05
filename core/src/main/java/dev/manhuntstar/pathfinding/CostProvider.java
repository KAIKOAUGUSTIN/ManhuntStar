package dev.manhuntstar.pathfinding;

/**
 * Cost of each block for pathfinding.
 * <ul>
 *   <li>{@link #AIR} (0): free to pass.</li>
 *   <li>{@link #WATER} (-2): water (can swim).</li>
 *   <li>{@link #AVOID} (-1): danger or outside the known area (lava, fire, berry bush, powder snow...).</li>
 *   <li>{@link #UNBREAKABLE} (-3): solid that doesn't break (bedrock, barrier): can stand on it, can't dig it.</li>
 *   <li>1..127: breakable solid; the value is the cost of digging it.</li>
 * </ul>
 */
@FunctionalInterface
public interface CostProvider {

    byte AIR = 0;
    byte AVOID = -1;
    byte WATER = -2;
    byte UNBREAKABLE = -3;

    byte cost(int x, int y, int z);

    static boolean isMinable(byte c) {
        return c > 0;
    }

    /** Can be used as a floor (solid, breakable or not). */
    static boolean isSolid(byte c) {
        return c > 0 || c == UNBREAKABLE;
    }

    /** The body can't occupy it (danger or unbreakable). */
    static boolean isBlocking(byte c) {
        return c == AVOID || c == UNBREAKABLE;
    }
}
