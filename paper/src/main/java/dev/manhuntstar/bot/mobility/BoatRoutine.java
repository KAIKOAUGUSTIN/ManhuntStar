package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameBoat;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

/**
 * Boat: chasing someone in a boat (or, from phase 6 on, someone swimming), placing its own boat on the
 * water and rowing along a path on the surface; getting off near the bank, ramming the target's boat to
 * attack or boarding it as a passenger. It also crosses by boat the stretch of water the A* chose instead
 * of going around a lake. The boat is steered as the client would (±1°/tick accumulated turn, 0.04
 * forward push), because only players send paddle input.
 */
public final class BoatRoutine {

    private static final double SEARCH_RADIUS_SQ = 40.0 * 40.0;
    /** With no new request from the path for this long, the crossing on foot is canceled. */
    private static final int CROSSING_REQUEST_TTL = 5;
    /** After a failed crossing, the A* doesn't plan boats for this long (30 s). */
    private static final int CROSSING_FAIL_BLOCK_TICKS = 600;
    /** Extra cost per step in water touching the bank: the hull scrapes the ground and the boat nearly stops. */
    private static final double SHORE_PENALTY = 3.0;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final Bot bot;
    private UUID riderBoat;
    private UUID passengerHost;
    private boolean assaultActive;
    private UUID assaultTarget;
    private int actionCooldown;
    private int reboardCooldown;
    private int noMoveTicks;
    private float deltaRotation;
    private List<Pos> surfacePath = Collections.emptyList();
    private int surfaceIndex;
    private int surfaceRecalc;
    private Pos surfaceStart;
    private Pos surfaceGoal;
    /** Crossing requested by the path: where to get off, who is being followed and the deadline. */
    private Pos crossingGoal;
    private GameLiving crossingFollow;
    private int crossingRequestTick;
    private int crossingTicks;
    private int crossingBudget;

    public BoatRoutine(Bot bot) {
        this.bot = bot;
    }

    /**
     * The path has ahead a stretch of water that the A* decided to cross by boat, ending at
     * {@code goal}. The request holds while it's repeated; the bot boards when it reaches the edge.
     */
    public void requestCrossing(Pos goal, GameLiving follow) {
        if (!bot.settings().allowBoat.on()) {
            return;
        }
        if (crossingGoal == null) {
            crossingTicks = 0;
            crossingBudget = budgetFor(bot.pos(), goal);
        }
        crossingGoal = goal;
        crossingFollow = follow;
        crossingRequestTick = Platform.server().currentTick();
    }

    public boolean isCrossing() {
        return crossingGoal != null;
    }

    /** Crossing deadline: 10 s of slack plus 6 ticks per block (the boat does ~2.5 ticks per block). */
    private int budgetFor(Vec3 from, Pos goal) {
        return crossingTicks + 200 + (int) (from.distanceTo(Vec3.bottomCenter(goal)) * 6);
    }

    public void tickCooldowns() {
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (reboardCooldown > 0) {
            reboardCooldown--;
        }
        if (riderBoat != null && bot.tickDelay(20) && !(Platform.server().entity(riderBoat) instanceof GameBoat b && b.isValid())) {
            riderBoat = null;
            noMoveTicks = 0;
            clearAssault();
        }
    }

    public boolean isRiderBoat(GameBoat b) {
        return riderBoat != null && riderBoat.equals(b.uuid());
    }

    private GameBoat riderBoatEntity() {
        return riderBoat != null && Platform.server().entity(riderBoat) instanceof GameBoat b && b.isValid() ? b : null;
    }

