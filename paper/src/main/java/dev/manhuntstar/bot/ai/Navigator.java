package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.pathfinding.AStar;
import dev.manhuntstar.pathfinding.PathResult;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameBoat;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;

/**
 * Path navigation (A*) with the mod's rules: "resolved" target (skybase →
 * approach point, distant target → 96-block intermediate step), recomputing when the path gets
 * stale or the target moves, computing the next stretch ahead of time, queues of blocks to
 * dig/place, parkour and water navigation (coming up for air, getting out by the bank).
 */
public final class Navigator {

    private static final double NODE_REACH_SQ = 2.25;
    /** Radius to count the node as reached when it can't go straight to the next one (corner turn). */
    private static final double NODE_REACH_TIGHT_SQ = 0.36;
    /** The node being followed ended up more than this above the bot (it fell, got pushed off a ledge): recompute. */
    private static final double STRAY_BELOW = 3.0;
    /** On the ground, this far from the node being followed (to the side or above it), the path is no longer usable. */
    private static final double STRAY_HORIZONTAL = 5.0;
    private static final double STRAY_ABOVE = AStar.MAX_DIG_FALL + 2;
    /** How far below a bot in the air to look for the ground it will land on. */
    private static final int LANDING_SCAN = 64;
    private static final long STALE_MS = 2000L;
    private static final int LOOKAHEAD_TRIGGER = 25;
    private static final int MAX_DISTANCE = 150;
    private static final int APPROACH_RADIUS = 12;
    private static final int FAR_STEP = 96;
    /** Distances and detours (degrees) tried, in this order, for the intermediate point. */
    private static final int[] STEP_LENGTHS = {FAR_STEP, 64, 40, 20};
    private static final int[] STEP_ANGLES = {0, 30, -30, 60, -60};
    private static final int STEP_RADIUS = 5;
    /** The chosen detour holds until the bot gets within this distance of it or the target moves more than this. */
    private static final int STEP_KEEP = 4;
    private static final int STEP_KEEP_TARGET_MOVE = 16;
    /** In a world with a ceiling (Nether), how many blocks above and below the bot to look for ground. */
    private static final int CEILING_BAND = 12;
    private static final int SKYBASE_MIN_ELEVATION = 3;
    /** Within this height difference and distance the bot is at the target's level (no approach needed). */
    private static final int SAME_LEVEL_DY = 2;
    private static final int SAME_LEVEL_RADIUS = 32;
    private static final int RESOLVE_CACHE_TICKS = 10;
    private static final int WATER_ASCEND_AIR = 60;
    private static final int WATER_RESUME_AIR = 220;
    /** A 2–3 block jump takes less than 1 s; past 3 s, the bot got stuck. */
    private static final int PARKOUR_TIMEOUT_TICKS = 60;
    /** A stretch of water shorter than this isn't worth the boat, even if the A* went through it. */
    private static final int BOAT_MIN_RUN = 6;
    /** How much a block of swimming is worth in blocks of sprinting, when choosing where to leave the water. */
    private static final double SWIM_EXIT_WEIGHT = 3.0;

    private enum WaterMode { NONE, CRUISE, ASCEND_FOR_AIR }

    /** Looks, in an (x, z) column, for a place to stand. */
    @FunctionalInterface
    private interface ColumnSearch {
        Pos find(int x, int z);
    }

    private final Bot bot;
    private final Personality personality;
    private PathResult current;
    private int pathIndex;
    private Pos lastTarget;
    private long lastCalcMs;
    private Pos lastResolvedReal;
    private Pos lastResolved;
    private int lastResolvedTick = -200;
    private String lastResolveMode = "init";
    private int lastResolveDebugTick = -200;
    private CompletableFuture<PathResult> task;
    private boolean lookahead;
    /** End of a path where the extra stretch got nowhere: it isn't requested from there again. */
    private Pos lookaheadDeadEnd;
    private final ArrayDeque<Pos> mineQueue = new ArrayDeque<>();
    private final ArrayDeque<Pos> placeQueue = new ArrayDeque<>();
    private boolean parkouring;
    private Pos parkourTarget;
    private int parkourTicks;
    private WaterMode waterMode = WaterMode.NONE;
    private Pos escapeTarget;
    /** The last point returned is a node of a computed path (and not a "go straight there"). */
    private boolean onPath;
    /** How the last intermediate point came out, for debugging: distance/detour, water or why it failed. */
    private String stepNote = "";
    /** Detour in use when the 96-block straight line has no ground, and the target it was chosen for. */
    private Pos keptStep;
    private Pos keptStepTarget;
    private String keptStepNote = "";
    /** Side (1 or -1) of the last detour: on the next choice that side is tried first. */
    private int stepSide = 1;
    /** Until this tick the A* doesn't plan boats (a crossing has just failed). */
    private int boatBlockedUntil;

    public Navigator(Bot bot) {
        this.bot = bot;
        this.personality = new Personality(bot.id());
    }

    private GameWorld world() {
        return bot.world();
    }

    // ------------------------------------------------------------------ main entry

    /** Next point to walk to toward the target, or null if there is no path and the fallback is off. */
    public Vec3 waypoint(Vec3 finalTarget) {
        personality.tick();
        return astarWaypoint(resolveWaterAwareTarget(finalTarget));
    }

    /** Flees from the threat to a place 40–70 blocks away in the opposite direction. */
    public Vec3 escapeWaypoint(Vec3 threat) {
        personality.tick();
        Pos botPos = bot.blockPos();
        boolean reached = escapeTarget != null && botPos.manhattan(escapeTarget) < 5;
        boolean pathDone = current != null && pathIndex >= current.path().size();
        boolean idle = task == null && current == null;
        if (escapeTarget == null || reached || pathDone || idle) {
            escapeTarget = pickEscapePoint(threat);
        }
        return astarWaypoint(Vec3.bottomCenter(escapeTarget));
    }

    public void clearEscapeTarget() {
        escapeTarget = null;
    }

