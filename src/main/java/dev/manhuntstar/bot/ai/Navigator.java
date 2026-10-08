package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.pathfinding.AStar;
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
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;

/**
 * Navegação por caminho (A*) com as regras do mod: alvo "resolvido" (skybase →
 * ponto de aproximação, alvo distante → passo intermediário de 96 blocos), recálculo quando o
 * caminho fica velho ou o alvo anda, cálculo antecipado do próximo trecho, filas de blocos a
 * cavar/colocar, parkour e navegação na água (subir para respirar, sair pela margem).
 */
public final class Navigator {

    private static final double NODE_REACH_SQ = 2.25;
    /** Raio para dar o nó por alcançado quando não dá para seguir reto até o próximo (curva em quina). */
    private static final double NODE_REACH_TIGHT_SQ = 0.36;
    /** O nó seguido ficou mais que isso acima do bot (caiu, foi empurrado de uma beirada): recalcula. */
    private static final double STRAY_BELOW = 3.0;
    /** No chão, longe assim do nó seguido (de lado ou acima dele), o caminho não serve mais. */
    private static final double STRAY_HORIZONTAL = 5.0;
    private static final double STRAY_ABOVE = AStar.MAX_DIG_FALL + 2;
    /** Até onde procurar, embaixo do bot no ar, o chão em que ele vai pousar. */
    private static final int LANDING_SCAN = 64;
    private static final long STALE_MS = 2000L;
    private static final int LOOKAHEAD_TRIGGER = 25;
    private static final int MAX_DISTANCE = 150;
    private static final int APPROACH_RADIUS = 12;
    private static final int FAR_STEP = 96;
    /** Distâncias e desvios (graus) tentados, nessa ordem, para o ponto intermediário. */
    private static final int[] STEP_LENGTHS = {FAR_STEP, 64, 40, 20};
    private static final int[] STEP_ANGLES = {0, 30, -30, 60, -60};
    private static final int STEP_RADIUS = 5;
    /** O desvio escolhido vale até o bot chegar a esta distância dele ou o alvo andar mais que isso. */
    private static final int STEP_KEEP = 4;
    private static final int STEP_KEEP_TARGET_MOVE = 16;
    /** Em mundo com teto (Nether), quantos blocos acima e abaixo do bot procurar chão. */
    private static final int CEILING_BAND = 12;
    private static final int SKYBASE_MIN_ELEVATION = 3;
    /** Até essa diferença de altura e distância o bot está no nível do alvo (não precisa de aproximação). */
    private static final int SAME_LEVEL_DY = 2;
    private static final int SAME_LEVEL_RADIUS = 32;
    private static final int RESOLVE_CACHE_TICKS = 10;
    private static final int WATER_ASCEND_AIR = 60;
    private static final int WATER_RESUME_AIR = 220;
    /** Um pulo de 2–3 blocos leva menos de 1 s; passou de 3 s, o bot ficou preso. */
    private static final int PARKOUR_TIMEOUT_TICKS = 60;
    /** Trecho de água mais curto que isso não vale o barco, mesmo que o A* tenha passado por ele. */
    private static final int BOAT_MIN_RUN = 6;
    /** Quanto um bloco nadando vale em blocos correndo, na escolha do ponto de saída da água. */
    private static final double SWIM_EXIT_WEIGHT = 3.0;

    private enum WaterMode { NONE, CRUISE, ASCEND_FOR_AIR }

