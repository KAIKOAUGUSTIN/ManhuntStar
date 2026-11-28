package dev.manhuntstar.bot.perception;

import static dev.manhuntstar.bot.perception.PerceptionTuning.TOWER_BEARING_DEGREES;
import static dev.manhuntstar.bot.perception.PerceptionTuning.TOWER_SIGHT;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.Collection;

/**
 * A tall tower of blocks placed by players stands out from far away: with the compass pointing at it, the player
 * hiding is up there (otherwise, in a manhunt, they are almost always underground).
 */
public final class TowerSpotter {

    private TowerSpotter() {
    }

    /**
     * The top of the closest tower that a bot at {@code eye}, looking along {@code yaw}/{@code pitch}, sees in the
     * compass's direction ({@code bearing}); null when none.
     */
    public static Pos spot(GameWorld w, Vec3 eye, float yaw, float pitch, Vec3 bearing, Collection<Pos> towers) {
        if (bearing == null || bearing.equals(Vec3.ZERO)) {
            return null;
        }
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Pos top : towers) {
            Vec3 standing = standingOn(top);
            Vec3 flat = standing.subtract(eye).horizontal();
            double d = flat.length();
            if (d > TOWER_SIGHT || d >= bestDist || !inCompassDirection(eye, top, bearing)) {
                continue;
            }
            if (!stillThere(w, top) || !Vision.inView(eye, yaw, pitch, standing) || !WorldUtil.clearLine(w, eye, standing)) {
                continue;
            }
            best = top;
            bestDist = d;
        }
        return best;
    }

    /** The tower is in the compass's direction (or the bot is right at its foot). */
    public static boolean inCompassDirection(Vec3 from, Pos top, Vec3 bearing) {
        Vec3 flat = standingOn(top).subtract(from).horizontal();
        return flat.lengthSqr() < 4.0 || (bearing != null && !bearing.equals(Vec3.ZERO)
                && Compass.angle(flat, bearing) <= TOWER_BEARING_DEGREES);
    }

    /** The top block is still there (an explosion takes blocks without telling who placed them). */
    public static boolean stillThere(GameWorld w, Pos top) {
        return w.isChunkLoaded(top.x() >> 4, top.z() >> 4) && !w.type(top.x(), top.y(), top.z()).is(BlockTrait.AIR);
    }

    /** Where someone standing on the top would be. */
    public static Vec3 standingOn(Pos top) {
        return Vec3.bottomCenter(top.above()).add(0, 0.2, 0);
    }
}
