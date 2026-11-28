package dev.manhuntstar.bot.perception;

import static dev.manhuntstar.bot.perception.PerceptionTuning.SOUND_RANGE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.SOUND_RANGE_SPRINTING;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the bots hear: the vibrations a sculk sensor would pick up from a player (the platform reports them already
 * filtered by the game's rules, e.g. no steps while sneaking). Short range, longer with the player sprinting, and
 * wool between the source and the bot stops them, as it does for the sculk.
 */
public final class Hearing {

    private Hearing() {
    }

    /** At most one sound per player every {@link PerceptionTuning#SOUND_INTERVAL_TICKS} ticks (each step is a vibration). */
    public static final class Throttle {

        private final Map<UUID, Long> last = new HashMap<>();

        /** The player's vibration on {@code now} is heard (and the next ones only after the interval). */
        public boolean allow(UUID player, long now) {
            Long t = last.get(player);
            if (t != null && now >= t && now - t < PerceptionTuning.SOUND_INTERVAL_TICKS) {
                return false;
            }
            last.put(player, now);
            return true;
        }

        public void clear() {
            last.clear();
        }
    }

    /** A bot with its ears at {@code ear} hears a vibration at {@code source}. */
    public static boolean hears(GameWorld w, Vec3 ear, Vec3 source, boolean sprinting) {
        double range = sprinting ? SOUND_RANGE_SPRINTING : SOUND_RANGE;
        return ear.distanceToSqr(source) <= range * range && !occluded(w, source, ear);
    }

    /** A block that stops vibrations (wool) on the way between the two points (their own blocks don't count). */
    static boolean occluded(GameWorld w, Vec3 from, Vec3 to) {
        Pos a = from.toPos();
        Pos b = to.toPos();
        Vec3 d = to.subtract(from);
        int steps = (int) Math.ceil(d.length() / 0.25);
        Pos last = null;
        for (int i = 1; i < steps; i++) {
            Pos p = from.add(d.scale(i / (double) steps)).toPos();
            if (p.equals(last) || p.equals(a) || p.equals(b)) {
                continue;
            }
            last = p;
            if (w.type(p.x(), p.y(), p.z()).is(BlockTrait.OCCLUDES_VIBRATIONS)) {
                return true;
            }
        }
        return false;
    }
}
