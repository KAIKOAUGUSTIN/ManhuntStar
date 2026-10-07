package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.Modes.PathMode;
import dev.manhuntstar.pathfinding.FlowField;
import dev.manhuntstar.pathfinding.PathResult;
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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Navegação por caminho (A* ou campo de fluxo) com as regras do mod: alvo "resolvido" (skybase →
 * ponto de aproximação, alvo distante → passo intermediário de 96 blocos), recálculo quando o
 * caminho fica velho ou o alvo anda, cálculo antecipado do próximo trecho, filas de blocos a
 * cavar/colocar, parkour e navegação na água (subir para respirar, sair pela margem).
 */
public final class Navigator {

    private static final double NODE_REACH_SQ = 2.25;
    private static final long STALE_MS = 2000L;
    private static final int LOOKAHEAD_TRIGGER = 25;
    private static final int MAX_DISTANCE = 150;
    private static final int APPROACH_RADIUS = 12;
    private static final int FAR_STEP = 96;
    private static final int SKYBASE_MIN_ELEVATION = 3;
    private static final int RESOLVE_CACHE_TICKS = 10;
    private static final int WATER_ASCEND_AIR = 60;
    private static final int WATER_RESUME_AIR = 220;
    /** Um pulo de 2–3 blocos leva menos de 1 s; passou de 3 s, o bot ficou preso. */
    private static final int PARKOUR_TIMEOUT_TICKS = 60;

    private enum WaterMode { NONE, CRUISE, ASCEND_FOR_AIR }

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
    private final ArrayDeque<Pos> mineQueue = new ArrayDeque<>();
    private final ArrayDeque<Pos> placeQueue = new ArrayDeque<>();
    private boolean parkouring;
    private Pos parkourTarget;
    private int parkourTicks;
    private Pos lastFlowTarget;
    private WaterMode waterMode = WaterMode.NONE;
    private Pos escapeTarget;

    public Navigator(Bot bot) {
        this.bot = bot;
        this.personality = new Personality(bot.id());
    }

    private World world() {
        return bot.world();
    }

    // ------------------------------------------------------------------ entrada principal

    /** Próximo ponto para andar rumo ao alvo, ou null se não houver caminho e o fallback estiver desligado. */
    public Vec3 waypoint(Vec3 finalTarget) {
        personality.tick();
        Vec3 effective = resolveWaterAwareTarget(finalTarget);
        if (bot.settings().pathMode.get() == PathMode.DIJKSTRA) {
            return dijkstraWaypoint(effective);
        }
        return astarWaypoint(effective);
    }

