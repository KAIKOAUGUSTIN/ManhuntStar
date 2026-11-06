package dev.manhuntstar.platform;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;

/**
 * A block of a world at a position, like Bukkit's Block: reading it reads the world as it is now, and
 * changing it changes the world. Unless said otherwise, a change updates the neighbors (physics).
 */
public interface GameBlock {

    GameWorld world();

    int x();

    int y();

    int z();

    default Pos pos() {
        return new Pos(x(), y(), z());
    }

    default GameBlock relative(int dx, int dy, int dz) {
        return world().block(x() + dx, y() + dy, z() + dz);
    }

    default GameBlock relative(Face face) {
        return relative(face.modX(), face.modY(), face.modZ());
    }

    BlockType type();

    /** Sets the block to the type's default state. */
    default void setType(BlockType type) {
        setType(type, true);
    }

    void setType(BlockType type, boolean physics);

    /**
     * Places a block the way the bot builds: the type's default state, leaves don't decay, with physics.
     */
    void place(BlockType type);

    /** Breaks the block as if mined with {@code tool}, dropping what that tool would get; {@code effects} plays the break particles and sound. */
    boolean breakNaturally(GameItem tool, boolean effects);

    /** An entity can walk through it (air, grass, open doors...). */
    boolean isPassable();

    /** Placing a block here replaces it. */
    boolean isReplaceable();

    /** Water or lava. */
    boolean isLiquid();

    Fluid fluid();

    /** The fluid level of a water or lava block (0 = source); -1 for other blocks. */
    int fluidLevel();

    /** The top face holds a block fully (like a full block's top). */
    boolean isTopSturdy();

    int lightLevel();

    int skyLight();

    /** A lit campfire, furnace or other block that can be lit. */
    boolean isLit();

    /** A door, gate or trapdoor. */
    boolean isOpenable();

    boolean isOpen();

    void setOpen(boolean open);

    BedPart bedPart();

    /** Puts one half of a bed of the given type facing that direction, without physics. */
    void setBed(BlockType bed, Face facing, BedPart part);

    /** Puts a nether portal block along the X axis, without physics. */
    void setPortalX();

    /** The block's collision box in the world (empty when it has none). */
    Box boundingBox();

    SoundGroup soundGroup();
}