    /** @return true if the boat controlled the bot this tick. */
    public boolean handle() {
        GameEntity vehicle = bot.vehicle();
        if (!bot.settings().allowBoat.on()) {
            if (vehicle instanceof GameBoat b && isRiderBoat(b)) {
                cleanup(true);
            }
            clearAssault();
            clearCrossing();
            return false;
        }
        GamePlayer boating = findNearbyBoatingPlayer();
        if (crossingGoal != null) {
            if (boating == null) {
                return handleCrossing(vehicle);
            }
            // someone in a boat nearby: the usual boat chase applies
            clearCrossing();
        }
        GamePlayer chase = primaryChaseTarget();
        boolean phase6WaterChase = bot.progression().phase() >= 6 && chase != null && !isOutOfWater(chase);
        boolean deferToVertical = boating == null && chase != null && shouldDeferToVerticalChase(chase);
        GamePlayer opsTarget = boating != null ? boating : (phase6WaterChase && !deferToVertical ? chase : null);
        if (assaultActive && (boating == null || assaultTarget == null || !assaultTarget.equals(boating.uuid()))) {
            clearAssault();
        }
        if (vehicle instanceof GameBoat mounted) {
            if (!isRiderBoat(mounted)) {
                return handleForeignBoat(mounted, chase);
            }
            return handleRiding(mounted, boating, chase, deferToVertical);
        }
        passengerHost = null;
        if (opsTarget == null) {
            clearAssault();
            return false;
        }
        if (assaultActive && boating != null && assaultTarget != null && assaultTarget.equals(boating.uuid())) {
            return bot.distanceTo(boating) > 7.0 && tryReenterParkedBoat(boating);
        }
        if (reboardCooldown > 0) {
            return false;
        }
        if (boating != null && boating.vehicle() instanceof GameBoat targetBoat && tryBoardAsPassenger(boating, targetBoat)) {
            return true;
        }
        if (tryReenterParkedBoat(opsTarget)) {
            return true;
        }
        if (actionCooldown > 0) {
            return false;
        }
        Pos water = findBoatPlacementWater();
        return water != null && placeAndBoard(water);
    }

    private boolean placeAndBoard(Pos water) {
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        bot.setItem(EquipSlot.HAND, GameItem.of(Items.OAK_BOAT));
        bot.lookAt(Vec3.center(water));
        GameWorld w = bot.world();
        Place loc = new Place(w, water.x() + 0.5, water.y() + 1.0, water.z() + 0.5, bot.yaw(), 0);
        if (!(w.spawnBoat(loc) instanceof GameBoat boat)) {
            return false;
        }
        bot.brain().mlg().trackSpawnedBoat(boat);
        riderBoat = boat.uuid();
        clearAssault();
        noMoveTicks = 0;
        actionCooldown = 8;
        bot.swing();
        boat.addPassenger(bot.entity());
        bot.motor().resetFallTracking();
        return true;
    }

    // ------------------------------------------------------------------ crossing requested by the path

    private boolean handleCrossing(GameEntity vehicle) {
        GameLiving follow = crossingFollow;
        boolean followGone = follow == null || follow.isDead() || !follow.world().equals(bot.world());
        if (vehicle instanceof GameBoat mounted && isRiderBoat(mounted)) {
            crossingTicks++;
            if (!followGone && bot.tickDelay(10)) {
                // the path is recomputed from the boat; the end of the water stretch may have changed
                Navigator nav = bot.brain().navigator();
                nav.waypoint(follow.position());
                Pos next = nav.boatCrossingGoal(true);
                if (next != null && !next.equals(crossingGoal)) {
                    crossingGoal = next;
                    crossingBudget = budgetFor(mounted.position(), next);
                }
            }
            Place bl = mounted.place();
            Vec3 goal = Vec3.bottomCenter(crossingGoal);
            boolean arrived = Math.hypot(goal.x() - bl.x(), goal.z() - bl.z()) < 2.5;
            boolean close = !followGone && bl.distance(follow.place()) < 4.0;
            boolean stuck = noMoveTicks >= 40;
            boolean late = crossingTicks > crossingBudget;
            if (followGone || close || stuck || late || shouldDismountNearShore(mounted) || (arrived && isBoatCloseToLand(mounted))) {
                finishCrossing(stuck || late);
                return false;
            }
            if (arrived) {
                // the path ends in the middle of the water (target still far): waits for the next stretch
                deltaRotation = 0;
                bot.motor().stopWalk();
                return true;
            }
            steerTowards(mounted, goal);
            return true;
        }
        if (vehicle != null || followGone || Platform.server().currentTick() - crossingRequestTick > CROSSING_REQUEST_TTL) {
            // the path stopped asking for the crossing (took a detour, lost the target) or the bot mounted something else
            clearCrossing();
            return false;
        }
        if (actionCooldown > 0 || reboardCooldown > 0) {
            return false;
        }
        // on foot: boards when at the edge; until then the normal path takes the bot to the water
        Pos water = findBoatPlacementWater();
        return water != null && placeAndBoard(water);
    }

