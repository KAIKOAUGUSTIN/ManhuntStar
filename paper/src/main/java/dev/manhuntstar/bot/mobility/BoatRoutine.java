package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.ai.Navigator;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Barco: perseguir quem está de barco (ou, da fase 6 em diante, quem está nadando), colocando um
 * barco próprio na água e remando por um caminho na superfície; descer perto da margem, encostar
 * no barco do alvo para atacar ou embarcar como passageiro. Também atravessa de barco o trecho de
 * água que o A* escolheu em vez de contornar um lago. O barco é dirigido como o cliente faria
 * (giro ±1°/tick acumulado, impulso 0,04 para frente), porque só jogadores mandam remadas.
 */
public final class BoatRoutine {

    private static final double SEARCH_RADIUS_SQ = 40.0 * 40.0;
    /** Sem novo pedido do caminho por esse tempo, a travessia a pé é cancelada. */
    private static final int CROSSING_REQUEST_TTL = 5;
    /** Depois de uma travessia que falhou, o A* fica esse tempo sem planejar barco (30 s). */
    private static final int CROSSING_FAIL_BLOCK_TICKS = 600;
    /** Custo extra por passo em água encostada na margem: o casco raspa na terra e o barco quase para. */
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
    /** Travessia pedida pelo caminho: onde descer, quem está sendo seguido e o prazo. */
    private Pos crossingGoal;
    private LivingEntity crossingFollow;
    private int crossingRequestTick;
    private int crossingTicks;
    private int crossingBudget;

    public BoatRoutine(Bot bot) {
        this.bot = bot;
    }

    /**
     * O caminho tem à frente um trecho de água que o A* decidiu atravessar de barco, terminando em
     * {@code goal}. O pedido vale enquanto for repetido; o bot embarca quando chegar na beira.
     */
    public void requestCrossing(Pos goal, LivingEntity follow) {
        if (!bot.settings().allowBoat.on()) {
            return;
        }
        if (crossingGoal == null) {
            crossingTicks = 0;
            crossingBudget = budgetFor(bot.pos(), goal);
        }
        crossingGoal = goal;
        crossingFollow = follow;
        crossingRequestTick = Bukkit.getCurrentTick();
    }

    public boolean isCrossing() {
        return crossingGoal != null;
    }