    /** Procura, numa coluna (x, z), onde ficar em pé. */
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
    /** Fim de caminho de onde o trecho extra não rendeu nada: não é pedido de novo de lá. */
    private Pos lookaheadDeadEnd;
    private final ArrayDeque<Pos> mineQueue = new ArrayDeque<>();
    private final ArrayDeque<Pos> placeQueue = new ArrayDeque<>();
    private boolean parkouring;
    private Pos parkourTarget;
    private int parkourTicks;
    private WaterMode waterMode = WaterMode.NONE;
    private Pos escapeTarget;
    /** O último ponto devolvido é um nó de caminho calculado (e não um "vá reto até lá"). */
    private boolean onPath;
    /** Como saiu o último ponto intermediário, para o debug: distância/desvio, água ou motivo da falha. */
    private String stepNote = "";
    /** Desvio em uso quando a reta de 96 blocos não tem chão, e o alvo para o qual foi escolhido. */
    private Pos keptStep;
    private Pos keptStepTarget;
    private String keptStepNote = "";
    /** Lado (1 ou -1) do último desvio: na próxima escolha esse lado é tentado primeiro. */
    private int stepSide = 1;
    /** Até este tick o A* não planeja barco (uma travessia acabou de falhar). */
    private int boatBlockedUntil;

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
        return astarWaypoint(resolveWaterAwareTarget(finalTarget));
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
        // Longe do caminho, o trecho extra pedido do fim dele não serve: recalcula de onde o bot está. Com um
        // cálculo inteiro já em andamento, espera por ele.
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
     * O último {@link #waypoint} veio de um caminho calculado. Quando é false o ponto é só uma direção
     * (alvo longe demais, caminho ainda sendo calculado ou que falhou) e quem anda precisa desviar dos
     * obstáculos sozinho.
     */
    public boolean onPath() {
        return onPath;
    }

    // ------------------------------------------------------------------ barco

    private boolean boatsAllowed() {
        return bot.settings().allowBoat.on() && Bukkit.getCurrentTick() >= boatBlockedUntil;
    }

    /** Depois de uma travessia que não deu certo o bot nada ou contorna por um tempo. */
    public void blockBoats(int ticks) {
        boatBlockedUntil = Bukkit.getCurrentTick() + ticks;
    }

    /**
     * Fim do trecho de água à frente no caminho, quando o A* decidiu atravessá-lo de barco. A pé só
     * responde com o bot já na beira de um trecho longo; de barco, devolve o fim do trecho em que ele está.
     */
    public Pos boatCrossingGoal(boolean riding) {
        if (current == null || !boatsAllowed()) {
            return null;
        }
        List<Pos> path = current.path();
        World w = world();
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

    private static boolean isBoatWater(World w, Pos p) {
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
                    // do fim do caminho não sai nada (ponto no ar, beco): pedia de novo de lá a cada tick
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
        task = bot.manager().plugin().pathService().findPath(world(), start, target, allowPlacement, boatsAllowed());
        lastTarget = target;
        // o trecho extra continua o caminho antigo: contar como cálculo novo impedia o caminho de ficar velho
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

    // ------------------------------------------------------------------ seguir os nós

    /**
     * O bot saiu de perto do nó que está seguindo (caiu de uma beirada, foi empurrado). Um nó bem acima dele
     * nunca é alcançado: ele ficava pulando embaixo, olhando para cima.
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
        // no ar (pulando, numa queda planejada) espera pousar
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
        Entity vehicle = bot.vehicle();
        Vec3 p = vehicle instanceof AbstractHorse ? Vec3.of(vehicle.getLocation()) : bot.pos();
        double dx = p.x() - centre.x();
        double dz = p.z() - centre.z();
        double dy = p.y() - centre.y();
        double distSq = dx * dx + dz * dz;
        // Perto do nó já vale seguir para o próximo, a não ser que a linha reta até ele bata num bloco:
        // aí o caminho faz uma curva em volta dele e o bot precisa chegar de fato no nó.
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
            // montado, o vão é pulado pelo cavalo (HorseRoutine), não pelo parkour a pé
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
     * A linha reta da posição atual até o centro do nó passa livre na altura dos pés e da cabeça do bot.
     * Degrau para cima não passa (o bloco do degrau está na altura dos pés), então subida nunca corta caminho.
     */
    private boolean straightWalkClear(Vec3 from, Pos node) {
        World w = world();
        Vec3 to = Vec3.bottomCenter(node);
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        int steps = (int) Math.ceil(Math.hypot(dx, dz) / 0.25);
        // +0.5: em cima de laje ou areia das almas os pés ficam no bloco de cima, não no da laje
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
        World w = world();
        // Para subir um degrau o corpo passa 2 blocos acima do nó atual. O A* conta com cavar esse
        // bloco, que não é nem os pés nem a cabeça do próximo nó; sem isso o bot fica preso embaixo dele.
        if (node.y() > from.y() && WorldUtil.collides(w, from.above(2))) {
            mineQueue.addLast(from.above(2));
        }
        // Cavar o chão e cair no vão de baixo (teto da caixa do alvo): o bloco cavado é o chão do nó atual.
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

    /**
     * Altura da maior queda que o caminho prevê logo à frente (até {@link AStar#MAX_DIG_FALL} blocos; 0 =
     * nenhuma). Quedas planejadas não precisam de MLG.
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

    // ------------------------------------------------------------------ resolução do alvo

    private Pos resolveTarget(Pos botPos, Pos real) {
        int now = Bukkit.getCurrentTick();
        // a falha também fica guardada: refazer a busca toda a cada tick custaria caro
        if (lastResolvedReal != null && now - lastResolvedTick <= RESOLVE_CACHE_TICKS && lastResolvedReal.manhattan(real) <= 2) {
            if (lastResolved != null) {
                lastResolveMode = "cache";
            }
            return lastResolved;
        }
        World w = world();
        // O ponto de aproximação é para alvo acima do bot (torre, plataforma no céu). Com o bot já no nível
        // do alvo e perto, ele mandava o bot para outro lugar (até o teto de uma caixa de vidro) e não no alvo.
        // Com o bot acima do alvo (no teto da caixa dele), mandava descer até o chão embaixo da plataforma.
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
            // rumo ao alvo real; se já foi essa a busca que falhou acima, não repete
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
     * Ponto intermediário rumo a um alvo distante. Primeiro 96 blocos em linha reta, como no mod; se ali
     * não houver chão (lago, mar, mata fechada), tenta para os lados e mais perto, e por fim a superfície
     * da água, porque o bot nada. Com barco liberado, água na reta já serve: ele atravessa remando. Só
     * aceita pontos ao alcance do A* ({@link #MAX_DISTANCE}). Um desvio
     * escolhido é mantido enquanto servir, senão o bot trocaria de lado a cada passo.
     */
    private Pos intermediateTarget(Pos botPos, Pos target) {
        stepNote = "";
        Vec3 flat = new Vec3(target.x() - botPos.x(), 0, target.z() - botPos.z());
        if (flat.lengthSqr() < 1.0E-6) {
            return null;
        }
        World w = world();
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
                        // de barco, atravessar em linha reta costuma ganhar de procurar terra para os lados
                        keptStep = null;
                        stepNote = "(barco)";
                        return top;
                    }
                    if (water == null && surfaceWater) {
                        water = top;
                    }
                }
            }
        }
        if (water != null) {
            stepNote = "(água)";
            keep(water, target, 0);
            return water;
        }
        keptStep = null;
        stepNote = lava ? "(lava)" : "(sem chão)";
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

    /** O desvio guardado ainda leva para perto do alvo, o bot não chegou nele e ainda dá para ficar lá. */
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
        World w = world();
        return WorldUtil.isLoaded(w, keptStep) && (WorldUtil.isWater(w, keptStep) || isStandableApproach(keptStep, false));
    }