    private void finishCrossing(boolean failed) {
        cleanup(true);
        bot.inventory().restorePreferredMainHand();
        clearCrossing();
        Navigator nav = bot.brain().navigator();
        if (failed) {
            nav.blockBoats(CROSSING_FAIL_BLOCK_TICKS);
        }
        // new path from the bank: the old one still pointed at the start of the water
        nav.reset();
    }

    private void clearCrossing() {
        crossingGoal = null;
        crossingFollow = null;
    }

    private boolean handleForeignBoat(GameBoat mounted, GamePlayer chase) {
        if (mounted.passengers().indexOf(bot.entity()) == 0) {
            forceDismount(mounted);
            return false;
        }
        if (passengerHost == null && chase != null && chase.vehicle() == mounted) {
            passengerHost = chase.uuid();
        }
        boolean hostInBoat = passengerHost != null && mounted.passengers().stream().anyMatch(e -> e.uuid().equals(passengerHost));
        if (!hostInBoat || chase == null || chase.isDead() || chase.vehicle() != mounted || isOutOfWater(chase)) {
            forceDismount(mounted);
            return false;
        }
        return true;
    }

    private boolean handleRiding(GameBoat mounted, GamePlayer boating, GamePlayer chase, boolean deferToVertical) {
        if (deferToVertical || (boating == null && chase != null && isOutOfWater(chase))
                || (chase != null && isOutOfWater(chase) && isBoatCloseToLand(mounted))) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            return false;
        }
        if (chase != null && !chase.isDead() && !(chase.vehicle() instanceof GameBoat)
                && mounted.place().distance(chase.place()) <= 3.0) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            if (bot.distanceTo(chase) <= 3.3) {
                bot.combat().attack(chase);
            }
            return false;
        }
        if (boating != null && boating.vehicle() instanceof GameBoat targetBoat
                && mounted.place().distance(targetBoat.place()) <= 3.2) {
            if (tryBoardAsPassenger(boating, targetBoat)) {
                return true;
            }
            assaultActive = true;
            assaultTarget = boating.uuid();
            park(mounted, boating.position());
            if (bot.onGround()) {
                bot.motor().jump();
            }
            if (bot.distanceTo(boating) <= 3.3) {
                bot.combat().attack(boating);
            }
            return false;
        }
        if (shouldDismountNearShore(mounted)) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            return false;
        }
        Vec3 steer = null;
        if (boating != null) {
            steer = boating.vehicle() instanceof GameBoat tb ? tb.position() : boating.position();
        }
        if (steer == null && chase != null && !chase.isDead()) {
            steer = chase.position();
        }
        if (steer == null) {
            deltaRotation = 0;
            clearSurfacePath();
            return true;
        }
        steerTowards(mounted, steer);
        if (noMoveTicks >= 40 && shouldDismountNearShore(mounted)) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ steering

    private void steerTowards(GameBoat boat, Vec3 chaseTarget) {
        Vec3 nav = surfaceWaypoint(boat, chaseTarget);
        Place bl = boat.place();
        Vec3 to = new Vec3(nav.x() - bl.x(), 0, nav.z() - bl.z());
        if (to.lengthSqr() < 0.09) {
            deltaRotation = 0;
            bot.motor().stopWalk();
            return;
        }
        bot.lookAt(nav.add(0, 1, 0));
        Vec3 desired = to.normalize();
        float desiredYaw = desired.yaw();
        float yawDelta = Bot.wrapDegrees(desiredYaw - bl.yaw());
        float abs = Math.abs(yawDelta);
        boolean right = yawDelta > 4f;
        boolean left = yawDelta < -4f;
        boolean forward = abs <= 70f || abs > 125f;
        if (left) {
            deltaRotation -= 1f;
        }
        if (right) {
            deltaRotation += 1f;
        }
        float accel = 0f;
        if (right != left && !forward) {
            accel += 0.005f;
        }
        float yaw = bl.yaw() + deltaRotation;
        boat.setRotation(yaw, 0);
        if (forward) {
            accel += 0.04f;
        }
        double rad = Math.toRadians(yaw);
        // the speed below is the one after the push, as before (Bukkit's Vector.add changed the vector itself)
        Vec3 v = boat.velocity().add(-Math.sin(rad) * accel, 0, Math.cos(rad) * accel);
        boat.setVelocity(v);
        deltaRotation *= 0.9f;
        double speed = Math.hypot(v.x(), v.z());
        noMoveTicks = forward && speed < 0.03 ? noMoveTicks + 1 : 0;
        bot.motor().stopWalk();
    }

    private Vec3 surfaceWaypoint(GameBoat boat, Vec3 chaseTarget) {
        Pos start = nearestSurfaceWater(boat.blockPos(), 2, 3);
        Pos goal = nearestSurfaceWater(chaseTarget.toPos(), 3, 8);
        if (start == null || goal == null) {
            clearSurfacePath();
            return chaseTarget;
        }
        if (surfaceRecalc > 0) {
            surfaceRecalc--;
        }
        boolean recalc = surfacePath.isEmpty() || surfaceIndex >= surfacePath.size() || surfaceRecalc <= 0
                || surfaceStart == null || surfaceGoal == null || start.manhattan(surfaceStart) > 3 || goal.manhattan(surfaceGoal) > 3;
        if (recalc) {
            surfacePath = computeSurfacePath(start, goal);
            surfaceIndex = 0;
            surfaceStart = start;
            surfaceGoal = goal;
            surfaceRecalc = 12;
        }
        if (surfacePath.isEmpty()) {
            return Vec3.bottomCenter(goal);
        }
        Place bl = boat.place();
        while (surfaceIndex < surfacePath.size()) {
            Vec3 c = Vec3.bottomCenter(surfacePath.get(surfaceIndex));
            double dx = bl.x() - c.x();
            double dz = bl.z() - c.z();
            if (dx * dx + dz * dz > 1.8) {
                return c;
            }
            surfaceIndex++;
        }
        return Vec3.bottomCenter(goal);
    }

    private record Node(Pos pos, double g, double f, Node parent) {
    }

    private List<Pos> computeSurfacePath(Pos start, Pos goal) {
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Double> bestG = new HashMap<>();
        Map<Long, Boolean> shore = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        Node startNode = new Node(start, 0, heuristic(start, goal), null);
        open.add(startNode);
        bestG.put(start.asLong(), 0.0);
        Node best = startNode;
        int explored = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!open.isEmpty() && explored < 2500) {
            Node cur = open.poll();
            explored++;
            if (!closed.add(cur.pos.asLong())) {
                continue;
            }
            if (heuristic(cur.pos, goal) < heuristic(best.pos, goal)) {
                best = cur;
            }
            if (Math.abs(cur.pos.x() - goal.x()) <= 1 && Math.abs(cur.pos.z() - goal.z()) <= 1) {
                return reconstruct(cur);
            }
            for (int[] d : dirs) {
                Pos n = surfaceWaterInColumn(cur.pos.x() + d[0], cur.pos.z() + d[1], cur.pos.y(), 3);
                if (n == null) {
                    continue;
                }
                double step = (d[0] != 0 && d[1] != 0 ? 1.414 : 1.0) + Math.abs(n.y() - cur.pos.y()) * 0.2;
                if (touchesShore(n, shore)) {
                    step += SHORE_PENALTY;
                }
                double ng = cur.g + step;
                Double known = bestG.get(n.asLong());
                if (known != null && known <= ng) {
                    continue;
                }
                bestG.put(n.asLong(), ng);
                open.add(new Node(n, ng, ng + heuristic(n, goal), cur));
            }
        }
        return best == startNode ? Collections.emptyList() : reconstruct(best);
    }

    /** One of the four neighbors isn't water (an unloaded chunk doesn't count). */
    private boolean touchesShore(Pos p, Map<Long, Boolean> cache) {
        Boolean known = cache.get(p.asLong());
        if (known != null) {
            return known;
        }
        GameWorld w = bot.world();
        boolean result = false;
        for (int[] d : CARDINALS) {
            Pos side = p.offset(d[0], 0, d[1]);
            if (WorldUtil.isLoaded(w, side) && !WorldUtil.isWater(w, side)) {
                result = true;
                break;
            }
        }
        cache.put(p.asLong(), result);
        return result;
    }

    private static List<Pos> reconstruct(Node tail) {
        List<Pos> out = new ArrayList<>();
        for (Node n = tail; n != null; n = n.parent) {
            out.add(n.pos);
        }
        Collections.reverse(out);
        return out;
    }

    private static double heuristic(Pos a, Pos b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(a.y() - b.y()) * 0.15;
    }

    private Pos nearestSurfaceWater(Pos center, int radius, int yRange) {
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Pos found = surfaceWaterInColumn(center.x() + dx, center.z() + dz, center.y(), yRange);
                if (found != null) {
                    double d = found.distSqr(center);
                    if (d < bestDist) {
                        bestDist = d;
                        best = found;
                    }
                }
            }
        }
        return best;
    }

    private Pos surfaceWaterInColumn(int x, int z, int anchorY, int yRange) {
        GameWorld w = bot.world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        Pos best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (int dy = -yRange; dy <= yRange; dy++) {
            Pos p = new Pos(x, anchorY + dy, z);
            if (isSurfaceWater(w, p) && Math.abs(dy) < bestDelta) {
                bestDelta = Math.abs(dy);
                best = p;
            }
        }
        return best;
    }

    private static boolean isSurfaceWater(GameWorld w, Pos p) {
        if (!WorldUtil.isWater(w, p) || WorldUtil.hasFluid(w, p.above())) {
            return false;
        }
        GameBlock above = w.block(p.above());
        return above.type().is(BlockTrait.AIR) || above.isReplaceable();
    }

    private void clearSurfacePath() {
        surfacePath = Collections.emptyList();
        surfaceIndex = 0;
        surfaceRecalc = 0;
        surfaceStart = null;
        surfaceGoal = null;
    }

    // ------------------------------------------------------------------ boarding / getting off

    private boolean tryBoardAsPassenger(GamePlayer target, GameBoat targetBoat) {
        if (!targetBoat.isValid() || target.vehicle() != targetBoat || targetBoat.passengers().size() >= 2) {
            return false;
        }
        if (bot.pos().distanceTo(targetBoat.position()) > 2.5) {
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(targetBoat.position().add(0, 0.5, 0));
        boolean boarded = targetBoat.addPassenger(bot.entity());
        if (boarded) {
            passengerHost = target.uuid();
        }
        return boarded;
    }

    private void park(GameBoat boat, Vec3 attackTarget) {
        boat.setVelocity(new Vec3(0, boat.velocity().y(), 0));
        bot.lookAt(attackTarget.add(0, 1, 0));
        boat.removePassenger(bot.entity());
        deltaRotation = 0;
        actionCooldown = Math.max(actionCooldown, 6);
        clearSurfacePath();
        bot.motor().stopWalk();
    }

    private boolean tryReenterParkedBoat(GamePlayer target) {
        if (target == null || riderBoat == null) {
            return false;
        }
        GameBoat parked = riderBoatEntity();
        if (parked == null) {
            riderBoat = null;
            clearSurfacePath();
            clearAssault();
            return false;
        }
        if (!parked.passengers().isEmpty() && !parked.passengers().contains(bot.entity())) {
            riderBoat = null;
            clearSurfacePath();
            clearAssault();
            return false;
        }
        if (bot.distanceTo(target) < 7.0) {
            return false;
        }
        if (bot.pos().distanceTo(parked.position()) <= 2.8) {
            bot.lookAt(parked.position().add(0, 0.5, 0));
            if (parked.addPassenger(bot.entity())) {
                clearAssault();
                actionCooldown = Math.max(actionCooldown, 6);
                return true;
            }
        }
        moveToward(parked.position());
        return true;
    }

    private void moveToward(Vec3 target) {
        Navigator nav = bot.brain().navigator();
        boolean hasPath = false;
        Vec3 dest = target;
        if (bot.settings().pathMode.get().usesPlanner()) {
            if (nav.isParkouring()) {
                nav.executeParkourTick();
                return;
            }
            Vec3 wp = nav.waypoint(target);
            if (wp != null) {
                dest = wp;
                hasPath = true;
            }
        }
        bot.brain().dumbStarMove(dest, hasPath, primaryChaseTarget());
    }

    private void forceDismount(GameBoat foreign) {
        foreign.removePassenger(bot.entity());
        passengerHost = null;
        reboardCooldown = Math.max(reboardCooldown, 12);
        if (bot.onGround()) {
            bot.motor().jump();
        }
    }

    private void cleanup(boolean breakBoat) {
        GameBoat tracked = riderBoatEntity();
        if (tracked != null) {
            if (tracked.passengers().contains(bot.entity())) {
                tracked.removePassenger(bot.entity());
            }
            if (breakBoat) {
                bot.motor().stopWalk();
                bot.lookAt(tracked.position());
                bot.swing();
                bot.swing();
                bot.brain().mlg().breakBoat(tracked);
            }
        }
        riderBoat = null;
        passengerHost = null;
        clearAssault();
        noMoveTicks = 0;
        deltaRotation = 0;
        clearSurfacePath();
        bot.motor().setSprinting(false);
        bot.motor().stopWalk();
    }

    private void clearAssault() {
        assaultActive = false;
        assaultTarget = null;
    }

    // ------------------------------------------------------------------ queries

    private GamePlayer findNearbyBoatingPlayer() {
        GamePlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (GamePlayer p : bot.world().players()) {
            if (p.isDead() || !(p.vehicle() instanceof GameBoat) || !bot.manager().isEligibleTarget(p)) {
                continue;
            }
            double d = bot.pos().distanceToSqr(p.position());
            if (d <= SEARCH_RADIUS_SQ && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private GamePlayer primaryChaseTarget() {
        GamePlayer t = bot.target();
        if (t != null && !t.isDead()) {
            return t;
        }
        t = bot.fallbackTarget();
        return t != null && !t.isDead() ? t : null;
    }

    private boolean shouldDeferToVerticalChase(GamePlayer target) {
        if (!bot.settings().allowTowering.on() || !isOutOfWater(target)) {
            return false;
        }
        double yDiff = target.position().y() - bot.pos().y();
        return yDiff >= 3.0 && bot.isWithinToweringRange(target, 3.0);
    }

    static boolean isOutOfWater(GamePlayer p) {
        if (p.vehicle() instanceof GameBoat) {
            return false;
        }
        return !p.isInWater() && !p.isUnderWater() && !p.isInLava();
    }

    private Pos findBoatPlacementWater() {
        Pos origin = bot.blockPos();
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        GameWorld w = bot.world();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Pos c = origin.offset(dx, dy, dz);
                    if (!canPlaceBoat(w, c)) {
                        continue;
                    }
                    double d = c.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    private static boolean canPlaceBoat(GameWorld w, Pos water) {
        if (!isWaterSource(w, water)) {
            return false;
        }
        GameBlock above = w.block(water.above());
        if (!above.type().is(BlockTrait.AIR) && !above.isReplaceable()) {
            return false;
        }
        return !WorldUtil.hasFluid(w, water.above()) && countSurfaceWater(w, water, 2) >= 5;
    }

    private static boolean isWaterSource(GameWorld w, Pos p) {
        GameBlock b = w.block(p);
        return b.type() == Blocks.WATER && b.fluidLevel() == 0;
    }

    /** How many water sources with air above there are within a radius (at the same height). */
    static int countSurfaceWater(GameWorld w, Pos center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Pos water = new Pos(center.x() + dx, center.y(), center.z() + dz);
                if (!isWaterSource(w, water)) {
                    continue;
                }
                GameBlock above = w.block(water.above());
                if ((above.type().is(BlockTrait.AIR) || above.isReplaceable()) && !WorldUtil.hasFluid(w, water.above())) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean shouldDismountNearShore(GameBoat boat) {
        GameWorld w = bot.world();
        Pos center = boat.blockPos();
        if (WorldUtil.isWater(w, center) || WorldUtil.isWater(w, center.below())) {
            return false;
        }
        return hasLandAround(w, center, 1);
    }

    private boolean isBoatCloseToLand(GameBoat boat) {
        return hasLandAround(bot.world(), boat.blockPos(), 0);
    }

    private static boolean hasLandAround(GameWorld w, Pos center, int maxDy) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= maxDy; dy++) {
                    Pos p = center.offset(dx, dy, dz);
                    GameBlock b = w.block(p);
                    if (b.type().is(BlockTrait.AIR) || WorldUtil.hasFluid(w, p)) {
                        continue;
                    }
                    if (b.type().is(BlockTrait.SOLID) || WorldUtil.isSturdyTop(w, p)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