    /** Prazo da travessia: 10 s de folga mais 6 ticks por bloco (o barco faz ~2,5 ticks por bloco). */
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
        if (riderBoat != null && bot.tickDelay(20) && !(Bukkit.getEntity(riderBoat) instanceof Boat b && b.isValid())) {
            riderBoat = null;
            noMoveTicks = 0;
            clearAssault();
        }
    }

    public boolean isRiderBoat(Boat b) {
        return riderBoat != null && riderBoat.equals(b.getUniqueId());
    }

    private Boat riderBoatEntity() {
        return riderBoat != null && Bukkit.getEntity(riderBoat) instanceof Boat b && b.isValid() ? b : null;
    }

    /** @return true se o barco controlou o bot neste tick. */
    public boolean handle() {
        Entity vehicle = bot.vehicle();
        if (!bot.settings().allowBoat.on()) {
            if (vehicle instanceof Boat b && isRiderBoat(b)) {
                cleanup(true);
            }
            clearAssault();
            clearCrossing();
            return false;
        }
        Player boating = findNearbyBoatingPlayer();
        if (crossingGoal != null) {
            if (boating == null) {
                return handleCrossing(vehicle);
            }
            // alguém de barco por perto: vale a perseguição de barco de sempre
            clearCrossing();
        }
        Player chase = primaryChaseTarget();
        boolean phase6WaterChase = bot.progression().phase() >= 6 && chase != null && !isOutOfWater(chase);
        boolean deferToVertical = boating == null && chase != null && shouldDeferToVerticalChase(chase);
        Player opsTarget = boating != null ? boating : (phase6WaterChase && !deferToVertical ? chase : null);
        if (assaultActive && (boating == null || assaultTarget == null || !assaultTarget.equals(boating.getUniqueId()))) {
            clearAssault();
        }
        if (vehicle instanceof Boat mounted) {
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
        if (assaultActive && boating != null && assaultTarget != null && assaultTarget.equals(boating.getUniqueId())) {
            return bot.distanceTo(boating) > 7.0 && tryReenterParkedBoat(boating);
        }
        if (reboardCooldown > 0) {
            return false;
        }
        if (boating != null && boating.getVehicle() instanceof Boat targetBoat && tryBoardAsPassenger(boating, targetBoat)) {
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
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.OAK_BOAT));
        bot.lookAt(Vec3.center(water));
        World w = bot.world();
        Location loc = new Location(w, water.x() + 0.5, water.y() + 1.0, water.z() + 0.5, bot.yaw(), 0);
        if (!(w.spawnEntity(loc, EntityType.OAK_BOAT) instanceof Boat boat)) {
            return false;
        }
        bot.brain().mlg().trackSpawnedBoat(boat);
        riderBoat = boat.getUniqueId();
        clearAssault();
        noMoveTicks = 0;
        actionCooldown = 8;
        bot.swing();
        boat.addPassenger(bot.entity());
        bot.motor().resetFallTracking();
        return true;
    }

    // ------------------------------------------------------------------ travessia pedida pelo caminho

    private boolean handleCrossing(Entity vehicle) {
        LivingEntity follow = crossingFollow;
        boolean followGone = follow == null || follow.isDead() || !follow.getWorld().equals(bot.world());
        if (vehicle instanceof Boat mounted && isRiderBoat(mounted)) {
            crossingTicks++;
            if (!followGone && bot.tickDelay(10)) {
                // o caminho é recalculado a partir do barco; o fim do trecho de água pode ter mudado
                Navigator nav = bot.brain().navigator();
                nav.waypoint(Vec3.of(follow.getLocation()));
                Pos next = nav.boatCrossingGoal(true);
                if (next != null && !next.equals(crossingGoal)) {
                    crossingGoal = next;
                    crossingBudget = budgetFor(Vec3.of(mounted.getLocation()), next);
                }
            }
            Location bl = mounted.getLocation();
            Vec3 goal = Vec3.bottomCenter(crossingGoal);
            boolean arrived = Math.hypot(goal.x() - bl.getX(), goal.z() - bl.getZ()) < 2.5;
            boolean close = !followGone && bl.distance(follow.getLocation()) < 4.0;
            boolean stuck = noMoveTicks >= 40;
            boolean late = crossingTicks > crossingBudget;
            if (followGone || close || stuck || late || shouldDismountNearShore(mounted) || (arrived && isBoatCloseToLand(mounted))) {
                finishCrossing(stuck || late);
                return false;
            }
            if (arrived) {
                // o caminho acaba no meio da água (alvo ainda longe): espera o próximo trecho
                deltaRotation = 0;
                bot.motor().stopWalk();
                return true;
            }
            steerTowards(mounted, goal);
            return true;
        }
        if (vehicle != null || followGone || Bukkit.getCurrentTick() - crossingRequestTick > CROSSING_REQUEST_TTL) {
            // o caminho deixou de pedir a travessia (desviou, perdeu o alvo) ou o bot montou em outra coisa
            clearCrossing();
            return false;
        }
        if (actionCooldown > 0 || reboardCooldown > 0) {
            return false;
        }
        // a pé: embarca quando estiver na beira; até lá o caminho normal leva o bot até a água
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
        // caminho novo a partir da margem: o antigo ainda apontava para o começo da água
        nav.reset();
    }

    private void clearCrossing() {
        crossingGoal = null;
        crossingFollow = null;
    }

    private boolean handleForeignBoat(Boat mounted, Player chase) {
        if (mounted.getPassengers().indexOf(bot.entity()) == 0) {
            forceDismount(mounted);
            return false;
        }
        if (passengerHost == null && chase != null && chase.getVehicle() == mounted) {
            passengerHost = chase.getUniqueId();
        }
        boolean hostInBoat = passengerHost != null && mounted.getPassengers().stream().anyMatch(e -> e.getUniqueId().equals(passengerHost));
        if (!hostInBoat || chase == null || chase.isDead() || chase.getVehicle() != mounted || isOutOfWater(chase)) {
            forceDismount(mounted);
            return false;
        }
        return true;
    }

    private boolean handleRiding(Boat mounted, Player boating, Player chase, boolean deferToVertical) {
        if (deferToVertical || (boating == null && chase != null && isOutOfWater(chase))
                || (chase != null && isOutOfWater(chase) && isBoatCloseToLand(mounted))) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            return false;
        }
        if (chase != null && !chase.isDead() && !(chase.getVehicle() instanceof Boat)
                && mounted.getLocation().distance(chase.getLocation()) <= 3.0) {
            cleanup(true);
            bot.inventory().restorePreferredMainHand();
            if (bot.distanceTo(chase) <= 3.3) {
                bot.combat().attack(chase);
            }
            return false;
        }
        if (boating != null && boating.getVehicle() instanceof Boat targetBoat
                && mounted.getLocation().distance(targetBoat.getLocation()) <= 3.2) {
            if (tryBoardAsPassenger(boating, targetBoat)) {
                return true;
            }
            assaultActive = true;
            assaultTarget = boating.getUniqueId();
            park(mounted, Vec3.of(boating.getLocation()));
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
            steer = Vec3.of(boating.getVehicle() instanceof Boat tb ? tb.getLocation() : boating.getLocation());
        }
        if (steer == null && chase != null && !chase.isDead()) {
            steer = Vec3.of(chase.getLocation());
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

    // ------------------------------------------------------------------ direção

    private void steerTowards(Boat boat, Vec3 chaseTarget) {
        Vec3 nav = surfaceWaypoint(boat, chaseTarget);
        Location bl = boat.getLocation();
        Vec3 to = new Vec3(nav.x() - bl.getX(), 0, nav.z() - bl.getZ());
        if (to.lengthSqr() < 0.09) {
            deltaRotation = 0;
            bot.motor().stopWalk();
            return;
        }
        bot.lookAt(nav.add(0, 1, 0));
        Vec3 desired = to.normalize();
        float desiredYaw = desired.yaw();
        float yawDelta = Bot.wrapDegrees(desiredYaw - bl.getYaw());
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
        float yaw = bl.getYaw() + deltaRotation;
        boat.setRotation(yaw, 0);
        if (forward) {
            accel += 0.04f;
        }
        double rad = Math.toRadians(yaw);
        Vector v = boat.getVelocity();
        boat.setVelocity(v.add(new Vector(-Math.sin(rad) * accel, 0, Math.cos(rad) * accel)));
        deltaRotation *= 0.9f;
        double speed = Math.hypot(v.getX(), v.getZ());
        noMoveTicks = forward && speed < 0.03 ? noMoveTicks + 1 : 0;
        bot.motor().stopWalk();
    }

    private Vec3 surfaceWaypoint(Boat boat, Vec3 chaseTarget) {
        Pos start = nearestSurfaceWater(Pos.of(boat.getLocation()), 2, 3);
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
        Location bl = boat.getLocation();
        while (surfaceIndex < surfacePath.size()) {
            Vec3 c = Vec3.bottomCenter(surfacePath.get(surfaceIndex));
            double dx = bl.getX() - c.x();
            double dz = bl.getZ() - c.z();
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

    /** Algum dos quatro vizinhos não é água (chunk descarregado não conta). */
    private boolean touchesShore(Pos p, Map<Long, Boolean> cache) {
        Boolean known = cache.get(p.asLong());
        if (known != null) {
            return known;
        }
        World w = bot.world();
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
        World w = bot.world();
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

    private static boolean isSurfaceWater(World w, Pos p) {
        if (!WorldUtil.isWater(w, p) || WorldUtil.hasFluid(w, p.above())) {
            return false;
        }
        Block above = p.above().block(w);
        return above.getType().isAir() || above.isReplaceable();
    }

    private void clearSurfacePath() {
        surfacePath = Collections.emptyList();
        surfaceIndex = 0;
        surfaceRecalc = 0;
        surfaceStart = null;
        surfaceGoal = null;
    }

    // ------------------------------------------------------------------ embarque / desembarque

    private boolean tryBoardAsPassenger(Player target, Boat targetBoat) {
        if (!targetBoat.isValid() || target.getVehicle() != targetBoat || targetBoat.getPassengers().size() >= 2) {
            return false;
        }
        if (bot.pos().distanceTo(Vec3.of(targetBoat.getLocation())) > 2.5) {
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(Vec3.of(targetBoat.getLocation()).add(0, 0.5, 0));
        boolean boarded = targetBoat.addPassenger(bot.entity());
        if (boarded) {
            passengerHost = target.getUniqueId();
        }
        return boarded;
    }

    private void park(Boat boat, Vec3 attackTarget) {
        boat.setVelocity(new Vector(0, boat.getVelocity().getY(), 0));
        bot.lookAt(attackTarget.add(0, 1, 0));
        boat.removePassenger(bot.entity());
        deltaRotation = 0;
        actionCooldown = Math.max(actionCooldown, 6);
        clearSurfacePath();
        bot.motor().stopWalk();
    }

    private boolean tryReenterParkedBoat(Player target) {
        if (target == null || riderBoat == null) {
            return false;
        }
        Boat parked = riderBoatEntity();
        if (parked == null) {
            riderBoat = null;
            clearSurfacePath();
            clearAssault();
            return false;
        }
        if (!parked.getPassengers().isEmpty() && !parked.getPassengers().contains(bot.entity())) {
            riderBoat = null;
            clearSurfacePath();
            clearAssault();
            return false;
        }
        if (bot.distanceTo(target) < 7.0) {
            return false;
        }
        if (bot.pos().distanceTo(Vec3.of(parked.getLocation())) <= 2.8) {
            bot.lookAt(Vec3.of(parked.getLocation()).add(0, 0.5, 0));
            if (parked.addPassenger(bot.entity())) {
                clearAssault();
                actionCooldown = Math.max(actionCooldown, 6);
                return true;
            }
        }
        moveToward(Vec3.of(parked.getLocation()));
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

    private void forceDismount(Boat foreign) {
        foreign.removePassenger(bot.entity());
        passengerHost = null;
        reboardCooldown = Math.max(reboardCooldown, 12);
        if (bot.onGround()) {
            bot.motor().jump();
        }
    }

    private void cleanup(boolean breakBoat) {
        Boat tracked = riderBoatEntity();
        if (tracked != null) {
            if (tracked.getPassengers().contains(bot.entity())) {
                tracked.removePassenger(bot.entity());
            }
            if (breakBoat) {
                bot.motor().stopWalk();
                bot.lookAt(Vec3.of(tracked.getLocation()));
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

    // ------------------------------------------------------------------ consultas

    private Player findNearbyBoatingPlayer() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : bot.world().getPlayers()) {
            if (p.isDead() || !(p.getVehicle() instanceof Boat) || !bot.manager().isEligibleTarget(p)) {
                continue;
            }
            double d = bot.pos().distanceToSqr(Vec3.of(p.getLocation()));
            if (d <= SEARCH_RADIUS_SQ && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private Player primaryChaseTarget() {
        Player t = bot.target();
        if (t != null && !t.isDead()) {
            return t;
        }
        t = bot.fallbackTarget();
        return t != null && !t.isDead() ? t : null;
    }

    private boolean shouldDeferToVerticalChase(Player target) {
        if (!bot.settings().allowTowering.on() || !isOutOfWater(target)) {
            return false;
        }
        double yDiff = target.getY() - bot.pos().y();
        return yDiff >= 3.0 && bot.isWithinToweringRange(target, 3.0);
    }

    static boolean isOutOfWater(Player p) {
        if (p.getVehicle() instanceof Boat) {
            return false;
        }
        return !p.isInWater() && !p.isUnderWater() && !p.isInLava();
    }

    private Pos findBoatPlacementWater() {
        Pos origin = bot.blockPos();
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        World w = bot.world();
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

    private static boolean canPlaceBoat(World w, Pos water) {
        if (!isWaterSource(w, water)) {
            return false;
        }
        Block above = water.above().block(w);
        if (!above.getType().isAir() && !above.isReplaceable()) {
            return false;
        }
        return !WorldUtil.hasFluid(w, water.above()) && countSurfaceWater(w, water, 2) >= 5;
    }

    private static boolean isWaterSource(World w, Pos p) {
        Block b = p.block(w);
        return b.getType() == Material.WATER && b.getBlockData() instanceof Levelled l && l.getLevel() == 0;
    }

    /** Quantas fontes de água com ar em cima há num raio (na mesma altura). */
    static int countSurfaceWater(World w, Pos center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Pos water = new Pos(center.x() + dx, center.y(), center.z() + dz);
                if (!isWaterSource(w, water)) {
                    continue;
                }
                Block above = water.above().block(w);
                if ((above.getType().isAir() || above.isReplaceable()) && !WorldUtil.hasFluid(w, water.above())) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean shouldDismountNearShore(Boat boat) {
        World w = bot.world();
        Pos center = Pos.of(boat.getLocation());
        if (WorldUtil.isWater(w, center) || WorldUtil.isWater(w, center.below())) {
            return false;
        }
        return hasLandAround(w, center, 1);
    }

    private boolean isBoatCloseToLand(Boat boat) {
        return hasLandAround(bot.world(), Pos.of(boat.getLocation()), 0);
    }

    private static boolean hasLandAround(World w, Pos center, int maxDy) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= maxDy; dy++) {
                    Pos p = center.offset(dx, dy, dz);
                    Block b = p.block(w);
                    if (b.getType().isAir() || WorldUtil.hasFluid(w, p)) {
                        continue;
                    }
                    if (b.getType().isSolid() || WorldUtil.isSturdyTop(w, p)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
