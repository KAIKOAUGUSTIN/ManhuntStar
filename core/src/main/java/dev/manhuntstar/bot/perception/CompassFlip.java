package dev.manhuntstar.bot.perception;

import static dev.manhuntstar.bot.perception.PerceptionTuning.FLIP_DEGREES;
import static dev.manhuntstar.bot.perception.PerceptionTuning.FLIP_MAX_TRAVEL;
import static dev.manhuntstar.bot.perception.PerceptionTuning.FLIP_SAMPLE_TICKS;

import dev.manhuntstar.util.Vec3;
import java.util.ArrayDeque;

/**
 * Notices when the compass turns around while the bot hardly moved: it just walked over the player (who is under it,
 * hidden). It doesn't estimate any distance; it only catches the moment of passing over, as a person holding the
 * compass would.
 */
public final class CompassFlip {

    private record Reading(Vec3 pos, Vec3 bearing, long tick) {
    }

    private static final int MAX_READINGS = 8;

    private final ArrayDeque<Reading> readings = new ArrayDeque<>();

    /**
     * A new compass reading ({@code bearing} from {@link Compass#bearing}, null when it spins).
     *
     * @return where the bot passed over the player, or null
     */
    public Vec3 read(Vec3 pos, Vec3 bearing, long now) {
        if (bearing == null) {
            readings.clear();
            return null;
        }
        if (bearing.equals(Vec3.ZERO)) {
            // right above (or below) the player
            readings.clear();
            return pos;
        }
        Reading lastReading = readings.peekLast();
        if (lastReading != null && now - lastReading.tick < FLIP_SAMPLE_TICKS) {
            return null;
        }
        Vec3 passed = null;
        for (Reading r : readings) {
            if (r.pos.horizontalDistance(pos) <= FLIP_MAX_TRAVEL && Compass.angle(r.bearing, bearing) >= FLIP_DEGREES) {
                passed = r.pos.add(pos).scale(0.5);
                break;
            }
        }
        if (passed != null) {
            readings.clear();
            return passed;
        }
        readings.addLast(new Reading(pos, bearing, now));
        while (readings.size() > MAX_READINGS || readings.peekFirst().pos.horizontalDistance(pos) > FLIP_MAX_TRAVEL * 2) {
            readings.removeFirst();
        }
        return null;
    }

    public void clear() {
        readings.clear();
    }
}
