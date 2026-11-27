package dev.manhuntstar.bot.perception;

import static dev.manhuntstar.bot.perception.PerceptionTuning.APPROX_TICKS;
import static dev.manhuntstar.bot.perception.PerceptionTuning.BODY_RANGE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.FLIP_FORGET_DISTANCE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.LOOK_INTERVAL_TICKS;
import static dev.manhuntstar.bot.perception.PerceptionTuning.MEMORY_TICKS;
import static dev.manhuntstar.bot.perception.PerceptionTuning.PORTAL_DISCOVERY;
import static dev.manhuntstar.bot.perception.PerceptionTuning.TOWER_INTERVAL_TICKS;
import static dev.manhuntstar.bot.perception.PerceptionTuning.VISIBLE_TICKS;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.perception.Sighting.Kind;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One bot's eyes and compass. Every few ticks (the bots take turns) it looks at the hunted players of its world and
 * tells its team what it saw ({@link TeamIntel}); on every tick it reads the compass, notices when it passed over
 * the target, looks for towers in the compass's direction and for the portal the compass points at.
 * {@link #about} is what the bot knows about its target: the real position only while someone on the team sees it.
 */
public final class Perception {

    private final Bot bot;
    private final CompassFlip flip = new CompassFlip();
    /** The random part of the guess about each player, kept while the bot keeps noticing them. */
    private final Map<UUID, double[]> noise = new HashMap<>();
    private Vec3 bearing;
    private Vec3 passedOver;
    private Pos tower;
    private UUID knowledgeFor;
    private long knowledgeTick = Long.MIN_VALUE;
    private Knowledge knowledge = Knowledge.NOTHING;

    public Perception(Bot bot) {
        this.bot = bot;
    }

    private BotManager manager() {
        return bot.manager();
    }

    /** The compass's direction to the target (a unit vector, ZERO right above or below it), or null when it spins. */
    public Vec3 bearing() {
        return bearing;
    }

    /** Where the bot passed over the target (the compass turned around), or null. */
    public Vec3 passedOver() {
        return passedOver;
    }

    /** Nothing was found where the compass turned: the bot stops digging there. */
    public void forgetPassedOver() {
        passedOver = null;
        flip.clear();
    }

    /** The top of the tower the target is probably on, or null. */
    public Pos tower() {
        return tower;
    }

    public void forgetTower() {
        tower = null;
    }

    /** A new hunt: what the bot noticed in the last one doesn't count. */
    public void reset() {
        flip.clear();
        noise.clear();
        bearing = null;
        passedOver = null;
        tower = null;
        knowledgeTick = Long.MIN_VALUE;
    }

    /** Called every tick with the bot's target (null without one). */
    public void tick(GamePlayer target) {
        GameWorld w = bot.world();
        if (w == null || !bot.isAlive()) {
            return;
        }
        long now = Platform.server().currentTick();
        if (bot.tickDelay(LOOK_INTERVAL_TICKS)) {
            look(w, now);
        }
        if (target == null || target.isDead()) {
            bearing = null;
            passedOver = null;
            tower = null;
            flip.clear();
            return;
        }
        boolean sameWorld = target.world().id().equals(w.id());
        Vec3 at = Compass.pointsAt(w.id(), target, manager().travel());
        bearing = at == null ? null : Compass.bearing(bot.pos(), at);
        if (sameWorld) {
            Vec3 passed = flip.read(bot.pos(), bearing, now);
            if (passed != null) {
                passedOver = passed;
            }
            if (passedOver != null && passedOver.horizontalDistance(bot.pos()) > FLIP_FORGET_DISTANCE) {
                passedOver = null;
            }
            if (bot.tickDelay(TOWER_INTERVAL_TICKS)) {
                lookForTower(w);
            }
        } else {
            flip.clear();
            passedOver = null;
            tower = null;
            findPortal(w, target, now);
        }
    }

    /** Looks at every hunted player of the bot's world and tells the team what it saw. */
    private void look(GameWorld w, long now) {
        Vec3 eye = bot.eyePos();
        float yaw = bot.yaw();
        float pitch = bot.pitch();
        double rangeSq = (BODY_RANGE + 2) * (BODY_RANGE + 2);
        for (GamePlayer p : w.players()) {
            if (eye.distanceToSqr(p.position()) > rangeSq || !manager().isEligibleTarget(p)) {
                noise.remove(p.uuid());
                continue;
            }
            PlayerView v = manager().playerView(p);
            Kind k = Vision.look(w, eye, yaw, pitch, v);
            if (k == null) {
                noise.remove(p.uuid());
                continue;
            }
            double[] n = noise.computeIfAbsent(p.uuid(), id -> randomNoise());
            manager().intel().report(bot.groupName(), new Sighting(p.uuid(), w.id(), Vision.estimate(eye, v.feet(), k, n), k, now));
        }
    }

    /** Three numbers from -1 to 1. */
    public static double[] randomNoise() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new double[]{r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)};
    }

    private void lookForTower(GameWorld w) {
        if (tower != null && (!TowerSpotter.stillThere(w, tower) || !TowerSpotter.inCompassDirection(bot.pos(), tower, bearing))) {
            tower = null;
        }
        if (tower == null) {
            tower = TowerSpotter.spot(w, bot.eyePos(), bot.yaw(), bot.pitch(), bearing, manager().blockTracker().towers(w));
        }
    }

    /** The target is in another world: near the portal the compass points at, the bot sees it and the team learns where they went. */
    private void findPortal(GameWorld w, GamePlayer target, long now) {
        PlayerTravel.Departure d = manager().travel().departureFrom(target.uuid(), w.id());
        if (d == null || !d.viaPortal() || knowsDeparture(target, d)) {
            return;
        }
        Vec3 portal = Vec3.center(d.portal());
        if (bot.eyePos().distanceTo(portal) <= PORTAL_DISCOVERY && WorldUtil.clearLine(w, bot.eyePos(), portal)) {
            manager().intel().discover(bot.groupName(), target.uuid(), d.tick());
        }
    }

    /** The team knows the target went away through the portal of this departure (the game announced it, or a bot found it). */
    public boolean knowsDeparture(GamePlayer target, PlayerTravel.Departure d) {
        return manager().intel().knowsDeparture(bot.groupName(), manager()::alliedGroups, target.uuid(), d.tick());
    }

    /** What the bot knows about where the target is now. */
    public Knowledge about(GamePlayer target) {
        if (target == null || bot.world() == null) {
            return Knowledge.NOTHING;
        }
        long now = Platform.server().currentTick();
        if (knowledgeTick == now && target.uuid().equals(knowledgeFor)) {
            return knowledge;
        }
        UUID world = bot.world().id();
        Sighting s = manager().intel().best(bot.groupName(), manager()::alliedGroups, target.uuid(), now, MEMORY_TICKS);
        Knowledge k;
        if (s != null && s.world().equals(world)) {
            long age = now - s.tick();
            if (s.kind() == Kind.BODY && age <= VISIBLE_TICKS && target.world().id().equals(world) && !target.isDead()) {
                k = new Knowledge(Knowledge.State.VISIBLE, target.position(), age, s);
            } else {
                k = new Knowledge(age <= APPROX_TICKS ? Knowledge.State.APPROX : Knowledge.State.LAST, s.pos(), age, s);
            }
        } else {
            k = bearing != null ? Knowledge.COMPASS_ONLY : Knowledge.NOTHING;
        }
        knowledgeFor = target.uuid();
        knowledgeTick = now;
        knowledge = k;
        return k;
    }

    /** The bot got to the clue and the target wasn't there: the team drops it. */
    public void dismiss(GamePlayer target, Sighting s) {
        manager().intel().dismiss(bot.groupName(), manager()::alliedGroups, target.uuid(), s.tick());
        knowledgeTick = Long.MIN_VALUE;
    }
}
