package dev.manhuntstar.bot.perception;

import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;

/**
 * The hunter's compass: only the horizontal direction to the player, with no distance and no height. With the
 * player in another world it points at the portal they left this one through; with no such portal it has nothing to
 * point at (it spins).
 */
public final class Compass {

    private Compass() {
    }

    /** What the compass of someone in {@code world} points at for this player, or null when it spins. */
    public static Vec3 pointsAt(UUID world, GamePlayer target, PlayerTravel travel) {
        return pointsAt(world, target.uuid(), target.world().id(), target.position(), travel);
    }

    /** The same, for the player {@code player} who is in {@code playerWorld} at {@code playerPos}. */
    public static Vec3 pointsAt(UUID world, UUID player, UUID playerWorld, Vec3 playerPos, PlayerTravel travel) {
        if (playerWorld.equals(world)) {
            return playerPos;
        }
        PlayerTravel.Departure d = travel.departureFrom(player, world);
        return d != null && d.viaPortal() ? Vec3.bottomCenter(d.portal()) : null;
    }

    /** The horizontal direction from {@code from} to {@code to}: a unit vector, ZERO right above or below it. */
    public static Vec3 bearing(Vec3 from, Vec3 to) {
        Vec3 h = to.subtract(from).horizontal();
        return h.lengthSqr() < 0.0625 ? Vec3.ZERO : h.normalize();
    }

    /** Angle in degrees between two directions. */
    public static double angle(Vec3 a, Vec3 b) {
        double dot = a.dot(b) / Math.max(1.0E-9, a.length() * b.length());
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }
}
