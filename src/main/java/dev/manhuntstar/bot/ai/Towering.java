package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Torre (pilar sob o próprio pé) e ponte até um jogador em skybase/torre, e a ponte agachada
 * rápida no Nether. Só perto do alvo (até 3 blocos na horizontal), como no mod; quantos bots
 * constroem depende de quantos perseguem o mesmo jogador.
 */
final class Towering {

    private final Bot bot;
    private boolean assignedToTower;
    private boolean assignmentChecked;
    private int bridgePhase;
    private int bridgeTimer;
    private double lastProgressDist = Double.MAX_VALUE;
    private int lastPlaceTick = -200;

    private boolean speedBridging;
    private int speedStep;
    private int bridgedBlocks;
    private Pos speedTarget;

    Towering(Bot bot) {
        this.bot = bot;
    }

    private World world() {
        return bot.world();
    }

    private boolean solid(Pos p) {
        return WorldUtil.isTrulySolid(world(), p);
    }

    // ------------------------------------------------------------------ torre / ponte para skybase

    /** @return true se tomou o controle do movimento neste tick. */
    boolean handle(Player sky, Navigator nav) {
        Vec3 p = bot.pos();
        Vec3 t = Vec3.of(sky.getLocation());
        double yDiff = t.y() - p.y();
        double horizontal = Math.hypot(t.x() - p.x(), t.z() - p.z());
        double lookAngle = Math.toDegrees(Math.atan2(yDiff, horizontal));
        boolean close = bot.isWithinToweringRange(sky, 3.0);
        boolean forcePillar = close && shouldForceCloseVerticalPillar(sky, yDiff);
        boolean towerCandidate = close && yDiff > 0;
        boolean shouldTower = forcePillar || (towerCandidate && shouldUseTowering(sky, yDiff, lookAngle, nav));
        boolean needsBridge = close && shouldUseBridgeAssist(sky, yDiff, horizontal);
        if (!bot.settings().allowTowering.on() || (!shouldTower && !needsBridge) || bot.isInWater() || bot.isInLava()) {
            if (assignmentChecked || assignedToTower || bridgePhase != 0) {
                reset();
            }
            return false;
        }
        if (forcePillar) {
            assignmentChecked = true;
            assignedToTower = true;
        } else if (!assignmentChecked) {
            assignmentChecked = true;
            assignedToTower = ThreadLocalRandom.current().nextDouble() < towerProbability(sky);
        }
        if (!assignedToTower) {
            if (!shouldTower && !needsBridge) {
                reset();
            }
            return false;
        }
        bridgePhase = needsBridge ? 1 : 0;
        int now = Bukkit.getCurrentTick();
        double prev = lastProgressDist == Double.MAX_VALUE ? horizontal : lastProgressDist;
        boolean movingAway = horizontal > prev + 0.4;
        if (now - lastPlaceTick <= 30 && movingAway) {
            reset();
            return false;
        }
        if (bridgePhase == 0) {
            Pos placePos = bot.blockPos();
            bot.setSneaking(true);
            bot.lookAt(Vec3.center(placePos));
            if (!bot.hasPendingPillar() && bot.isLookingAtTarget(15f) && bot.onGround()) {
                bot.motor().jump();
                bot.queuePillarBlock(placePos, () -> {
                    lastPlaceTick = Bukkit.getCurrentTick();
                    bot.setSneaking(false);
                    if (sky.isValid()) {
                        bot.lookAt(Vec3.of(sky.getLocation()));
                    }
                });
            }
            bot.motor().stopWalk();
            lastProgressDist = horizontal;
            return true;
        }
        Vec3 dir = t.subtract(p).horizontal().normalize();
        bot.lookAt(t);
        Pos aheadBelow = p.add(dir.scale(1.2)).toPos().below();
        if (!solid(aheadBelow) && bot.onGround()) {
            bot.setSneaking(true);
            bot.motor().walk(dir.scale(-0.3));
            if (++bridgeTimer > 2) {
                if (WorldUtil.canBeReplaced(world(), aheadBelow) && bot.placeBlock(aheadBelow, Material.COBBLESTONE)) {
                    lastPlaceTick = now;
                }
                bridgeTimer = 0;
            }
        } else {
            bot.setSneaking(false);
            bot.motor().walk(dir.scale(0.5));
            bridgeTimer = 0;
        }
        lastProgressDist = horizontal;
        if (horizontal < 4.0 || (!needsBridge && !shouldTower)) {
            reset();
            return false;
        }
        return true;
    }

