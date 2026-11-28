package dev.manhuntstar.bot.ai;

import static dev.manhuntstar.bot.perception.PerceptionTuning.COMPASS_REPLAN_DEGREES;
import static dev.manhuntstar.bot.perception.PerceptionTuning.COMPASS_REPLAN_DISTANCE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.COMPASS_STEP;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.perception.Compass;
import dev.manhuntstar.bot.perception.Knowledge;
import dev.manhuntstar.bot.perception.Perception;
import dev.manhuntstar.bot.perception.PlayerTravel;
import dev.manhuntstar.bot.perception.TowerSpotter;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;

/**
 * Where the bot goes when nobody on its team sees the target, in this order: the portal the target went through
 * (when the team knows it), where the target was last seen or heard, the tower the compass points at, the spot where
 * the compass turned around (the target is under it: the bot digs), and finally a point a few blocks ahead along the
 * compass.
 */
final class Search {

    enum Kind {
        /** The portal the target went through. */
        PORTAL,
        /** The portal the bot itself came through (the compass spins: the target left without a portal). */
        WAY_BACK,
        /** Where the target was seen or heard. */
        CLUE,
        /** On top of the tower the compass points at. */
        TOWER,
        /** Where the compass turned around: the target is underground there. */
        DIG,
        /** A point ahead along the compass. */
        COMPASS
    }

    /**
     * Where to go and why. For {@link Kind#PORTAL} and {@link Kind#WAY_BACK}, {@code destWorld}/{@code destPos} are
     * where going through the portal leads.
     */
    record Goal(Kind kind, Vec3 pos, GameWorld destWorld, Vec3 destPos) {

        Goal(Kind kind, Vec3 pos) {
            this(kind, pos, null, null);
        }
    }

    private final Bot bot;
    private Vec3 compassPoint;
    private Vec3 compassPointBearing;

    Search(Bot bot) {
        this.bot = bot;
    }

    /** Where to go after the target that the team doesn't see now; null when there is nowhere (the compass spins). */
    Goal choose(GamePlayer target, Knowledge know) {
        GameWorld w = bot.world();
        Perception per = bot.perception();
        boolean elsewhere = !target.world().id().equals(w.id());
        PlayerTravel.Departure d = elsewhere ? bot.manager().travel().departureFrom(target.uuid(), w.id()) : null;
        // a portal broken since is no way to follow them: the bot goes on as if they had left without one
        boolean viaPortal = d != null && d.viaPortal() && portalStands(w, Vec3.bottomCenter(d.portal()));
        Bot.WayBack back = elsewhere && !viaPortal ? bot.wayBack(w) : null;
        if (back != null && !portalStands(w, back.portal())) {
            back = null;
        }
        GameWorld backWorld = back == null ? null : Platform.server().world(back.backWorld());
        Vec3 bearing = per.bearing();
        Kind kind = pick(elsewhere, viaPortal, viaPortal && per.knowsDeparture(target, d), backWorld != null,
                know.knowsPosition(), per.tower() != null, per.passedOver() != null, bearing != null && !bearing.equals(Vec3.ZERO));
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case PORTAL -> new Goal(kind, Vec3.bottomCenter(d.portal()), d.to(), d.toPos());
            case WAY_BACK -> new Goal(kind, back.portal(), backWorld, back.backPos());
            case CLUE -> new Goal(kind, know.pos());
            case TOWER -> new Goal(kind, TowerSpotter.standingOn(per.tower()));
            case DIG -> new Goal(kind, per.passedOver());
            case COMPASS -> new Goal(kind, compassPoint(bearing));
        };
    }

    /**
     * The order of the goals: with the target in another world, the portal they took once the team knows it, or the
     * way back when they left without a portal; then the clue; in the target's world, the tower and the spot to dig;
     * the compass last. Null when there is nothing to go by.
     */
    static Kind pick(boolean elsewhere, boolean viaPortal, boolean portalKnown, boolean wayBack, boolean knowsPosition,
                     boolean tower, boolean passedOver, boolean bearing) {
        if (elsewhere && viaPortal && portalKnown) {
            return Kind.PORTAL;
        }
        if (elsewhere && !viaPortal && wayBack) {
            return Kind.WAY_BACK;
        }
        if (knowsPosition) {
            return Kind.CLUE;
        }
        if (!elsewhere && tower) {
            return Kind.TOWER;
        }
        if (!elsewhere && passedOver) {
            return Kind.DIG;
        }
        return bearing ? Kind.COMPASS : null;
    }

    /** The Nether or End portal block at {@code at} (or just above or below it), or null when there's none. */
    static BlockType portalAt(GameWorld w, Vec3 at) {
        Pos c = at.toPos();
        for (int dy = -1; dy <= 1; dy++) {
            BlockType t = w.type(c.x(), c.y() + dy, c.z());
            if (t == Blocks.NETHER_PORTAL || t == Blocks.END_PORTAL) {
                return t;
            }
        }
        return null;
    }

    /** The portal at {@code at} is still there, as far as the bot can tell (an unloaded chunk counts as yes). */
    private static boolean portalStands(GameWorld w, Vec3 at) {
        Pos c = at.toPos();
        return !w.isChunkLoaded(c.x() >> 4, c.z() >> 4) || portalAt(w, at) != null;
    }

    /** A point {@link dev.manhuntstar.bot.perception.PerceptionTuning#COMPASS_STEP} blocks ahead along the compass, on the ground. */
    private Vec3 compassPoint(Vec3 bearing) {
        Vec3 here = bot.pos();
        if (compassPoint == null || compassPointBearing == null || Compass.angle(bearing, compassPointBearing) > COMPASS_REPLAN_DEGREES
                || here.horizontalDistance(compassPoint) < COMPASS_REPLAN_DISTANCE) {
            Vec3 ahead = here.add(bearing.scale(COMPASS_STEP));
            compassPoint = onGround(ahead);
            compassPointBearing = bearing;
        }
        return compassPoint;
    }

    /**
     * The point moved to the ground of its column: the surface, or the ground closest to the bot's height in a world
     * with a ceiling and with the bot underground.
     */
    private Vec3 onGround(Vec3 p) {
        GameWorld w = bot.world();
        int x = (int) Math.floor(p.x());
        int z = (int) Math.floor(p.z());
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return p;
        }
        Pos here = bot.blockPos();
        boolean underground = here.y() < WorldUtil.surfaceYNoLeaves(w, here.x(), here.z()) - 4;
        if (!w.hasCeiling() && !underground) {
            return new Vec3(p.x(), WorldUtil.surfaceYNoLeaves(w, x, z), p.z());
        }
        // under a ceiling (the Nether) or underground: the ground closest to the bot's height
        int ref = here.y();
        for (int i = 0; i <= 24; i++) {
            int y = ref + ((i + 1) >> 1) * ((i & 1) == 1 ? 1 : -1);
            if (WorldUtil.isSafeStand(w, new Pos(x, y, z))) {
                return new Vec3(p.x(), y, p.z());
            }
        }
        return p;
    }

    /** The compass point is made again on the next look (the bot was taken somewhere else). */
    void reset() {
        compassPoint = null;
        compassPointBearing = null;
    }
}