    /** Chão da coluna para o ponto intermediário: o lugar em pé mais alto perto do terreno, sem contar copa de árvore. */
    private Pos groundInColumn(int x, int z) {
        World w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int surface = WorldUtil.surfaceYNoLeaves(w, x, z);
        int top = Math.min(w.getMaxHeight() - 2, surface + 4);
        int bottom = Math.max(w.getMinHeight() + 1, surface - 8);
        for (int y = top; y >= bottom; y--) {
            Pos feet = new Pos(x, y, z);
            if (isStandableApproach(feet, false) && !Tag.LEAVES.isTagged(feet.below().block(w).getType())) {
                return feet;
            }
        }
        return null;
    }

    /**
     * Em mundo com teto (Nether) o mapa de alturas aponta o teto de bedrock; procura chão perto da altura
     * do bot, abaixo do teto.
     */
    private Pos groundNearY(int x, int z, int refY) {
        World w = world();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int low = Math.max(w.getMinHeight() + 1, refY - CEILING_BAND);
        int high = Math.min(w.getLogicalHeight() - 1, refY + CEILING_BAND);
        for (int i = 0; i <= 2 * CEILING_BAND; i++) {
            // refY, refY+1, refY-1, refY+2, ...: o mais perto da altura do bot primeiro
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

    /** Bot na altura do alvo e perto: já está na plataforma dele (ou ao lado) e o caminho normal resolve. */
    private static boolean sameLevelNearby(Pos botPos, Pos target) {
        return Math.abs(botPos.y() - target.y()) <= SAME_LEVEL_DY
                && Math.hypot(botPos.x() - target.x(), botPos.z() - target.z()) <= SAME_LEVEL_RADIUS;
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
            ShoreExit exit = shoreExitToward(finalTarget);
            if (exit == null) {
                return finalTarget;
            }
            // já na saída: mira o chão firme para subir (mirar a própria água deixava o bot sem direção)
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

    /** Bloco de água na beira e o chão firme ao lado dele, por onde o bot sai da água. */
    private record ShoreExit(Pos water, Pos land) {
    }

    /**
     * Saída da água mais rápida até o alvo: nadar custa ~3× correr. Com distâncias ao quadrado a
     * saída mais longe vencia quando o alvo seguia pela margem, e o bot nadava rente à beira sem sair.
     */
    private ShoreExit shoreExitToward(Vec3 finalTarget) {
        World w = world();
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
        Pos p = bot.blockPos();
        Mannequin e = bot.entity();
        if (e == null || bot.onGround() || bot.isInWater() || bot.isInLava() || e.isClimbing() || e.isGliding()) {
            return p;
        }
        // No ar (pulando, caindo) o caminho sai de onde ele vai pousar. Saindo do ponto no ar o A* não acha
        // apoio e devolve só aquele nó: o bot pousava e ficava embaixo dele, pulando e olhando para cima.
        World w = world();
        for (int i = 0; i < LANDING_SCAN && p.y() - i > w.getMinHeight(); i++) {
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
        p.sendActionBar(Component.text(String.format("[A*] modo:%s | colocar:%s | dist:%d | alcance:%d | nó:%s",
                lastResolveMode, allowPlacement ? "SIM" : "NÃO", horizontal, range, label)));
    }
}
