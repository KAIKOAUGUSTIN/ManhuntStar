package dev.manhuntstar.bot.perception;

import dev.manhuntstar.util.Vec3;
import java.util.UUID;

/**
 * Something a bot learned about where a player is: the position (the real one for a body seen, a guess for the
 * others), what gave it away and when.
 */
public record Sighting(UUID player, UUID world, Vec3 pos, Kind kind, long tick) {

    /** What gave the player away, most precise first. */
    public enum Kind {
        /** The body (feet, chest and eyes with no block in the way): the real position. */
        BODY,
        /** The name over the head, also through blocks. */
        NAMETAG,
        /** The particles of the invisibility potion. */
        PARTICLES,
        /** A vibration (steps, landing, a block broken or placed...). */
        SOUND
    }

    /**
     * This sighting replaces {@code old} as what is known: the newer one wins, unless the two are close in time and
     * the older one is more precise (a sound right after the body was seen doesn't blur it).
     */
    public boolean replaces(Sighting old) {
        if (old == null) {
            return true;
        }
        long dt = tick - old.tick;
        if (dt > PerceptionTuning.PRECISION_WINDOW_TICKS) {
            return true;
        }
        if (dt < -PerceptionTuning.PRECISION_WINDOW_TICKS) {
            return false;
        }
        return kind.ordinal() < old.kind.ordinal() || (kind == old.kind && dt >= 0);
    }
}