    /** Chance de este bot ser um dos que constroem, pelo número de bots atrás do mesmo jogador. */
    private double towerProbability(Player target) {
        int n = 0;
        for (Bot other : bot.manager().aliveBots()) {
            if (target.getUniqueId().equals(other.targetPlayerId())) {
                n++;
            }
        }
        n = Math.max(1, n);
        if (n == 1) {
            return 1.0;
        }
        if (n == 2) {
            return 0.75;
        }
        if (n == 3) {
            return 0.5;
        }
        if (n <= 10) {
            return 0.5 - (n - 3) * 0.043;
        }
        if (n <= 50) {
            return 0.2 - (n - 10) * 0.00125;
        }
        return Math.max(0.1, 0.15 - (n - 50) * 0.001);
    }

    void reset() {
        assignedToTower = false;
        assignmentChecked = false;
        bridgePhase = 0;
        bridgeTimer = 0;
        lastProgressDist = Double.MAX_VALUE;
        lastPlaceTick = -200;
        bot.setSneaking(false);
    }

    private boolean shouldUseTowering(Player target, double yDiff, double lookAngle, Navigator nav) {
        if (yDiff < 8.0 || target.getY() <= bot.pos().y() + 1.5) {
            return false;
        }
        if (lookAngle < 20.0 && yDiff < 12.0) {
            return false;
        }
        if (hasStairLikeRouteAhead(target) || hasAStarAscendingRoute(target, nav)) {
            return false;
        }
        Pos front = bot.blockPos().relative(bot.facing());
        if (solid(front) && !solid(front.above())) {
            return false;
        }
        return isDirectChaseUnsupported(target) || isTargetOnIsolatedSupport(target) || yDiff >= 12.0;
    }

    private boolean shouldForceCloseVerticalPillar(Player target, double yDiff) {
        if (yDiff < 3.0 || bot.isInWater() || bot.isInLava() || bot.isRiding()) {
            return false;
        }
        Vec3 p = bot.pos();
        return Math.abs(target.getX() - p.x()) <= 0.6 && Math.abs(target.getZ() - p.z()) <= 0.6;
    }

    private boolean shouldUseBridgeAssist(Player target, double yDiff, double horizontal) {
        if (yDiff < -2.0 || yDiff > 6.0 || horizontal <= 3.0) {
            return false;
        }
        if (hasStairLikeRouteAhead(target)) {
            return false;
        }
        return bridgePhase == 1 || isDirectChaseUnsupported(target);
    }

    private boolean hasStairLikeRouteAhead(Player target) {
        Vec3 flat = Vec3.of(target.getLocation()).subtract(bot.pos()).horizontal();
        if (flat.lengthSqr() < 1.0E-6) {
            return false;
        }
        flat = flat.normalize();
        int currentY = bot.blockPos().y();
        int rises = 0;
        for (int step = 1; step <= 8; step++) {
            Pos column = bot.pos().add(flat.scale(step)).toPos();
            int reachable = reachableFeetY(column, currentY);
            if (reachable == Integer.MIN_VALUE) {
                return false;
            }
            int rise = reachable - currentY;
            if (rise > 1) {
                return false;
            }
            if (rise > 0) {
                rises += rise;
            }
            currentY = reachable;
        }
        return rises >= 2;
    }