    /** Foge do ponto de ameaça para um lugar a 40–70 blocos na direção oposta. */
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
        World w = world();
        int ty = w.isChunkLoaded(tx >> 4, tz >> 4) ? WorldUtil.surfaceY(w, tx, tz) : bot.blockPos().y();
        return new Pos(tx, ty, tz);
    }

    // ------------------------------------------------------------------ A*

    private Vec3 astarWaypoint(Vec3 finalTarget) {
        Pos botPos = anchorPos();
        Pos realTarget = finalTarget.toPos();
        Pos targetPos = resolveTarget(botPos, realTarget);
        boolean allowPlacement = bot.brain().canPlaceToweringBlocksNearTarget();
        emitResolveDebug(botPos, realTarget, targetPos, allowPlacement, "A*");
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
        if (current != null && task == null && !current.complete() && !needsRecalc) {
            int total = current.path().size();
            if (pathIndex >= total / 2 && total - pathIndex <= LOOKAHEAD_TRIGGER) {
                lookahead = true;
                requestPath(current.path().get(total - 1), targetPos);
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
            updateDebug();
            return Vec3.bottomCenter(current.path().get(pathIndex));
        }
        Vec3 wp = advanceToNextNode(finalTarget);
        updateDebug();
        return wp;
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
        }
        lookahead = false;
        task = null;
        mineQueue.clear();
        placeQueue.clear();
        if (bot.settings().debugPaths.on()) {
            Player p = bot.target();
            if (p != null && p.hasPermission("manhuntstar.admin")) {
                var ps = bot.manager().plugin().pathService();
                String msg = current == null
                        ? String.format("[A*] FALHOU | cópia: %dms | fila: %d", ps.lastCaptureMs(), ps.activeTasks())
                        : String.format("[A*] %s | %dms | %d nós | cópia: %dms | fila: %d", current.complete() ? "COMPLETO" : "TRECHO",
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
        task = bot.manager().plugin().pathService().findPath(world(), start, target, allowPlacement);
        lastTarget = target;
        lastCalcMs = System.currentTimeMillis();
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

    // ------------------------------------------------------------------ Dijkstra (campo de fluxo)

    private Vec3 dijkstraWaypoint(Vec3 finalTarget) {
        Pos botPos = anchorPos();
        Pos real = finalTarget.toPos();
        Pos targetPos = resolveTarget(botPos, real);
        emitResolveDebug(botPos, real, targetPos, bot.brain().canPlaceToweringBlocksNearTarget(), "Dijkstra");
        if (targetPos == null) {
            clearDebug();
            return finalTarget;
        }
        int retarget = recalcThreshold(botPos.manhattan(targetPos));
        if (lastFlowTarget == null || lastFlowTarget.manhattan(targetPos) > retarget) {
            lastFlowTarget = targetPos;
        }
        FlowField field = bot.manager().plugin().pathService().flowField(world(), lastFlowTarget);
        if (field == null) {
            return Vec3.bottomCenter(targetPos);
        }
        Vec3 raw = field.waypoint(botPos.x(), botPos.y(), botPos.z());
        if (raw == null) {
            return Vec3.bottomCenter(targetPos);
        }
        Pos next = raw.toPos();
        World w = world();
        if (!bot.mining().isMining() && !bot.isRiding()) {
            if (WorldUtil.collides(w, next)) {
                bot.mining().startNoStop(next);
            } else if (WorldUtil.collides(w, next.above())) {
                bot.mining().startNoStop(next.above());
            }
        }
        Vec3 moveDir = raw.subtract(bot.pos()).horizontal();
        double len = moveDir.length();
        if (len > 1.0E-6) {
            moveDir = moveDir.scale(1.0 / len);
        }
        if (bot.settings().debugPaths.on()) {
            List<Pos> sim = new ArrayList<>();
            int cx = botPos.x();
            int cy = botPos.y();
            int cz = botPos.z();
            for (int i = 0; i < 60; i++) {
                sim.add(new Pos(cx, cy, cz));
                int[] d = field.direction(cx, cy, cz);
                if (d == null || (d[0] == 0 && d[1] == 0 && d[2] == 0)) {
                    break;
                }
                cx += d[0];
                cy += d[1];
                cz += d[2];
            }
            bot.manager().plugin().debugRenderer().update(bot, sim, 0, Set.of(), Set.of());
        } else {
            clearDebug();
        }
        return personality.humanize(raw, moveDir, bot.settings().humanize.value());
    }

    // ------------------------------------------------------------------ seguir os nós

    private Vec3 advanceToNextNode(Vec3 finalTarget) {
        if (current == null || pathIndex >= current.path().size()) {
            return finalTarget;
        }
        Pos next = current.path().get(pathIndex);
        Vec3 centre = Vec3.bottomCenter(next);
        Entity vehicle = bot.vehicle();
        Vec3 p = vehicle instanceof AbstractHorse ? Vec3.of(vehicle.getLocation()) : bot.pos();
        double dx = p.x() - centre.x();
        double dz = p.z() - centre.z();
        double dy = p.y() - centre.y();
        if (dx * dx + dz * dz < NODE_REACH_SQ && dy > -0.2 && dy < 1.5) {
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
            // montado, o vão é pulado pelo cavalo (HorseRoutine), não pelo parkour a pé
            if ((gapX >= 2 || gapZ >= 2) && (gapX == 0 || gapZ == 0) && vehicle == null) {
                parkouring = true;
                parkourTarget = next;
                parkourTicks = 0;
            }
            enqueueActions(next);
        }
        return centre;
    }

    private void enqueueActions(Pos node) {
        if (bot.isRiding() || current == null) {
            return;
        }
        World w = world();
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

    /** @return true enquanto cava/coloca blocos do caminho (o bot fica no nó atual). */
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
        World w = world();
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
            bot.placeBlock(floor, Material.COBBLESTONE, true);
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
            // o pulo não deu certo e o bot ficou num vão raso: desiste e recalcula o caminho
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

    /** Pulo de corrida em reta plana (ou sob teto baixo), como o jogador faz para ganhar velocidade. */
    public boolean shouldSprintJump() {
        if (current == null || pathIndex >= current.path().size() || parkouring) {
            return false;
        }
        Pos cur = bot.blockPos();
        Pos next1 = current.path().get(pathIndex);
        if (next1.y() != cur.y()) {
            return false;
        }
        World w = world();
        boolean ceiling = WorldUtil.collides(w, cur.above(2)) || WorldUtil.collides(w, next1.above(2));
        if (ceiling) {
            return true;
        }
        return pathIndex + 1 < current.path().size() && current.path().get(pathIndex + 1).y() == cur.y();
    }

    /** Há bloco inquebrável (dureza ≥ 50 ou negativa) entre os olhos do bot e o ponto. */
    public boolean hasIndestructibleBlockInPath(Vec3 targetEye) {
        Vec3 from = bot.eyePos();
        Vec3 dir = targetEye.subtract(from);
        double len = dir.length();
        if (len < 0.01) {
            return false;
        }
        Vec3 unit = dir.normalize().scale(0.5);
        int steps = (int) Math.ceil(len / 0.5);
        World w = world();
        for (int i = 1; i <= steps; i++) {
            Pos check = from.add(unit.scale(i)).toPos();
            if (!WorldUtil.isLoaded(w, check)) {
                return false;
            }
            Block b = check.block(w);
            if (b.getType().isAir() || b.isPassable()) {
                continue;
            }
            float h = b.getType().getHardness();
            if (h >= 50f || h < 0f) {
                return true;
            }
        }
        return false;
    }

    /** O caminho prevê uma queda de 1 a 3 blocos logo à frente (não precisa de MLG). */
    public boolean isPlannedFall() {
        if (current == null || pathIndex >= current.path().size()) {
            return false;
        }
        List<Pos> path = current.path();
        int from = Math.max(0, pathIndex - 1);
        int to = Math.min(path.size() - 1, pathIndex + 3);
        for (int i = from; i < to; i++) {
            int dy = path.get(i).y() - path.get(i + 1).y();
            if (dy >= 1 && dy <= 3) {
                return true;
            }
        }
        return false;
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
        lastFlowTarget = null;
        mineQueue.clear();
        placeQueue.clear();
        parkouring = false;
        lookahead = false;
        cancelTask();
        clearDebug();
    }

    // ------------------------------------------------------------------ resolução do alvo

    private Pos resolveTarget(Pos botPos, Pos real) {
        int now = Bukkit.getCurrentTick();
        if (lastResolved != null && lastResolvedReal != null && now - lastResolvedTick <= RESOLVE_CACHE_TICKS
                && lastResolvedReal.manhattan(real) <= 2) {
            lastResolveMode = "cache";
            return lastResolved;
        }
        World w = world();
        boolean needsApproach = isTargetAirborneOrSkybasing(w, real);
        Pos resolved = real;
        String mode = needsApproach ? "skybase" : "direct";
        if (needsApproach) {
            Pos approach = closestStandableNear(real, botPos, true, APPROACH_RADIUS);
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
                mode += "+step";
            }
        }
        if (botPos.manhattan(resolved) > MAX_DISTANCE) {
            Pos fallback = intermediateTarget(botPos, real);
            if (fallback == null || botPos.manhattan(fallback) > MAX_DISTANCE) {
                lastResolveMode = mode + "+null";
                return null;
            }
            resolved = fallback;
            mode += "+force-step";
        }
        if (needsApproach && resolved.equals(real)) {
            Pos soft = closestStandableNear(real, botPos, false, APPROACH_RADIUS);
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

    private Pos intermediateTarget(Pos botPos, Pos target) {
        Vec3 flat = new Vec3(target.x() - botPos.x(), 0, target.z() - botPos.z());
        if (flat.lengthSqr() < 1.0E-6) {
            return null;
        }
        Vec3 step = flat.normalize().scale(Math.min(FAR_STEP, flat.length()));
        int x = botPos.x() + (int) Math.round(step.x());
        int z = botPos.z() + (int) Math.round(step.z());
        World w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return new Pos(x, botPos.y(), z);
        }
        return closestStandableNear(new Pos(x, WorldUtil.surfaceYNoLeaves(w, x, z), z), botPos, false, 5);
    }

    private Pos closestStandableNear(Pos target, Pos botPos, boolean avoidPlayerSupport, int radius) {
        Pos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    Pos c = standableInColumn(target.x() + dx, target.z() + dz, target.y(), avoidPlayerSupport);
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
        World w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int surface = WorldUtil.surfaceYNoLeaves(w, x, z);
        int top = Math.min(w.getMaxHeight() - 2, Math.max(targetY, surface + 4));
        int bottom = Math.max(w.getMinHeight() + 1, surface - 8);
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
        World w = world();
        for (int y = Math.min(targetY, w.getMaxHeight() - 2); y >= w.getMinHeight(); y--) {
            Pos p = new Pos(x, y, z);
            Block b = p.block(w);
            if (b.getType().isAir() || b.isReplaceable()) {
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
        World w = world();
        boolean clearFeet = !WorldUtil.hasCollision(w, feet) && !WorldUtil.hasFluid(w, feet);
        boolean clearHead = !WorldUtil.hasCollision(w, feet.above()) && !WorldUtil.hasFluid(w, feet.above());
        Pos floor = feet.below();
        Block fb = floor.block(w);
        boolean safeFloor = !fb.getType().isAir() && !WorldUtil.hasFluid(w, floor)
                && (fb.getType().isSolid() || WorldUtil.isSturdyTop(w, floor));
        if (!clearFeet || !clearHead || !safeFloor) {
            return false;
        }
        return !avoidElevated || (!isPlayerPlaced(floor) && !hasLargeAirGapBelow(w, floor));
    }

    private boolean isPlayerPlaced(Pos p) {
        return bot.manager().blockTracker().isPlayerPlaced(world(), p);
    }

    private boolean isTargetAirborneOrSkybasing(World w, Pos target) {
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
            if (p.y() < w.getMinHeight()) {
                break;
            }
            Block b = p.block(w);
            boolean empty = b.getType().isAir() || (b.isReplaceable() && !WorldUtil.hasFluid(w, p));
            if (empty || isPlayerPlaced(p)) {
                continue;
            }
            return target.y() - p.y() > 3;
        }
        return true;
    }

    private Pos firstNaturalSupportBelow(World w, Pos start) {
        for (int i = 1; i <= 64; i++) {
            Pos p = start.below(i);
            if (p.y() < w.getMinHeight()) {
                return null;
            }
            Block b = p.block(w);
            boolean empty = b.getType().isAir() || (b.isReplaceable() && !WorldUtil.hasFluid(w, p));
            if (!empty && !isPlayerPlaced(p)) {
                return p;
            }
        }
        return null;
    }

    private static Pos firstSupportBelow(World w, Pos target) {
        for (int y = target.y() - 1; y >= Math.max(w.getMinHeight(), target.y() - 8); y--) {
            Pos p = new Pos(target.x(), y, target.z());
            Block b = p.block(w);
            if (b.getType().isAir() || (b.isReplaceable() && !WorldUtil.hasFluid(w, p))) {
                continue;
            }
            return p;
        }
        return null;
    }

    private static boolean hasLargeAirGapBelow(World w, Pos support) {
        int open = 0;
        for (int y = support.y() - 1; y >= Math.max(w.getMinHeight(), support.y() - 8); y--) {
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

    private static boolean hasNearbyWater(World w, Pos target) {
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

    // ------------------------------------------------------------------ água

    private Vec3 resolveWaterAwareTarget(Vec3 finalTarget) {
        if (bot.vehicle() instanceof Boat) {
            waterMode = WaterMode.NONE;
            return boatSurfaceTarget(finalTarget);
        }
        if (!bot.isInWater() || bot.isInLava()) {
            waterMode = WaterMode.NONE;
            return finalTarget;
        }
        if (isDryLandTarget(finalTarget)) {
            waterMode = WaterMode.NONE;
            Pos exit = shoreExitToward(finalTarget);
            return exit == null ? finalTarget : Vec3.bottomCenter(exit);
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
        World w = world();
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
        World w = world();
        Pos feet = finalTarget.toPos();
        if (WorldUtil.isWater(w, feet) || WorldUtil.isWater(w, feet.below())) {
            return false;
        }
        Block below = feet.below().block(w);
        return !below.getType().isAir() && !WorldUtil.hasFluid(w, feet.below()) && below.getType().isOccluding();
    }

    /** O alvo não está sobre chão firme (está na água/no ar): navegar pela água em vez de seguir direto. */
    private boolean isTargetOffSolidGround(Vec3 finalTarget) {
        World w = world();
        Pos below = finalTarget.toPos().below();
        Block b = below.block(w);
        return !(!b.getType().isAir() && !WorldUtil.hasFluid(w, below) && b.getType().isOccluding());
    }

    private void updateWaterMode() {
        int air = bot.entity() == null ? 300 : bot.entity().getRemainingAir();
        if (waterMode == WaterMode.ASCEND_FOR_AIR) {
            if (air >= WATER_RESUME_AIR) {
                waterMode = WaterMode.CRUISE;
            }
            return;
        }
        waterMode = air <= WATER_ASCEND_AIR ? WaterMode.ASCEND_FOR_AIR : WaterMode.CRUISE;
    }

    private Pos shoreExitToward(Vec3 finalTarget) {
        World w = world();
        Pos botPos = bot.blockPos();
        Pos targetPos = finalTarget.toPos();
        Vec3 toward = finalTarget.subtract(bot.pos()).horizontal();
        toward = toward.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : toward.normalize();
        Pos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Pos water = botPos.offset(dx, dy, dz);
                    if (!WorldUtil.isWater(w, water) || WorldUtil.hasFluid(w, water.above())) {
                        continue;
                    }
                    for (BlockFace face : Pos.HORIZONTALS) {
                        Pos landFeet = water.relative(face);
                        Pos landBelow = landFeet.below();
                        Block lb = landBelow.block(w);
                        if (lb.getType().isAir() || WorldUtil.hasFluid(w, landBelow)) {
                            continue;
                        }
                        if (!lb.getType().isSolid() && !WorldUtil.isSturdyTop(w, landBelow)) {
                            continue;
                        }
                        if (WorldUtil.hasCollision(w, landFeet) || WorldUtil.hasCollision(w, landFeet.above())
                                || WorldUtil.hasFluid(w, landFeet) || WorldUtil.hasFluid(w, landFeet.above())) {
                            continue;
                        }
                        Vec3 edge = new Vec3(face.getModX(), 0, face.getModZ());
                        double align = 1.0 - Math.max(0.0, edge.normalize().dot(toward));
                        double score = water.distSqr(botPos) * 0.45 + landFeet.distSqr(targetPos) + Math.abs(water.y() - botPos.y()) * 2.0 + align * 4.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = water;
                        }
                    }
                }
            }
        }
        return best;
    }

    private Pos cruiseNodeToward(Vec3 finalTarget) {
        World w = world();
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
                while (surface < w.getMaxHeight() - 1 && WorldUtil.isWater(w, new Pos(x, surface + 1, z))) {
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
        World w = world();
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
                    if (!air.block(w).getType().isAir() || WorldUtil.hasFluid(w, air)) {
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
        Entity v = bot.vehicle();
        if (v instanceof Boat boat) {
            Pos p = Pos.of(boat.getLocation());
            if (WorldUtil.isWater(world(), p)) {
                return p;
            }
            return WorldUtil.isWater(world(), p.below()) ? p.below() : p;
        }
        if (v instanceof AbstractHorse horse) {
            return Pos.of(horse.getLocation());
        }
        return bot.blockPos();
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

    private void emitResolveDebug(Pos botPos, Pos real, Pos resolved, boolean allowPlacement, String planner) {
        if (!bot.settings().debugPaths.on()) {
            return;
        }
        Player p = bot.target();
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
        p.sendActionBar(Component.text(String.format("[%s] modo:%s | colocar:%s | dist:%d | alcance:%d | nó:%s",
                planner, lastResolveMode, allowPlacement ? "SIM" : "NÃO", horizontal, range, label)));
    }
}
