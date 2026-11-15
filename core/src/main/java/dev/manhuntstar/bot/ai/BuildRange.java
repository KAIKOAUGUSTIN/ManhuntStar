package dev.manhuntstar.bot.ai;

import java.util.UUID;

/**
 * When a bot may place blocks to reach its target (tower, bridge, the path's blocks, filling a gap). A build
 * only starts with the target close, as in the mod, and then keeps going toward that same target however far
 * the target goes, while the bot keeps placing blocks: a player who climbs a tall tower or bridges away on top
 * of it is followed up there. Without it the bot lost the right to build as soon as the player was 3 blocks
 * away, climbed down and built a new tower under the player.
 *
 * <p>It ends after {@link #KEEP_TICKS} without a block or with another target. A build doesn't spread blocks
 * around: placing a block costs the path much more than walking, so once the bot can reach the player on foot
 * it walks, stops placing, and the build ends.
 */
final class BuildRange {

    /** Horizontal distance to the target that starts a build (the mod's). */
    static final double START = 3.0;
    /**
     * Ticks a build keeps going after its last block. Longer than the limit of {@code Bot.placeBlock} (10 blocks
     * every 10 s), so a bot waiting for that limit on a tall tower doesn't give up.
     */
    static final int KEEP_TICKS = 300;

    private UUID target;
    private long lastBlockTick;

    /** A block of the build toward this target was placed. */
    void placed(UUID target, long now) {
        this.target = target;
        this.lastBlockTick = now;
    }

    /** Whether the bot may place blocks toward this target, at this horizontal distance from it. */
    boolean allows(UUID target, double horizontal, long now) {
        return horizontal <= START || keeps(target, now);
    }

    /** A build toward this target is under way: the bot placed a block for it a moment ago. */
    boolean keeps(UUID target, long now) {
        return target != null && target.equals(this.target) && now - lastBlockTick <= KEEP_TICKS;
    }
}