    private int reachableFeetY(Pos column, int currentY) {
        for (int y = currentY + 1; y >= currentY - 1; y--) {
            Pos feet = new Pos(column.x(), y, column.z());
            if (solid(feet.below()) && WorldUtil.hasBodySpace(world(), feet)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean hasAStarAscendingRoute(Player target, Navigator nav) {
        if (target.getY() <= bot.pos().y()) {
            return false;
        }
        List<Pos> path = nav.currentPath();
        if (path == null || path.size() < 2) {
            return false;
        }
        int idx = Math.max(0, nav.pathIndex());
        if (idx >= path.size()) {
            return false;
        }
        int botY = bot.blockPos().y();
        int required = Math.max(1, Math.min(6, target.getLocation().getBlockY() - botY));
        int max = Math.min(path.size() - 1, idx + 14);
        int highest = 0;
        for (int i = idx; i <= max; i++) {
            highest = Math.max(highest, path.get(i).y() - botY);
        }
        return highest >= required;
    }

    private boolean isDirectChaseUnsupported(Player target) {
        Vec3 flat = Vec3.of(target.getLocation()).subtract(bot.pos()).horizontal();
        if (flat.lengthSqr() < 1.0E-6) {
            return false;
        }
        Pos ahead = bot.pos().add(flat.normalize().scale(1.4)).toPos();
        boolean missingSupport = !solid(ahead.below());
        boolean wall = solid(ahead) && solid(ahead.above());
        return missingSupport || wall;
    }

    private boolean isTargetOnIsolatedSupport(Player target) {
        Pos support = Pos.of(target.getLocation()).below();
        if (!solid(support)) {
            return true;
        }
        int neighbors = 0;
        for (Pos n : new Pos[]{support.offset(0, 0, -1), support.offset(0, 0, 1), support.offset(1, 0, 0), support.offset(-1, 0, 0)}) {
            if (solid(n)) {
                neighbors++;
            }
        }
        return neighbors <= 1;
    }

    // ------------------------------------------------------------------ ponte agachada rápida (Nether)

    boolean handleSpeedBridging(boolean canPlace) {
        if (!canPlace || !bot.onGround() || bot.isInWater() || bot.isInLava() || world().getEnvironment() != World.Environment.NETHER) {
            resetBridge();
            return false;
        }
        Vec3 forward = bot.forwardVector();
        Pos oneAhead = bot.pos().add(forward.scale(1.1)).toPos();
        if (!speedBridging) {
            if (!WorldUtil.isAir(world(), oneAhead.below())) {
                return false;
            }
            for (int i = 1; i <= 3; i++) {
                if (!WorldUtil.isAir(world(), oneAhead.below(i))) {
                    return false;
                }
            }
            Pos below = oneAhead.below();
            boolean support = false;
            for (Pos n : new Pos[]{below.below(), below.offset(1, 0, 0), below.offset(-1, 0, 0), below.offset(0, 0, 1), below.offset(0, 0, -1)}) {
                if (!WorldUtil.isAir(world(), n)) {
                    support = true;
                    break;
                }
            }
            if (!support) {
                return false;
            }
        }
        speedBridging = true;
        switch (speedStep) {
            case 0 -> {
                bot.setSneaking(true);
                bot.motor().stopWalk();
                bot.motor().walk(forward.scale(-1.0));
                if (++bridgeTimer > 3) {
                    speedStep = 1;
                    bridgeTimer = 0;
                }
            }
            case 1 -> {
                bot.motor().stopWalk();
                Pos target = bot.pos().add(forward.scale(1.1)).toPos().below();
                bot.lookAt(Vec3.center(target));
                bot.setPitchNow(82f);
                speedTarget = target;
                speedStep = 2;
            }
            case 2 -> {
                if (speedTarget != null && WorldUtil.canBeReplaced(world(), speedTarget) && bot.placeBlock(speedTarget, Material.COBBLESTONE)) {
                    bridgedBlocks++;
                }
                speedStep = 3;
                bridgeTimer = 0;
            }
            default -> {
                Pos next = bot.pos().add(forward.scale(1.1)).toPos();
                if (WorldUtil.isAir(world(), next.below()) && bridgedBlocks < 15) {
                    bot.setSneaking(false);
                    bot.motor().walk(forward.scale(0.5));
                    if (++bridgeTimer > 5) {
                        speedStep = 0;
                        bridgeTimer = 0;
                    }
                } else {
                    resetBridge();
                }
            }
        }
        return true;
    }

    void resetBridge() {
        if (speedBridging) {
            speedBridging = false;
            speedStep = 0;
            bridgedBlocks = 0;
            speedTarget = null;
            bot.setSneaking(false);
        }
    }
}