    private Pos pickEscapePoint(Vec3 threat) {
        Vec3 away = bot.pos().subtract(threat).horizontal().normalize();
        if (away.lengthSqr() < 1.0E-6) {
            away = bot.forwardVector().scale(-1);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 dir = away.rotateY((r.nextDouble() - 0.5) * Math.toRadians(120));
        int dist = 40 + r.nextInt(30);
        int tx = bot.blockPos().x() + (int) (dir.x() * dist);
        int tz = bot.blockPos().z() + (int) (dir.z() * dist);
        GameWorld w = world();
        int ty = w.isChunkLoaded(tx >> 4, tz >> 4) ? WorldUtil.surfaceY(w, tx, tz) : bot.blockPos().y();
        return new Pos(tx, ty, tz);
    }

    // ------------------------------------------------------------------ A*

    private Vec3 astarWaypoint(Vec3 finalTarget) {
        onPath = false;
        Pos botPos = anchorPos();
        Pos realTarget = finalTarget.toPos();
        Pos targetPos = resolveTarget(botPos, realTarget);
        boolean allowPlacement = bot.brain().canPlaceToweringBlocksNearTarget();
        emitResolveDebug(botPos, realTarget, targetPos, allowPlacement);
        if (!allowPlacement) {
            placeQueue.clear();
            if (current != null && !current.toPlace().isEmpty()) {
                current = null;
                pathIndex = 0;
            }
        }
        if (targetPos == null) {
            clearDebug();
            return finalTarget;
        }
        consumeFinishedTask();
        boolean needsRecalc = false;
        if (current != null && task == null) {
            boolean stale = System.currentTimeMillis() - lastCalcMs > STALE_MS;
            int threshold = recalcThreshold(botPos.manhattan(targetPos));
            boolean targetMoved = lastTarget == null || lastTarget.manhattan(targetPos) > threshold;
            needsRecalc = stale || targetMoved;
        }
        // Far from the path, the extra stretch requested from its end is no use: recompute from where the bot is. With
        // a full computation already running, wait for it.
        if (current != null && (task == null || lookahead) && strayedFromPath()) {
            needsRecalc = true;
        }
        if (current != null && task == null && !current.complete() && !needsRecalc) {
            int total = current.path().size();
            Pos end = current.path().get(total - 1);
            if (pathIndex >= total / 2 && total - pathIndex <= LOOKAHEAD_TRIGGER && !end.equals(lookaheadDeadEnd)) {
                lookahead = true;
                requestPath(end, targetPos);
            }
        }
        if (needsRecalc) {
            cancelTask();
            requestPath(botPos, targetPos);
        } else if ((current == null || pathIndex >= current.path().size()) && task == null) {
            requestPath(botPos, targetPos);
        }
        if (current == null || pathIndex >= current.path().size()) {
            clearDebug();
            return bot.settings().fallbackDumb.on() ? Vec3.bottomCenter(targetPos) : null;
        }
        if (parkouring) {
            return null;
        }
        if (tickActions()) {
            onPath = true;
            updateDebug();
            return Vec3.bottomCenter(current.path().get(pathIndex));
        }
        Vec3 wp = advanceToNextNode(finalTarget);
        onPath = current != null;
        updateDebug();
        return wp;
    }

    /**
     * The last {@link #waypoint} came from a computed path. When false the point is only a direction
     * (target too far, path still being computed or one that failed) and whoever walks has to avoid
     * obstacles on their own.
     */
    public boolean onPath() {
        return onPath;
    }

    // ------------------------------------------------------------------ boat

    private boolean boatsAllowed() {
        return bot.settings().allowBoat.on() && Bukkit.getCurrentTick() >= boatBlockedUntil;
    }

    /** After a crossing that didn't work the bot swims or goes around for a while. */
    public void blockBoats(int ticks) {
        boatBlockedUntil = Bukkit.getCurrentTick() + ticks;
    }

    /**
     * End of the water stretch ahead on the path, when the A* decided to cross it by boat. On foot it only
     * answers with the bot already at the edge of a long stretch; by boat, returns the end of the stretch it's on.
     */
    public Pos boatCrossingGoal(boolean riding) {
        if (current == null || !boatsAllowed()) {
            return null;
        }
        List<Pos> path = current.path();
        GameWorld w = world();
        int start = -1;
        for (int i = pathIndex; i < Math.min(path.size(), pathIndex + 3); i++) {
            if (isBoatWater(w, path.get(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int end = start;
        while (end + 1 < path.size() && isBoatWater(w, path.get(end + 1))) {
            end++;
        }
        if (!riding && (end - start + 1 < BOAT_MIN_RUN || bot.pos().distanceTo(Vec3.bottomCenter(path.get(start))) > 3.0)) {
            return null;
        }
        return path.get(end);
    }

    private static boolean isBoatWater(GameWorld w, Pos p) {
        return WorldUtil.isWater(w, p) && !WorldUtil.hasFluid(w, p.above());
    }

    private void consumeFinishedTask() {
        if (task == null || !task.isDone()) {
            return;
        }
        PathResult result;
        try {
            result = task.join();
        } catch (RuntimeException e) {
            result = null;
        }
        if (result != null && !bot.brain().canPlaceToweringBlocksNearTarget() && !result.toPlace().isEmpty()) {
            placeQueue.clear();
            result = null;
        }
        if (result != null) {
            if (lookahead && current != null) {
                List<Pos> path = new ArrayList<>(current.path());
                if (result.path().size() > 1) {
                    path.addAll(result.path().subList(1, result.path().size()));
                } else {
                    // nothing comes out of the end of the path (point in the air, dead end): it asked again from there every tick
                    lookaheadDeadEnd = path.getLast();
                }
                Set<Pos> mine = new LinkedHashSet<>(current.toMine());
                mine.addAll(result.toMine());
                Set<Pos> place = new LinkedHashSet<>(current.toPlace());
                place.addAll(result.toPlace());
                current = new PathResult(path, mine, place, result.complete(),
                        current.nodesExplored() + result.nodesExplored(), current.timeMs() + result.timeMs());
            } else {
                current = result;
                pathIndex = 0;
            }
        } else if (!lookahead) {
            current = null;
            pathIndex = 0;
        } else if (current != null) {
            lookaheadDeadEnd = current.path().getLast();
        }
        lookahead = false;
        task = null;
        mineQueue.clear();
        placeQueue.clear();
        if (bot.settings().debugPaths.on()) {
            GamePlayer p = bot.target();
            if (p != null && p.hasPermission("manhuntstar.admin")) {
                var ps = bot.manager().plugin().pathService();
                String msg = current == null
                        ? String.format("[A*] FAILED | snapshot: %dms | queue: %d", ps.lastCaptureMs(), ps.activeTasks())
                        : String.format("[A*] %s | %dms | %d nodes | snapshot: %dms | queue: %d", current.complete() ? "COMPLETE" : "PARTIAL",
                        current.timeMs(), current.nodesExplored(), ps.lastCaptureMs(), ps.activeTasks());
                p.sendActionBar(Component.text(msg));
            }
        }
    }

    private void requestPath(Pos start, Pos target) {
        if (task != null) {
            return;
        }
        boolean allowPlacement = bot.brain().canPlaceToweringBlocksNearTarget();
        task = bot.manager().plugin().pathService().findPath(world(), start, target, allowPlacement, boatsAllowed());
        lastTarget = target;
        // the extra stretch continues the old path: counting it as a new computation kept the path from going stale
        if (!lookahead) {
            lastCalcMs = System.currentTimeMillis();
        }
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel(false);
            task = null;
            lookahead = false;
        }
    }

    private static int recalcThreshold(int dist) {
        if (dist <= 9) {
            return 1;
        }
        return dist <= 20 ? 2 : 4;
    }

    // ------------------------------------------------------------------ following the nodes

    /**
     * The bot moved away from the node it's following (fell off a ledge, got pushed). A node well above it
     * is never reached: it kept jumping underneath, looking up.
     */
    private boolean strayedFromPath() {
        if (current == null || pathIndex >= current.path().size() || parkouring || bot.isRiding()) {
            return false;
        }
        Pos node = current.path().get(pathIndex);
        Vec3 p = bot.pos();
        double dy = p.y() - node.y();
        if (dy < -STRAY_BELOW) {
            return true;
        }
        // in the air (jumping, in a planned fall) waits to land
        if (!bot.onGround()) {
            return false;
        }
        double horizontal = Math.hypot(p.x() - (node.x() + 0.5), p.z() - (node.z() + 0.5));
        return horizontal > STRAY_HORIZONTAL || dy > STRAY_ABOVE;
    }

    private Vec3 advanceToNextNode(Vec3 finalTarget) {
        if (current == null || pathIndex >= current.path().size()) {
            return finalTarget;
        }
        Pos next = current.path().get(pathIndex);
        Vec3 centre = Vec3.bottomCenter(next);
        GameEntity vehicle = bot.vehicle();
        Vec3 p = vehicle instanceof GameHorse ? vehicle.position() : bot.pos();
        double dx = p.x() - centre.x();
        double dz = p.z() - centre.z();
        double dy = p.y() - centre.y();
        double distSq = dx * dx + dz * dz;
        // Close to the node it may already move on to the next one, unless the straight line to it hits a block:
        // then the path turns around it and the bot needs to actually reach the node.
        boolean reached = distSq < NODE_REACH_TIGHT_SQ || (distSq < NODE_REACH_SQ
                && (pathIndex + 1 >= current.path().size() || vehicle != null || straightWalkClear(p, current.path().get(pathIndex + 1))));
        if (reached && dy > -0.2 && dy < 1.5) {
            pathIndex++;
            if (pathIndex >= current.path().size()) {
                current = null;
                return finalTarget;
            }
            Pos prev = next;
            next = current.path().get(pathIndex);
            centre = Vec3.bottomCenter(next);
            int gapX = Math.abs(prev.x() - next.x());
            int gapZ = Math.abs(prev.z() - next.z());
            // mounted, the gap is jumped by the horse (HorseRoutine), not by parkour on foot
            if ((gapX >= 2 || gapZ >= 2) && (gapX == 0 || gapZ == 0) && vehicle == null) {
                parkouring = true;
                parkourTarget = next;
                parkourTicks = 0;
            }
            enqueueActions(prev, next);
        }
        return centre;
    }

    /**
     * The straight line from the current position to the node's center is free at the bot's feet and head
     * height. A step up doesn't pass (the step's block is at feet height), so going up never cuts corners.
     */
    private boolean straightWalkClear(Vec3 from, Pos node) {
        GameWorld w = world();
        Vec3 to = Vec3.bottomCenter(node);
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        int steps = (int) Math.ceil(Math.hypot(dx, dz) / 0.25);
        // +0.5: on a slab or soul sand the feet are in the block above, not in the slab's
        int feet = (int) Math.floor(from.y() + 0.5);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(from.x() + dx * t);
            int z = (int) Math.floor(from.z() + dz * t);
            if (WorldUtil.collides(w, new Pos(x, feet, z)) || WorldUtil.collides(w, new Pos(x, feet + 1, z))) {
                return false;
            }
        }
        return true;
    }

    private void enqueueActions(Pos from, Pos node) {
        if (bot.isRiding() || current == null) {
            return;
        }
        GameWorld w = world();
        // To climb a step the body goes 2 blocks above the current node. The A* relies on digging that
        // block, which is neither the next node's feet nor its head; without this the bot gets stuck under it.
        if (node.y() > from.y() && WorldUtil.collides(w, from.above(2))) {
            mineQueue.addLast(from.above(2));
        }
        // Digging the floor and falling into the gap below (the ceiling of the target's box): the dug block is the current node's floor.
        if (node.x() == from.x() && node.z() == from.z() && node.y() < from.y() - 1 && WorldUtil.collides(w, from.below())) {
            mineQueue.addLast(from.below());
        }
        if (WorldUtil.collides(w, node)) {
            mineQueue.addLast(node);
        }
        if (WorldUtil.collides(w, node.above())) {
            mineQueue.addLast(node.above());
        }
        boolean water = WorldUtil.hasFluid(w, node) || WorldUtil.hasFluid(w, node.above());
        if (!water && bot.brain().canPlaceToweringBlocksNearTarget() && !WorldUtil.collides(w, node.below())) {
            placeQueue.addLast(node.below());
        }
    }

    /** @return true while digging/placing the path's blocks (the bot stays at the current node). */
    private boolean tickActions() {
        if (bot.isRiding()) {
            mineQueue.clear();
            placeQueue.clear();
            return false;
        }
        boolean canPlace = bot.brain().canPlaceToweringBlocksNearTarget();
        if (!canPlace) {
            placeQueue.clear();
        }
        if (bot.mining().isMining()) {
            return true;
        }
        GameWorld w = world();
        while (!mineQueue.isEmpty()) {
            Pos next = mineQueue.peekFirst();
            if (!WorldUtil.collides(w, next)) {
                mineQueue.pollFirst();
                continue;
            }
            bot.mining().startNoStop(next);
            mineQueue.pollFirst();
            return true;
        }
        while (!placeQueue.isEmpty()) {
            if (!canPlace) {
                placeQueue.clear();
                return false;
            }
            Pos floor = placeQueue.peekFirst();
            if (!WorldUtil.canBeReplaced(w, floor)) {
                placeQueue.pollFirst();
                continue;
            }
            Pos me = bot.blockPos();
            if (floor.x() == me.x() && floor.z() == me.z() && floor.y() >= me.y()) {
                if (bot.onGround()) {
                    bot.motor().jump();
                    return true;
                }
                if (bot.velocity().y() > 0) {
                    return true;
                }
            }
            bot.placeBlock(floor, Blocks.COBBLESTONE, true);
            placeQueue.pollFirst();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ parkour

    public boolean isParkouring() {
        return parkouring;
    }

    public void executeParkourTick() {
        if (!parkouring || parkourTarget == null) {
            return;
        }
        if (++parkourTicks > PARKOUR_TIMEOUT_TICKS) {
            // the jump didn't work and the bot ended up in a shallow gap: gives up and recomputes the path
            parkouring = false;
            current = null;
            return;
        }
        Vec3 target = Vec3.bottomCenter(parkourTarget);
        bot.lookAt(target.add(0, 1.5, 0));
        bot.motor().setSprinting(true);
        Vec3 dir = target.subtract(bot.pos()).horizontal().normalize();
        bot.motor().walk(dir);
        Pos edge = bot.pos().add(dir.scale(0.4)).add(0, -0.2, 0).toPos();
        if (bot.onGround() && (!WorldUtil.collides(world(), edge) || bot.horizontalCollision())) {
            bot.motor().jump();
        }
        Vec3 p = bot.pos();
        double dx = p.x() - target.x();
        double dz = p.z() - target.z();
        double dy = p.y() - target.y();
        if (dx * dx + dz * dz < 1.5 && dy > -0.5 && dy < 1.5) {
            parkouring = false;
            pathIndex++;
        } else if (p.y() < target.y() - 2.0) {
            parkouring = false;
            current = null;
        }
    }

    /** Sprint jump on a flat straight (or under a low ceiling), as the player does to gain speed. */
    public boolean shouldSprintJump() {
        if (current == null || pathIndex >= current.path().size() || parkouring) {
            return false;
        }
        Pos cur = bot.blockPos();
        Pos next1 = current.path().get(pathIndex);
        if (next1.y() != cur.y()) {
            return false;
        }
        GameWorld w = world();
        boolean ceiling = WorldUtil.collides(w, cur.above(2)) || WorldUtil.collides(w, next1.above(2));
        if (ceiling) {
            return true;
        }
        return pathIndex + 1 < current.path().size() && current.path().get(pathIndex + 1).y() == cur.y();
    }

    /** There is an unbreakable block (hardness ≥ 50 or negative) between the bot's eyes and the point. */
    public boolean hasIndestructibleBlockInPath(Vec3 targetEye) {
        Vec3 from = bot.eyePos();
        Vec3 dir = targetEye.subtract(from);
        double len = dir.length();
        if (len < 0.01) {
            return false;
        }
        Vec3 unit = dir.normalize().scale(0.5);
        int steps = (int) Math.ceil(len / 0.5);
        GameWorld w = world();
        for (int i = 1; i <= steps; i++) {
            Pos check = from.add(unit.scale(i)).toPos();
            if (!WorldUtil.isLoaded(w, check)) {
                return false;
            }
            GameBlock b = w.block(check);
            if (b.type().is(BlockTrait.AIR) || b.isPassable()) {
                continue;
            }
            float h = b.type().hardness();
            if (h >= 50f || h < 0f) {
                return true;
            }
        }
        return false;
    }

    /**
     * Height of the biggest fall the path expects right ahead (up to {@link AStar#MAX_DIG_FALL} blocks; 0 =
     * none). Planned falls don't need an MLG.
     */
    public int plannedFallHeight() {
        if (current == null || pathIndex >= current.path().size()) {
            return 0;
        }
        List<Pos> path = current.path();
        int from = Math.max(0, pathIndex - 1);
        int to = Math.min(path.size() - 1, pathIndex + 3);
        int height = 0;
        for (int i = from; i < to; i++) {
            int dy = path.get(i).y() - path.get(i + 1).y();
            if (dy >= 1 && dy <= AStar.MAX_DIG_FALL) {
                height = Math.max(height, dy);
            }
        }
        return height;
    }

    public List<Pos> currentPath() {
        return current == null ? null : current.path();
    }

    public int pathIndex() {
        return pathIndex;
    }

    public void reset() {
        current = null;
        pathIndex = 0;
        lastTarget = null;
        lastCalcMs = 0;
        lastResolvedReal = null;
        lastResolved = null;
        lastResolvedTick = -200;
        lastResolveMode = "reset";
        lastResolveDebugTick = -200;
        keptStep = null;
        keptStepTarget = null;
        mineQueue.clear();
        placeQueue.clear();
        parkouring = false;
        lookahead = false;
        lookaheadDeadEnd = null;
        cancelTask();
        clearDebug();
    }

    // ------------------------------------------------------------------ target resolution

    private Pos resolveTarget(Pos botPos, Pos real) {
        int now = Bukkit.getCurrentTick();
        // the failure is stored too: redoing the whole search every tick would be expensive
        if (lastResolvedReal != null && now - lastResolvedTick <= RESOLVE_CACHE_TICKS && lastResolvedReal.manhattan(real) <= 2) {
            if (lastResolved != null) {
                lastResolveMode = "cache";
            }
            return lastResolved;
        }
        GameWorld w = world();
        // The approach point is for a target above the bot (tower, platform in the sky). With the bot already at the
        // target's level and close, it sent the bot somewhere else (up to the ceiling of a glass box) and not to the target.
        // With the bot above the target (on the ceiling of its box), it sent it down to the ground under the platform.
        boolean botAbove = botPos.y() - real.y() > SAME_LEVEL_DY;
        boolean needsApproach = !botAbove && !sameLevelNearby(botPos, real) && isTargetAirborneOrSkybasing(w, real);
        Pos resolved = real;
        String mode = needsApproach ? "skybase" : "direct";
        if (needsApproach) {
            Pos approach = closestStandableNear(real, botPos, APPROACH_RADIUS, (x, z) -> standableInColumn(x, z, real.y(), true));
            if (approach != null) {
                resolved = approach;
                mode = "skybase-approach";
            } else {
                mode = "skybase-no-approach";
            }
        }
        if (botPos.manhattan(resolved) > MAX_DISTANCE) {
            Pos step = intermediateTarget(botPos, resolved);
            if (step != null) {
                resolved = step;
                mode += "+step" + stepNote;
            }
        }
        if (botPos.manhattan(resolved) > MAX_DISTANCE) {
            // toward the real target; if that's the search that failed above, don't repeat it
            Pos fallback = resolved.equals(real) ? null : intermediateTarget(botPos, real);
            if (fallback == null || botPos.manhattan(fallback) > MAX_DISTANCE) {
                lastResolveMode = mode + "+null" + stepNote;
                lastResolvedReal = real;
                lastResolved = null;
                lastResolvedTick = now;
                return null;
            }
            resolved = fallback;
            mode += "+force-step" + stepNote;
        }
        if (needsApproach && resolved.equals(real)) {
            Pos soft = closestStandableNear(real, botPos, APPROACH_RADIUS, (x, z) -> standableInColumn(x, z, real.y(), false));
            if (soft != null) {
                resolved = soft;
                mode = "skybase-soft-approach";
            }
        }
        lastResolvedReal = real;
        lastResolved = resolved;
        lastResolvedTick = now;
        lastResolveMode = mode;
        return resolved;
    }

    /**
     * Intermediate point toward a distant target. First 96 blocks in a straight line, as in the mod; if there
     * is no ground there (lake, sea, dense forest), it tries to the sides and closer, and finally the water's
     * surface, because the bot swims. With boats allowed, water on the straight line is fine: it rows across.
     * Only accepts points within the A*'s reach ({@link #MAX_DISTANCE}). A chosen
     * detour is kept while it works, otherwise the bot would switch sides at every step.
     */
    private Pos intermediateTarget(Pos botPos, Pos target) {
        stepNote = "";
        Vec3 flat = new Vec3(target.x() - botPos.x(), 0, target.z() - botPos.z());
        if (flat.lengthSqr() < 1.0E-6) {
            return null;
        }
        GameWorld w = world();
        boolean ceiling = w.hasCeiling();
        Vec3 dir = flat.normalize();
        ColumnSearch ground = (x, z) -> {
            Pos c = ceiling ? groundNearY(x, z, botPos.y()) : groundInColumn(x, z);
            return c != null && botPos.manhattan(c) <= MAX_DISTANCE ? c : null;
        };
        Pos water = null;
        boolean lava = false;
        int lastLength = -1;
        for (int length : STEP_LENGTHS) {
            int d = (int) Math.min(length, flat.length());
            if (d == lastLength) {
                continue;
            }
            lastLength = d;
            for (int baseAngle : STEP_ANGLES) {
                boolean straight = length == FAR_STEP && baseAngle == 0;
                if (!straight && keptStepStillGood(botPos, target)) {
                    stepNote = keptStepNote;
                    return keptStep;
                }
                int angle = baseAngle * stepSide;
                Vec3 step = dir.rotateY(Math.toRadians(angle)).scale(d);
                int x = botPos.x() + (int) Math.round(step.x());
                int z = botPos.z() + (int) Math.round(step.z());
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    if (straight) {
                        keptStep = null;
                        return new Pos(x, botPos.y(), z);
                    }
                    continue;
                }
                Pos rough = new Pos(x, ceiling ? botPos.y() : WorldUtil.surfaceYNoLeaves(w, x, z), z);
                Pos found = closestStandableNear(rough, botPos, STEP_RADIUS, ground);
                if (found != null) {
                    if (straight) {
                        keptStep = null;
                    } else {
                        stepNote = "(" + d + (angle == 0 ? "" : " " + angle + "°") + ")";
                        keep(found, target, angle);
                    }
                    return found;
                }
                if (!ceiling) {
                    Pos top = rough.below();
                    lava |= WorldUtil.isLava(w, top);
                    boolean surfaceWater = WorldUtil.isWater(w, top) && !WorldUtil.hasFluid(w, rough) && botPos.manhattan(top) <= MAX_DISTANCE;
                    if (surfaceWater && straight && boatsAllowed()) {
                        // by boat, crossing in a straight line usually beats looking for land to the sides
                        keptStep = null;
                        stepNote = "(boat)";
                        return top;
                    }
                    if (water == null && surfaceWater) {
                        water = top;
                    }
                }
            }
        }
        if (water != null) {
            stepNote = "(water)";
            keep(water, target, 0);
            return water;
        }
        keptStep = null;
        stepNote = lava ? "(lava)" : "(no floor)";
        return null;
    }

    private void keep(Pos step, Pos target, int angle) {
        keptStep = step;
        keptStepTarget = target;
        keptStepNote = stepNote;
        if (angle != 0) {
            stepSide = angle > 0 ? 1 : -1;
        }
    }

    /** The stored detour still leads near the target, the bot hasn't reached it and it can still stand there. */
    private boolean keptStepStillGood(Pos botPos, Pos target) {
        if (keptStep == null || keptStepTarget == null || keptStepTarget.manhattan(target) > STEP_KEEP_TARGET_MOVE
                || botPos.manhattan(keptStep) > MAX_DISTANCE) {
            return false;
        }
        double botToStep = Math.hypot(keptStep.x() - botPos.x(), keptStep.z() - botPos.z());
        double stepToTarget = Math.hypot(target.x() - keptStep.x(), target.z() - keptStep.z());
        double botToTarget = Math.hypot(target.x() - botPos.x(), target.z() - botPos.z());
        if (botToStep < STEP_KEEP || stepToTarget > botToTarget - STEP_KEEP) {
            return false;
        }
        GameWorld w = world();
        return WorldUtil.isLoaded(w, keptStep) && (WorldUtil.isWater(w, keptStep) || isStandableApproach(keptStep, false));
    }

    /** Column ground for the intermediate point: the highest standing spot near the terrain, ignoring tree canopies. */
    private Pos groundInColumn(int x, int z) {
        GameWorld w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int surface = WorldUtil.surfaceYNoLeaves(w, x, z);
        int top = Math.min(w.maxHeight() - 2, surface + 4);
        int bottom = Math.max(w.minHeight() + 1, surface - 8);
        for (int y = top; y >= bottom; y--) {
            Pos feet = new Pos(x, y, z);
            if (isStandableApproach(feet, false) && !w.block(feet.below()).type().is(BlockTrait.LEAVES)) {
                return feet;
            }
        }
        return null;
    }

    /**
     * In a world with a ceiling (Nether) the heightmap points at the bedrock ceiling; looks for ground near the
     * bot's height, below the ceiling.
     */
    private Pos groundNearY(int x, int z, int refY) {
        GameWorld w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int low = Math.max(w.minHeight() + 1, refY - CEILING_BAND);
        int high = Math.min(w.logicalHeight() - 1, refY + CEILING_BAND);
        for (int i = 0; i <= 2 * CEILING_BAND; i++) {
            // refY, refY+1, refY-1, refY+2, ...: closest to the bot's height first
            int y = refY + ((i + 1) >> 1) * ((i & 1) == 1 ? 1 : -1);
            if (y < low || y > high) {
                continue;
            }
            Pos feet = new Pos(x, y, z);
            if (isStandableApproach(feet, false)) {
                return feet;
            }
        }
        return null;
    }

    private Pos closestStandableNear(Pos target, Pos botPos, int radius, ColumnSearch column) {
        Pos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    Pos c = column.find(target.x() + dx, target.z() + dz);
                    if (c == null) {
                        continue;
                    }
                    double tdx = c.x() - target.x();
                    double tdz = c.z() - target.z();
                    double score = (tdx * tdx + tdz * tdz) * 8.0 + c.distSqr(botPos) * 0.05 + Math.abs(c.y() - target.y()) * 0.25;
                    if (score < bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
            if (best != null && r >= 2) {
                return best;
            }
        }
        return best;
    }

    private Pos standableInColumn(int x, int z, int targetY, boolean avoidElevated) {
        GameWorld w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int surface = WorldUtil.surfaceYNoLeaves(w, x, z);
        int top = Math.min(w.maxHeight() - 2, Math.max(targetY, surface + 4));
        int bottom = Math.max(w.minHeight() + 1, surface - 8);
        if (avoidElevated) {
            top = Math.min(top, naturalColumnTopY(x, z, targetY) + 4);
        }
        for (int y = top; y >= bottom; y--) {
            Pos feet = new Pos(x, y, z);
            if (isStandableApproach(feet, avoidElevated)) {
                return feet;
            }
        }
        return null;
    }

    private int naturalColumnTopY(int x, int z, int targetY) {
        GameWorld w = world();
        for (int y = Math.min(targetY, w.maxHeight() - 2); y >= w.minHeight(); y--) {
            Pos p = new Pos(x, y, z);
            GameBlock b = w.block(p);
            if (b.type().is(BlockTrait.AIR) || b.isReplaceable()) {
                continue;
            }
            if (!WorldUtil.hasFluid(w, p) && hasLargeAirGapBelow(w, p)) {
                continue;
            }
            return y + 1;
        }
        return WorldUtil.surfaceYNoLeaves(w, x, z);
    }

    private boolean isStandableApproach(Pos feet, boolean avoidElevated) {
        GameWorld w = world();
        boolean clearFeet = !WorldUtil.hasCollision(w, feet) && !WorldUtil.hasFluid(w, feet);
        boolean clearHead = !WorldUtil.hasCollision(w, feet.above()) && !WorldUtil.hasFluid(w, feet.above());
        Pos floor = feet.below();
        GameBlock fb = w.block(floor);
        boolean safeFloor = !fb.type().is(BlockTrait.AIR) && !WorldUtil.hasFluid(w, floor)
                && (fb.type().is(BlockTrait.SOLID) || WorldUtil.isSturdyTop(w, floor));
        if (!clearFeet || !clearHead || !safeFloor) {
            return false;
        }
        return !avoidElevated || (!isPlayerPlaced(floor) && !hasLargeAirGapBelow(w, floor));
    }

    private boolean isPlayerPlaced(Pos p) {
        return bot.manager().blockTracker().isPlayerPlaced(world(), p);
    }

    /** Bot at the target's height and close: it's already on their platform (or next to it) and the normal path handles it. */
    private static boolean sameLevelNearby(Pos botPos, Pos target) {
        return Math.abs(botPos.y() - target.y()) <= SAME_LEVEL_DY
                && Math.hypot(botPos.x() - target.x(), botPos.z() - target.z()) <= SAME_LEVEL_RADIUS;
    }

    private boolean isTargetAirborneOrSkybasing(GameWorld w, Pos target) {
        if (hasNearbyWater(w, target)) {
            return false;
        }
        if (!w.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
            return false;
        }
        if (target.y() - WorldUtil.surfaceY(w, target.x(), target.z()) >= SKYBASE_MIN_ELEVATION) {
            return true;
        }
        Pos support = firstSupportBelow(w, target);
        if (support != null) {
            if (hasLargeAirGapBelow(w, support)) {
                return true;
            }
            if (isPlayerPlaced(support)) {
                Pos natural = firstNaturalSupportBelow(w, support);
                return natural == null || support.y() - natural.y() >= 3;
            }
        }
        for (int i = 1; i <= 64; i++) {
            Pos p = target.below(i);
            if (p.y() < w.minHeight()) {
                break;
            }
            GameBlock b = w.block(p);
            boolean empty = b.type().is(BlockTrait.AIR) || (b.isReplaceable() && !WorldUtil.hasFluid(w, p));
            if (empty || isPlayerPlaced(p)) {
                continue;
            }
            return target.y() - p.y() > 3;
        }
        return true;
    }

    private Pos firstNaturalSupportBelow(GameWorld w, Pos start) {
        for (int i = 1; i <= 64; i++) {
            Pos p = start.below(i);
            if (p.y() < w.minHeight()) {
                return null;
            }
            GameBlock b = w.block(p);
            boolean empty = b.type().is(BlockTrait.AIR) || (b.isReplaceable() && !WorldUtil.hasFluid(w, p));
            if (!empty && !isPlayerPlaced(p)) {
                return p;
            }
        }
        return null;
    }

    private static Pos firstSupportBelow(GameWorld w, Pos target) {
        for (int y = target.y() - 1; y >= Math.max(w.minHeight(), target.y() - 8); y--) {
            Pos p = new Pos(target.x(), y, target.z());
            GameBlock b = w.block(p);
            if (b.type().is(BlockTrait.AIR) || (b.isReplaceable() && !WorldUtil.hasFluid(w, p))) {
                continue;
            }
            return p;
        }
        return null;
    }

    private static boolean hasLargeAirGapBelow(GameWorld w, Pos support) {
        int open = 0;
        for (int y = support.y() - 1; y >= Math.max(w.minHeight(), support.y() - 8); y--) {
            Pos p = new Pos(support.x(), y, support.z());
            if (WorldUtil.hasCollision(w, p) || WorldUtil.hasFluid(w, p)) {
                return false;
            }
            if (++open >= 5) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyWater(GameWorld w, Pos target) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy >= -6; dy--) {
                    if (WorldUtil.isWater(w, target.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ water

    private Vec3 resolveWaterAwareTarget(Vec3 finalTarget) {
        if (bot.vehicle() instanceof GameBoat) {
            waterMode = WaterMode.NONE;
            return boatSurfaceTarget(finalTarget);
        }
        if (!bot.isInWater() || bot.isInLava()) {
            waterMode = WaterMode.NONE;
            return finalTarget;
        }
        if (isDryLandTarget(finalTarget)) {
            waterMode = WaterMode.NONE;
            ShoreExit exit = shoreExitToward(finalTarget);
            if (exit == null) {
                return finalTarget;
            }
            // already at the exit: aims at the solid ground to climb out (aiming at the water itself left the bot with no direction)
            Vec3 water = Vec3.bottomCenter(exit.water());
            boolean atExit = Math.hypot(water.x() - bot.pos().x(), water.z() - bot.pos().z()) < 1.5;
            return atExit ? Vec3.bottomCenter(exit.land()) : water;
        }
        if (!isTargetOffSolidGround(finalTarget)) {
            waterMode = WaterMode.NONE;
            return finalTarget;
        }
        updateWaterMode();
        Pos goal = waterMode == WaterMode.ASCEND_FOR_AIR ? breathingNode() : cruiseNodeToward(finalTarget);
        return goal == null ? finalTarget : Vec3.center(goal);
    }

    private Vec3 boatSurfaceTarget(Vec3 finalTarget) {
        GameWorld w = world();
        Pos t = finalTarget.toPos();
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy >= -8; dy--) {
                    Pos p = t.offset(dx, dy, dz);
                    if (WorldUtil.isWater(w, p) && !WorldUtil.hasFluid(w, p.above())) {
                        double d = p.distSqr(t);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                        break;
                    }
                }
            }
        }
        return best == null ? finalTarget : Vec3.bottomCenter(best);
    }

    private boolean isDryLandTarget(Vec3 finalTarget) {
        GameWorld w = world();
        Pos feet = finalTarget.toPos();
        if (WorldUtil.isWater(w, feet) || WorldUtil.isWater(w, feet.below())) {
            return false;
        }
        GameBlock below = w.block(feet.below());
        return !below.type().is(BlockTrait.AIR) && !WorldUtil.hasFluid(w, feet.below()) && below.type().is(BlockTrait.OCCLUDING);
    }

    /** The target isn't on solid ground (it's in the water/in the air): navigate through the water instead of going straight. */
    private boolean isTargetOffSolidGround(Vec3 finalTarget) {
        GameWorld w = world();
        Pos below = finalTarget.toPos().below();
        GameBlock b = w.block(below);
        return !(!b.type().is(BlockTrait.AIR) && !WorldUtil.hasFluid(w, below) && b.type().is(BlockTrait.OCCLUDING));
    }

    private void updateWaterMode() {
        int air = bot.entity() == null ? 300 : bot.entity().remainingAir();
        if (waterMode == WaterMode.ASCEND_FOR_AIR) {
            if (air >= WATER_RESUME_AIR) {
                waterMode = WaterMode.CRUISE;
            }
            return;
        }
        waterMode = air <= WATER_ASCEND_AIR ? WaterMode.ASCEND_FOR_AIR : WaterMode.CRUISE;
    }

    /** Water block at the edge and the solid ground next to it, where the bot gets out of the water. */
    private record ShoreExit(Pos water, Pos land) {
    }

    /**
     * Fastest way out of the water toward the target: swimming costs ~3× sprinting. With squared distances the
     * farthest exit won when the target went along the bank, and the bot swam along the edge without getting out.
     */
    private ShoreExit shoreExitToward(Vec3 finalTarget) {
        GameWorld w = world();
        Pos botPos = bot.blockPos();
        Pos targetPos = finalTarget.toPos();
        Vec3 toward = finalTarget.subtract(bot.pos()).horizontal();
        toward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : toward.normalize();
        ShoreExit best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Pos water = botPos.offset(dx, dy, dz);
                    if (!WorldUtil.isWater(w, water) || WorldUtil.hasFluid(w, water.above())) {
                        continue;
                    }
                    for (BlockFace face : Locations.HORIZONTALS) {
                        Pos landFeet = Locations.relative(water, face);
                        Pos landBelow = landFeet.below();
                        GameBlock lb = w.block(landBelow);
                        if (lb.type().is(BlockTrait.AIR) || WorldUtil.hasFluid(w, landBelow)) {
                            continue;
                        }
                        if (!lb.type().is(BlockTrait.SOLID) && !WorldUtil.isSturdyTop(w, landBelow)) {
                            continue;
                        }
                        if (WorldUtil.hasCollision(w, landFeet) || WorldUtil.hasCollision(w, landFeet.above())
                                || WorldUtil.hasFluid(w, landFeet) || WorldUtil.hasFluid(w, landFeet.above())) {
                            continue;
                        }
                        Vec3 edge = new Vec3(face.getModX(), 0, face.getModZ());
                        double align = 1.0 - Math.max(0.0, edge.normalize().dot(toward));
                        double score = Math.sqrt(water.distSqr(botPos)) * SWIM_EXIT_WEIGHT + Math.sqrt(landFeet.distSqr(targetPos))
                                + Math.abs(water.y() - botPos.y()) * 2.0 + align * 4.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new ShoreExit(water, landFeet);
                        }
                    }
                }
            }
        }
        return best;
    }

    private Pos cruiseNodeToward(Vec3 finalTarget) {
        GameWorld w = world();
        Pos botPos = bot.blockPos();
        Pos target = finalTarget.toPos();
        Pos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int x = botPos.x() + dx;
                int z = botPos.z() + dz;
                int y = botPos.y();
                if (!WorldUtil.isWater(w, new Pos(x, y, z)) && !WorldUtil.isWater(w, new Pos(x, y - 1, z)) && !WorldUtil.isWater(w, new Pos(x, y + 1, z))) {
                    continue;
                }
                int surface = y;
                while (surface < w.maxHeight() - 1 && WorldUtil.isWater(w, new Pos(x, surface + 1, z))) {
                    surface++;
                }
                Pos c = new Pos(x, surface - 2, z);
                if (!canOccupyWater(c)) {
                    c = new Pos(x, surface - 1, z);
                    if (!canOccupyWater(c)) {
                        continue;
                    }
                }
                double score = c.distSqr(target) + c.distSqr(botPos) * 0.15;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        return best;
    }

    private boolean canOccupyWater(Pos feet) {
        return WorldUtil.isWater(world(), feet) && WorldUtil.isWater(world(), feet.above());
    }

    private Pos breathingNode() {
        GameWorld w = world();
        Pos botPos = bot.blockPos();
        Pos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -2; dy <= 10; dy++) {
                    Pos water = botPos.offset(dx, dy, dz);
                    if (!WorldUtil.isWater(w, water)) {
                        continue;
                    }
                    Pos air = water.above();
                    if (!w.block(air).type().is(BlockTrait.AIR) || WorldUtil.hasFluid(w, air)) {
                        continue;
                    }
                    double score = water.distSqr(botPos);
                    if (score < bestScore) {
                        bestScore = score;
                        best = water;
                    }
                }
            }
        }
        return best;
    }

    private Pos anchorPos() {
        GameEntity v = bot.vehicle();
        if (v instanceof GameBoat boat) {
            Pos p = boat.blockPos();
            if (WorldUtil.isWater(world(), p)) {
                return p;
            }
            return WorldUtil.isWater(world(), p.below()) ? p.below() : p;
        }
        if (v instanceof GameHorse horse) {
            return horse.blockPos();
        }
        Pos p = bot.blockPos();
        BotBody e = bot.entity();
        if (e == null || bot.onGround() || bot.isInWater() || bot.isInLava() || e.isClimbing() || e.isGliding()) {
            return p;
        }
        // In the air (jumping, falling) the path starts where it will land. Starting from the point in the air the A*
        // finds no support and returns only that node: the bot landed and stayed under it, jumping and looking up.
        GameWorld w = world();
        for (int i = 0; i < LANDING_SCAN && p.y() - i > w.minHeight(); i++) {
            Pos feet = p.below(i);
            if (WorldUtil.hasFluid(w, feet) || WorldUtil.collides(w, feet.below())) {
                return feet;
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ debug

    private void updateDebug() {
        if (!bot.settings().debugPaths.on()) {
            clearDebug();
            return;
        }
        if (current == null) {
            bot.manager().plugin().debugRenderer().update(bot, Collections.emptyList(), 0, Set.of(), Set.of());
            return;
        }
        bot.manager().plugin().debugRenderer().update(bot, current.path(), pathIndex, current.toMine(), current.toPlace());
    }

    private void clearDebug() {
        bot.manager().plugin().debugRenderer().clear(bot.id());
    }

    private void emitResolveDebug(Pos botPos, Pos real, Pos resolved, boolean allowPlacement) {
        if (!bot.settings().debugPaths.on()) {
            return;
        }
        GamePlayer p = bot.target();
        if (p == null || !p.hasPermission("manhuntstar.admin")) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (now - lastResolveDebugTick < 6) {
            return;
        }
        lastResolveDebugTick = now;
        int horizontal = (int) Math.round(Math.hypot(real.x() - botPos.x(), real.z() - botPos.z()));
        int range = resolved == null ? -1 : botPos.manhattan(resolved);
        String label = resolved == null ? "null" : resolved.x() + "," + resolved.y() + "," + resolved.z();
        p.sendActionBar(Component.text(String.format("[A*] mode:%s | place:%s | dist:%d | range:%d | node:%s",
                lastResolveMode, allowPlacement ? "YES" : "NO", horizontal, range, label)));
    }
}
