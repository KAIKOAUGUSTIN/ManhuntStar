package dev.manhuntstar.platform;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.List;
import java.util.UUID;

/** A world (dimension) of the server, as the AI sees it. */
public interface GameWorld {

    UUID id();

    String name();

    Dimension dimension();

    /** Lowest block Y. */
    int minHeight();

    /** One above the highest block Y. */
    int maxHeight();

    /** Height the world's mechanics consider (teleports, portals): 128 in the nether. */
    int logicalHeight();

    boolean hasCeiling();

    boolean isDayTime();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** The world's spawn point. */
    Vec3 spawnPosition();

    /** How far a chunk is loaded (for debug text): the platform's name for it. */
    String chunkLoadLevel(int chunkX, int chunkZ);

    /** A copy of a loaded chunk (server thread only). */
    BlockSnapshot chunkSnapshot(int chunkX, int chunkZ);

    GameBlock block(int x, int y, int z);

    default GameBlock block(Pos p) {
        return block(p.x(), p.y(), p.z());
    }

    /** The type of the block at a position (faster than {@code block(...).type()}). */
    BlockType type(int x, int y, int z);

    default BlockType type(Pos p) {
        return type(p.x(), p.y(), p.z());
    }

    /** The fluid at a position. */
    Fluid fluid(int x, int y, int z);

    /**
     * Y of the highest block that blocks motion in a column; {@code noLeaves} skips leaves (the game's
     * MOTION_BLOCKING and MOTION_BLOCKING_NO_LEAVES heightmaps).
     */
    int highestY(int x, int z, boolean noLeaves);

    /**
     * The first block a ray hits, or null.
     *
     * @param fluids whether water and lava stop the ray
     * @param ignorePassable whether blocks without collision (grass, open doors...) are skipped
     */
    RayHit rayTraceBlocks(Vec3 from, Vec3 direction, double maxDistance, boolean fluids, boolean ignorePassable);

    void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch);

    /** A particle effect seen by everyone nearby; {@code extra} is usually the speed. */
    void spawnParticle(Particle particle, Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra);

    /** Block crack particles of a block (the game's {@code block} particle). */
    void spawnBlockParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameBlock block);

    /** Crumbs of an item (the game's {@code item} particle), as when eating. */
    void spawnItemParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameItem item);

    /** The value of a true/false game rule (null if the world doesn't have it). */
    Boolean gameRule(GameRule rule);

    Difficulty difficulty();

    List<? extends GamePlayer> players();

    /** Entities whose box overlaps the given box. */
    List<GameEntity> nearbyEntities(Box box);

    /** Entities within the given distances (half sizes of a box) around a point. */
    List<GameEntity> nearbyEntities(Vec3 center, double x, double y, double z);

    /** Drops an item at a point with a small random offset and push, like a broken block's drop. */
    GameItemEntity dropItemNaturally(Vec3 at, GameItem item);
}
