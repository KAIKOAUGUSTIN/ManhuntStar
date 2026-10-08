package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.bot.ability.PearlAbility;
import dev.manhuntstar.bot.mobility.BoatRoutine;
import dev.manhuntstar.bot.mobility.ElytraFlight;
import dev.manhuntstar.bot.mobility.HorseRoutine;
import dev.manhuntstar.bot.mobility.MlgClutch;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * O cérebro do bot (o {@code runAI} do mod): escolhe o alvo (jogador, mob hostil ou bot de grupo
 * inimigo, pelo peso de "prioridade-alvo"), decide entre fugir, montar, atirar, cavar, construir,
 * lutar corpo a corpo ou seguir o caminho, e cuida de pérolas, dimensões e redistribuição.
 */
public final class Brain {

    private static final double SEPARATION_RADIUS = 3.0;
    /** Só para testes em servidor sem jogador: o cavalo também persegue bots inimigos. */
    private static final boolean HORSE_ANY_TARGET = Boolean.getBoolean("manhuntstar.teste.cavalo");

    private final Bot bot;
    private final Navigator navigator;
    private final Towering towering;
    private final MlgClutch mlg;
    private final HorseRoutine horse;
    private final BoatRoutine boat;
    private final ElytraFlight elytra;

    private int knockbackTicks;
    private int lavaExposureTicks;
    private int pearlCooldown;
    private Bot cachedEnemyBot;
    private int dimensionStuckTimer;
    private boolean teleporting;
    private int stabilizeTicks;
    private int redistributionCooldown;
    private Vec3 detourPos;
    private int detourTimer;
    private boolean stuckMiningMode;
    private Vec3 lastStuckCheckPos = Vec3.ZERO;
    private long lastStuckCheckTick;
    private int strafeTimer;
    private int strafeDirection;
    private long lastSafetySneakTick;
    private int nonHitBowRollCooldown;

    private boolean sneakyMode;
    private int sneakyIdleTimer;
    private Vec3 sneakyIdleTarget;
    private Entity sneakyLookTarget;
    private int sneakyWindowTimer;
    private boolean sneakyChance;
    private int sneakyBehavior = -1;
    private Vec3 lastTargetPos;
    private String state = "-";

    public Brain(Bot bot) {
        this.bot = bot;
        this.navigator = new Navigator(bot);
        this.towering = new Towering(bot);
        this.mlg = new MlgClutch(bot);
        this.horse = new HorseRoutine(bot);
        this.boat = new BoatRoutine(bot);
        this.elytra = new ElytraFlight(bot);
    }

    /** O que a IA decidiu no último tick (mostrado no /bot info). */
    public String state() {
        return state;
    }

    public Navigator navigator() {
        return navigator;
    }

    public MlgClutch mlg() {
        return mlg;
    }

    public BoatRoutine boat() {
        return boat;
    }

    public HorseRoutine horse() {
        return horse;
    }

    private Settings settings() {
        return bot.settings();
    }

    /** Torre/ponte e blocos do A* só a até 3 blocos (horizontal) do alvo, como no mod. */
    public boolean canPlaceToweringBlocksNearTarget() {
        return settings().allowTowering.on() && bot.isWithinToweringRange(bot.target(), 3.0);
    }

    private boolean plannerEnabled() {
        return settings().pathMode.get().usesPlanner();
    }

    // ------------------------------------------------------------------ eventos externos

    /** O bot levou repulsão: a IA fica parada 10 ticks (o jogador também não controla no ar). */
    public void onKnockback() {
        knockbackTicks = 10;
        bot.mining().cancel();
    }

    public boolean isTeleporting() {
        return teleporting;
    }

    public void setTeleporting(boolean v) {
        teleporting = v;
        if (!v) {
            dimensionStuckTimer = 0;
        }
    }

    public void forceRedistributeWithDelay(int ticks) {
        redistributionCooldown = ticks;
    }

    /** Depois de um teleporte o bot fica 15 ticks parado, sem velocidade, até o chão "assentar". */
    public void startPostTeleportStabilization() {
        stabilizeTicks = Math.max(stabilizeTicks, 15);
        bot.motor().stopWalk();
        bot.motor().setSprinting(false);
        bot.motor().setVelocity(Vec3.ZERO);
        clearSwimStateIfDry();
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        bot.combat().tickTridentPickup();
        bot.combat().tickLavaCleanup();
        if (bot.capacityLocked()) {
            state = "capacidade especial";
            bot.manager().tactics().tickControlled(bot);
            return;
        }
        tickLavaPearl();
        if (pearlCooldown > 0) {
            pearlCooldown--;
        }
        if (nonHitBowRollCooldown > 0) {
            nonHitBowRollCooldown--;
        }
        horse.tickCooldowns();
        boat.tickCooldowns();
        mlg.tick(navigator);
        if (!bot.isAlive()) {
            return;
        }
        if (knockbackTicks > 0) {
            knockbackTicks--;
            state = "repulsão";
            return;
        }
        runAI();
    }

    private void tickLavaPearl() {
        if (bot.isInLava() && deepInLava()) {
            lavaExposureTicks++;
            if (pearlCooldown <= 0 && PearlAbility.canUsePearl(bot)) {
                Pos safe = findSafeNetherSpot();
                if (safe != null) {
                    PearlAbility.throwPearlArc(bot, Vec3.center(safe));
                    pearlCooldown = (int) (1200 * pearlMultiplier()) + ThreadLocalRandom.current().nextInt(201) - 100;
                }
            }
        } else if (lavaExposureTicks > 0) {
            lavaExposureTicks--;
        }
    }

    private boolean deepInLava() {
        World w = bot.world();
        return WorldUtil.isLava(w, bot.blockPos()) || WorldUtil.isLava(w, bot.eyePos().toPos());
    }

    private double pearlMultiplier() {
        return bot.progression().pearlCooldownMultiplier() * settings().pearlCooldownMult.value();
    }

    private void runAI() {
        Player playerTarget = bot.manager().resolvePlayerTarget(bot);
        if (playerTarget != null) {
            bot.setTargetPlayer(playerTarget.getUniqueId());
        }
        if (stabilizeTicks > 0) {
            stabilizeTicks--;
            state = "estabilizando";
            bot.motor().stopWalk();
            bot.motor().setSprinting(false);
            if (!bot.isInWater() && bot.velocity().y() > 0) {
                bot.motor().setVelocity(bot.velocity().withY(0));
            }
            clearSwimStateIfDry();
            return;
        }
        if (bot.survival().isBusy()) {
            state = bot.survival().isEating() ? "comendo" : "apagando fogo";
            bot.motor().stopWalk();
            return;
        }
        if (escapeSuffocation()) {
            state = "preso dentro de bloco";
            return;
        }
        Player chosenTarget = playerTarget;
        if (playerTarget == null || playerTarget.isDead()) {
            playerTarget = bot.manager().isTrackingPlayers(bot) ? bot.fallbackTarget() : null;
        }
        IdleBehavior idle = bot.idle();
        if (idle != null && idle.tick()) {
            state = bot.manager().countdown().isCounting() ? "contagem" : "esperando na roda";
            return;
        }
        BotGroup group = bot.group();
        boolean escaping = group != null && group.role() == BotRole.ESCAPE;
        // na roda o bot não decola, e fugitivo não voa na direção do alvo
        elytra.tick(escaping ? null : chosenTarget);
        if (playerTarget != null) {
            if (!playerTarget.getWorld().equals(bot.world())) {
                state = "alvo em outra dimensão";
                dimensionStuckTimer++;
                if (dimensionStuckTimer > 20 && !teleporting) {
                    bot.manager().requestDimensionChange(playerTarget, bot.world());
                    teleporting = true;
                }
                bot.motor().stopWalk();
                return;
            }
            dimensionStuckTimer = 0;
            teleporting = false;
            handleAutoRedistribution(playerTarget);
            if (!bot.isAlive()) {
                return;
            }
            if (handleSneakyPlay(playerTarget)) {
                state = "jogo furtivo";
                return;
            }
            if (!escaping && bot.distanceTo(playerTarget) > 75.0 && pearlCooldown <= 0 && PearlAbility.canUsePearl(bot)) {
                handleOffensivePearl(playerTarget);
            }
        }

        LivingEntity mobTarget = updateMobTarget();
        Bot enemyBot = updateEnemyBot();
        LivingEntity enemyBotEntity = enemyBot == null ? null : enemyBot.entity();
        LivingEntity active = chooseTarget(playerTarget, mobTarget, enemyBotEntity);
        if (active == null) {
            horse.handle(null);
            state = "sem alvo";
            if (bot.idle() == null) {
                bot.motor().stopWalk();
            }
            return;
        }
        String who = active instanceof Player pl ? pl.getName() : bot.manager().byEntity(active) != null
                ? bot.manager().byEntity(active).name() : active.getType().name().toLowerCase(java.util.Locale.ROOT);
        if (escaping) {
            state = "fugindo de " + who;
            flee(active, plannerEnabled());
            return;
        }
        if (boat.handle()) {
            state = boat.isCrossing() ? "atravessando de barco" : "barco";
            return;
        }
        LivingEntity horseTarget = playerTarget;
        if (horseTarget == null && HORSE_ANY_TARGET) {
            horseTarget = enemyBotEntity;
        }
        if (horse.handle(horseTarget)) {
            state = bot.isRiding() ? "cavalgando até " + who + " (" + horse.detail() + ")" : "indo pegar um cavalo";
            return;
        }
        double dist = bot.distanceTo(active);
        boolean los = hasFullLineOfSight(active);
        if (active instanceof Player p && tryTriggerNonHitBowPressure(p, dist)) {
            state = "rajada de arco";
            return;
        }
        updateStrafe(dist);
        updateStuckCheck(dist, los);
        if (dist < 4.0 || los) {
            stuckMiningMode = false;
        }
        if (bot.combat().handleCrossbowCombat(active, dist) || bot.combat().handleTridentCombat(active, dist)
                || bot.combat().handleLavaBucketCombat(active, dist)) {
            state = "atirando em " + who;
            return;
        }
        if (bot.health() <= 6f) {
            state = "fugindo (vida baixa)";
            bot.combat().stopBlock();
            flee(active, plannerEnabled());
            return;
        }
        if (active instanceof Player p && forceTunnelForLineOfSight(p, dist, los)) {
            state = "cavando túnel";
            return;
        }
        if (towering.handleSpeedBridging(canPlaceToweringBlocksNearTarget())) {
            state = "ponte rápida";
            return;
        }
        if (playerTarget != null) {
            bot.combat().handleRiskyHitBoost(playerTarget);
        }
        // Na água o olho fica na altura da margem e ela tapa a visão: cavar a margem abre um canal de água
        // corrente que prende o bot. Ele sai nadando e pulando (melee faz isso).
        boolean inFluid = bot.isInWater() || bot.isInLava();
        if (dist < 4.5 && !los && !inFluid && openPassageTo(active)) {
            state = "abrindo passagem até " + who;
            return;
        }
        if (dist < 4.5) {
            state = "lutando com " + who;
            melee(active, dist);
            return;
        }
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        if (stuckMiningMode && active instanceof Player p) {
            state = "preso, cavando";
            bot.mining().executeHardcodedMining(p);
            if (bot.isInWater()) {
                bot.motor().setShouldBeSwimming(true);
                bot.setSwimmingPose(true);
                Vec3 flat = new Vec3(p.getX(), bot.pos().y(), p.getZ());
                bot.motor().walk(flat.subtract(bot.pos()).normalize());
                if (p.getY() > bot.pos().y() + 0.5) {
                    bot.motor().jump();
                }
            }
            return;
        }
        if (active instanceof Player sky && towering.handle(sky, navigator)) {
            state = "torre/ponte até " + who;
            return;
        }
        if (bot.isInWater()) {
            if (active instanceof Player wp && handleWaterBuilding(wp)) {
                return;
            }
            boolean deep = WorldUtil.isWater(bot.world(), bot.blockPos().above());
            bot.motor().setShouldBeSwimming(deep);
            if (deep) {
                bot.motor().setSprinting(true);
            }
            bot.setSwimmingPose(deep);
        } else {
            bot.motor().setShouldBeSwimming(false);
            bot.setSwimmingPose(false);
        }
        boolean usePlanner = plannerEnabled();
        if (!usePlanner && active instanceof Player p0 && navigator.hasIndestructibleBlockInPath(eyes(p0))) {
            usePlanner = true;
            stuckMiningMode = false;
        }
        Vec3 targetLoc = Vec3.of(active.getLocation());
        boolean hasPath = false;
        if (usePlanner) {
            if (navigator.isParkouring()) {
                state = "parkour";
                navigator.executeParkourTick();
                return;
            }
            Vec3 wp = navigator.waypoint(targetLoc);
            if (wp != null) {
                targetLoc = wp;
                // sem caminho calculado o ponto é só a direção: anda desviando dos obstáculos
                hasPath = navigator.onPath();
            }
            Pos crossing = navigator.boatCrossingGoal(false);
            if (crossing != null) {
                // o A* preferiu atravessar a água de barco: embarca quando chegar na beira
                boat.requestCrossing(crossing, active);
            }
        }
        state = (hasPath ? "seguindo caminho até " : "indo direto até ") + who + String.format(java.util.Locale.ROOT, " (%.0f blocos)", dist);
        dumbStarMove(targetLoc, hasPath, active);
        if (!stuckMiningMode && bot.distanceTo(active) > 6.0) {
            bot.motor().addVelocity(swarmSeparation());
        }
    }

    /** Alvo perto mas atrás de um bloco: cava o bloco no caminho da visão (como um jogador faria). */
    private boolean openPassageTo(LivingEntity target) {
        if (bot.mining().isMining()) {
            return true;
        }
        World w = bot.world();
        RayTraceResult hit = WorldUtil.rayBlocks(w, bot.eyePos(), eyes(target), false);
        if (hit == null || hit.getHitBlock() == null) {
            hit = WorldUtil.rayBlocks(w, bot.eyePos(), Vec3.of(target.getLocation()).add(0, 0.5, 0), false);
        }
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlock().getType().getHardness() < 0) {
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAtBlock(Pos.of(hit.getHitBlock()));
        return bot.mining().startNoStop(Pos.of(hit.getHitBlock()));
    }

    /**
     * O jogo empurra o jogador para fora de um bloco em que ele ficou preso; o Mannequin não. Se a
     * cabeça ou os pés estiverem dentro de um bloco sólido, o bot cava para sair (como o jogador faria).
     */
    private boolean escapeSuffocation() {
        World w = bot.world();
        Pos feet = bot.blockPos();
        Pos head = bot.eyePos().toPos();
        for (Pos p : new Pos[]{head, feet}) {
            if (!WorldUtil.collides(w, p)) {
                continue;
            }
            Block b = p.block(w);
            if (b.getType().getHardness() < 0 || !b.getBoundingBox().overlaps(bot.boundingBox().clone().expand(-0.05))) {
                continue;
            }
            bot.motor().stopWalk();
            if (!bot.mining().isMining() || !p.equals(bot.mining().miningPos())) {
                bot.mining().startNoStop(p);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ escolha do alvo

    private LivingEntity updateMobTarget() {
        LivingEntity m = bot.combat().mobAttacker();
        double priority = settings().targetPriority.value();
        if (priority < 5.0 && (m == null || m.isDead() || !m.isValid()) && bot.tickDelay(10)) {
            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            Location loc = bot.location();
            boolean end = bot.world().getEnvironment() == World.Environment.THE_END;
            BotGroup group = bot.group();
            Set<EntityType> allowed = group == null ? null : group.allowedMobTargets();
            for (Mob mob : bot.world().getNearbyEntitiesByType(Mob.class, loc, 30.0)) {
                if (!(mob instanceof Enemy) || mob.isDead() || mob instanceof EnderDragon) {
                    continue;
                }
                if (end && mob instanceof Enderman) {
                    continue;
                }
                if (allowed != null && !allowed.contains(mob.getType())) {
                    continue;
                }
                if (!bot.canSee(eyes(mob))) {
                    continue;
                }
                double d = bot.pos().distanceToSqr(Vec3.of(mob.getLocation()));
                if (d < closestDist) {
                    closestDist = d;
                    closest = mob;
                }
            }
            if (closest != null) {
                bot.combat().setMobAttacker(closest);
                m = closest;
            }
        }
        if (m instanceof EnderDragon || (m != null && (m.isDead() || !m.isValid() || !m.getWorld().equals(bot.world())))) {
            m = null;
            bot.combat().setMobAttacker(null);
        }
        return m;
    }

    private Bot updateEnemyBot() {
        if (bot.tickDelay(10)) {
            Bot enemy = bot.manager().nearestEnemyBot(bot);
            cachedEnemyBot = enemy != null && bot.distanceTo(enemy.pos()) < 60.0 ? enemy : null;
        }
        if (cachedEnemyBot != null && !cachedEnemyBot.isAlive()) {
            cachedEnemyBot = null;
        }
        return cachedEnemyBot;
    }

    /** Pesos do mod: jogador e bot inimigo pesam (5 - prioridade), mob pesa (5 + prioridade); menor nota vence. */
    private LivingEntity chooseTarget(Player player, LivingEntity mob, LivingEntity enemyBot) {
        double priority = settings().targetPriority.value();
        LivingEntity active;
        if (priority >= 5.0) {
            active = player;
        } else if (priority <= -5.0) {
            active = mob;
        } else {
            double wPlayer = 5.0 + priority;
            double wMob = 5.0 - priority;
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            if (player != null) {
                double s = bot.distanceTo(player) * wMob;
                if (s < bestScore) {
                    bestScore = s;
                    best = player;
                }
            }
            if (enemyBot != null) {
                double s = bot.distanceTo(enemyBot) * wMob;
                if (s < bestScore) {
                    bestScore = s;
                    best = enemyBot;
                }
            }
            if (mob != null) {
                double s = bot.distanceTo(mob) * wPlayer;
                if (s < bestScore) {
                    best = mob;
                }
            }
            active = best;
        }
        if (active == null && enemyBot != null) {
            active = enemyBot;
        }
        if (active instanceof EnderDragon) {
            active = player;
        }
        return active;
    }

    // ------------------------------------------------------------------ fuga

    private void flee(LivingEntity threat, boolean usePlanner) {
        Vec3 threatPos = Vec3.of(threat.getLocation());
        if (usePlanner) {
            if (navigator.isParkouring()) {
                navigator.executeParkourTick();
                return;
            }
            Vec3 wp = navigator.escapeWaypoint(threatPos);
            if (wp != null) {
                dumbStarMove(wp, navigator.onPath(), threat);
                return;
            }
        }
        Vec3 away = bot.pos().subtract(threatPos).horizontal().normalize();
        if (bot.health() <= 6f) {
            away = away.rotateY((ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5).normalize();
        }
        bot.lookAt(bot.eyePos().add(away));
        bot.motor().setSprinting(true);
        bot.motor().walk(away);
        if (bot.onGround()) {
            bot.motor().jump();
        }
        if (bot.motor().shouldHandleGapAhead()) {
            bot.motor().handleGaps();
        } else {
            bot.motor().handleObstacles();
        }
    }

    // ------------------------------------------------------------------ corpo a corpo

    private void updateStrafe(double dist) {
        if (dist < 8.0) {
            if (--strafeTimer <= 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                strafeTimer = 10 + r.nextInt(10);
                strafeDirection = r.nextInt(3) - 1;
            }
        } else {
            strafeDirection = 0;
            strafeTimer = 10;
        }
    }

    private void updateStuckCheck(double dist, boolean los) {
        long now = Bukkit.getCurrentTick();
        if (now - lastStuckCheckTick > 40) {
            if (lastStuckCheckPos.distanceTo(bot.pos()) < 2.0 && dist > 5.0 && !los) {
                stuckMiningMode = true;
            }
            lastStuckCheckPos = bot.pos();
            lastStuckCheckTick = now;
        }
    }

    private void melee(LivingEntity target, double dist) {
        stuckMiningMode = false;
        bot.lookAt(Vec3.of(target.getLocation()).add(0, target.getEyeHeight() * 0.7, 0));
        boolean aggressive = bot.health() > 14f;
        bot.inventory().restorePreferredMainHand();
        long now = Bukkit.getCurrentTick();
        Vec3 forward = Vec3.of(target.getLocation()).subtract(bot.pos()).horizontal().normalize();
        Vec3 left = new Vec3(-forward.z(), 0, forward.x()).normalize();
        Vec3 strafe = left.scale(strafeDirection * 0.25);
        // Na água andar não sobe a margem: nada até o alvo e pula para sair (sem isso ficava preso na beira).
        boolean inFluid = bot.isInWater() || bot.isInLava();
        if (aggressive) {
            if (bot.combat().isBlocking()) {
                bot.combat().stopBlock();
            }
            if (inFluid) {
                bot.motor().setSprinting(true);
                swimToward(Vec3.of(target.getLocation()), target);
            } else if (dist > 2.5) {
                bot.motor().walk(forward.add(strafe));
                bot.motor().setSprinting(true);
            } else {
                bot.motor().walk(strafe);
                bot.motor().setSprinting(false);
            }
            jumpForCritIfNeeded(target, dist);
            boolean falling = bot.velocity().y() < 0 && !bot.onGround();
            if (falling || now - bot.combat().lastAttackTime() > 12) {
                bot.combat().attack(target);
            }
            return;
        }
        boolean canShield = bot.progression().canUseShield() && !bot.shouldHoldTotemInOffhand();
        if (canShield) {
            if (inFluid) {
                swimToward(Vec3.of(target.getLocation()), target);
            } else {
                bot.motor().stopWalk();
            }
            if (!bot.combat().hasUsableShield()) {
                bot.combat().setShield(true);
            }
            long cycle = now % 20;
            if (cycle < 12) {
                if (!bot.combat().isBlocking() && bot.combat().hasUsableShield()) {
                    bot.combat().startBlock();
                }
            } else if (cycle < 15) {
                if (bot.combat().isBlocking()) {
                    bot.combat().stopBlock();
                }
            } else {
                if (bot.combat().isBlocking()) {
                    bot.combat().stopBlock();
                }
                if (now - bot.combat().lastAttackTime() > 10) {
                    bot.combat().attack(target);
                }
            }
            return;
        }
        bot.motor().setSprinting(true);
        if (inFluid) {
            swimToward(Vec3.of(target.getLocation()), target);
        } else {
            bot.motor().walk(forward.add(strafe));
        }
        jumpForCritIfNeeded(target, dist);
        if (now - bot.combat().lastAttackTime() > 10) {
            bot.combat().attack(target);
        }
    }

    private void jumpForCritIfNeeded(LivingEntity target, double dist) {
        if (bot.onGround() && (bot.horizontalCollision() || target.getY() > bot.pos().y() + 0.5 || dist < 3.0)) {
            bot.motor().jump();
        }
    }

    // ------------------------------------------------------------------ pressão de arco e túnel

    private boolean tryTriggerNonHitBowPressure(Player target, double dist) {
        if (!settings().bowPressure.on() || target.isDead()) {
            return false;
        }
        long now = Bukkit.getCurrentTick();
        if (now - bot.combat().lastSuccessfulPlayerHitTick() < 600) {
            return false;
        }
        boolean toweringOrSkybase = isLikelyToweringOrSkybase(target, dist);
        if (!toweringOrSkybase && dist < 16.0) {
            return false;
        }
        if (nonHitBowRollCooldown > 0) {
            return false;
        }
        nonHitBowRollCooldown = 40;
        if (!toweringOrSkybase && ThreadLocalRandom.current().nextDouble() >= 0.5) {
            return false;
        }
        if (bot.manager().tactics().tryStartBowBurst(bot, target, now)) {
            nonHitBowRollCooldown = 80;
            return true;
        }
        return false;
    }

    private boolean isLikelyToweringOrSkybase(Player target, double dist) {
        if (dist > 40.0) {
            return false;
        }
        double yDiff = target.getY() - bot.pos().y();
        if (yDiff < 3.0) {
            return false;
        }
        Pos feet = Pos.of(target.getLocation());
        boolean playerSupport = bot.manager().blockTracker().isPlayerPlaced(target.getWorld(), feet.below());
        boolean rising = target.getVelocity().getY() > 0.08;
        int surface = WorldUtil.surfaceYNoLeaves(target.getWorld(), feet.x(), feet.z());
        boolean high = target.getY() - surface >= 6.0;
        return playerSupport || rising || high;
    }

    private boolean hasFullLineOfSight(LivingEntity target) {
        if (target == null || target.isDead()) {
            return false;
        }
        Vec3 feet = Vec3.of(target.getLocation());
        Vec3 chest = feet.add(0, Math.max(0.7, target.getHeight() * 0.5), 0);
        return bot.canSee(feet) && bot.canSee(chest) && bot.canSee(eyes(target));
    }

    private boolean forceTunnelForLineOfSight(Player target, double dist, boolean los) {
        if (los || bot.isRiding() || bot.isInWater() || bot.isInLava()) {
            return false;
        }
        double horizontal = Math.hypot(target.getX() - bot.pos().x(), target.getZ() - bot.pos().z());
        double yDiff = target.getY() - bot.pos().y();
        if (dist > 12.0 || (yDiff > 1.5 && horizontal > 5.0)) {
            stuckMiningMode = false;
            return false;
        }
        if (dist <= 2.75) {
            return false;
        }
        stuckMiningMode = true;
        bot.mining().executeHardcodedMining(target);
        return true;
    }

    // ------------------------------------------------------------------ água

    private boolean handleWaterBuilding(Player target) {
        if (target.getY() <= bot.pos().y() + 1.0) {
            return false;
        }
        Pos current = bot.blockPos();
        if (WorldUtil.hasFluid(bot.world(), current.above())) {
            bot.motor().addVelocity(new Vec3(0, 0.05, 0));
            return false;
        }
        if (!bot.canPlaceLeafAt(current)) {
            return false;
        }
        bot.lookAt(Vec3.center(current));
        bot.motor().setVelocity(new Vec3(0, 0.3, 0));
        bot.placeBlock(current, Material.OAK_LEAVES);
        bot.motor().setShouldBeSwimming(true);
        bot.setSwimmingPose(true);
        return true;
    }

    private void clearSwimStateIfDry() {
        if (!bot.isInWater() && !bot.isInLava() && !bot.isRiding()) {
            bot.motor().setShouldBeSwimming(false);
            bot.setSwimmingPose(false);
        }
    }

    private boolean isTargetOutOfWater(LivingEntity target) {
        World w = target.getWorld();
        Pos feet = Pos.of(target.getLocation());
        return !WorldUtil.isWater(w, feet) && !WorldUtil.isWater(w, feet.below()) && !target.isInWater();
    }

    /** Nada até o ponto; com o alvo fora da água, mira acima da margem e pula para subir nela. */
    private void swimToward(Vec3 finalTarget, LivingEntity lookTarget) {
        Vec3 swimTarget = finalTarget;
        if (bot.isInWater() && lookTarget != null && isTargetOutOfWater(lookTarget)) {
            swimTarget = new Vec3(finalTarget.x(), Math.max(finalTarget.y() + 1.0, bot.pos().y() + 0.35), finalTarget.z());
            if (bot.horizontalCollision() || hasShorelineExitAhead()) {
                bot.motor().jump();
            }
        }
        bot.motor().swimTowardNode(swimTarget);
        bot.motor().tickWaterBuoyancy();
    }

    private boolean hasShorelineExitAhead() {
        World w = bot.world();
        Pos ahead = bot.blockPos().relative(bot.facing());
        Pos below = ahead.below();
        boolean clearFeet = !WorldUtil.hasCollision(w, ahead) && !WorldUtil.hasFluid(w, ahead);
        boolean clearHead = !WorldUtil.hasCollision(w, ahead.above()) && !WorldUtil.hasFluid(w, ahead.above());
        Block b = below.block(w);
        boolean solidBelow = !b.getType().isAir() && !WorldUtil.hasFluid(w, below) && (b.getType().isSolid() || WorldUtil.isSturdyTop(w, below));
        return clearFeet && clearHead && solidBelow;
    }

    // ------------------------------------------------------------------ movimento "Dumb*"

    /** Anda até o ponto desviando de obstáculos, abrindo portas, pulando degraus e nadando. */
    public void dumbStarMove(Vec3 finalTarget, boolean plannerActive, LivingEntity lookTarget) {
        if (plannerActive) {
            // um desvio de quando não havia caminho não vale mais
            detourPos = null;
        } else if (detourPos != null) {
            if (bot.pos().distanceTo(detourPos) < 2.0 || --detourTimer <= 0) {
                detourPos = null;
            } else {
                finalTarget = detourPos;
            }
        }
        World w = bot.world();
        double distToFinal = bot.pos().distanceTo(finalTarget);
        if (bot.horizontalCollision()) {
            Pos front = bot.blockPos().relative(bot.facing());
            if (bot.tryOpen(front) || bot.tryOpen(bot.blockPos())) {
                return;
            }
        }
        if (!plannerActive && detourPos == null && bot.onGround()) {
            Vec3 dir = finalTarget.subtract(bot.pos()).normalize();
            boolean blocked = false;
            double scan = Math.min(distToFinal, 6.0);
            for (double d = 1.0; d <= scan; d += 1.0) {
                Pos check = bot.pos().add(dir.scale(d)).toPos();
                if (isPathBlocked(check)) {
                    blocked = true;
                    bot.tryOpen(check);
                    break;
                }
            }
            if (blocked) {
                findDetour(dir);
                if (detourPos != null) {
                    finalTarget = detourPos;
                }
            }
        }
        Vec3 toTarget = finalTarget.subtract(bot.pos()).horizontal();
        Vec3 moveDir = toTarget.normalize();
        // Num caminho calculado só confere até o próximo nó: o A* já desviou da lava, e olhar além dele
        // fazia o bot recuar para sempre de uma descida segura ao lado de um lago de lava.
        Vec3 lookAhead = moveDir.scale(plannerActive ? Math.min(1.5, toTarget.length()) : 1.5);
        Pos lookAheadBlock = bot.pos().add(lookAhead).toPos();
        boolean cliff = !WorldUtil.blocksMotion(w, lookAheadBlock.below());
        if (w.getEnvironment() == World.Environment.NETHER && cliff
                && (isLavaBelow(bot.pos().add(lookAhead)) || isLavaBelow(bot.eyePos().add(lookAhead)))) {
            lastSafetySneakTick = Bukkit.getCurrentTick();
            bot.setSneaking(true);
            bot.motor().stopWalk();
            bot.motor().walk(moveDir.scale(-0.2));
            return;
        }
        bot.motor().setSprinting(true);
        bot.setSneaking(false);
        boolean inFluid = bot.isInWater() || bot.isInLava();
        if (lookTarget != null && ThreadLocalRandom.current().nextDouble() < 0.02) {
            bot.lookAt(eyes(lookTarget));
        } else {
            bot.lookAt(finalTarget.add(0, inFluid ? 0.5 : 1.5, 0));
        }
        if (inFluid) {
            swimToward(finalTarget, lookTarget);
            return;
        }
        bot.motor().walk(moveDir);
        if (!bot.onGround()) {
            return;
        }
        Pos pos = bot.blockPos();
        Pos ahead = pos.relative(bot.facing());
        double horizontalToFinal = Math.hypot(finalTarget.x() - bot.pos().x(), finalTarget.z() - bot.pos().z());
        boolean targetAbove = finalTarget.y() > bot.pos().y() + 0.5;
        boolean farVertical = targetAbove && horizontalToFinal > 6.0;
        boolean ceiling = WorldUtil.collides(w, pos.above(2));
        if (targetAbove && !farVertical) {
            if (!ceiling) {
                bot.motor().jump();
            }
        } else if ((bot.horizontalCollision() || WorldUtil.blocksMotion(w, ahead)) && !WorldUtil.blocksMotion(w, ahead.above())) {
            if (!ceiling) {
                bot.motor().jump();
            }
        } else if (plannerActive && navigator.shouldSprintJump()) {
            bot.motor().jump();
        } else if (!plannerActive && Bukkit.getCurrentTick() - lastSafetySneakTick > 5 && !ceiling && !farVertical) {
            bot.motor().jump();
        }
    }

    private void findDetour(Vec3 dir) {
        int[] angles = {45, -45, 65, -65, 90, -90, 145, -145};
        double[] radii = {5.5, 8.5};
        World w = bot.world();
        for (double radius : radii) {
            for (int angle : angles) {
                Vec3 trial = dir.horizontal().rotateY(Math.toRadians(angle)).normalize().scale(radius);
                Vec3 candidate = bot.pos().add(trial);
                Pos tp = candidate.toPos();
                if (isPathBlocked(tp) || WorldUtil.isLava(w, tp.below())) {
                    continue;
                }
                detourPos = candidate;
                detourTimer = 40;
                return;
            }
        }
    }

    private boolean isPathBlocked(Pos pos) {
        World w = bot.world();
        if (!WorldUtil.isLoaded(w, pos)) {
            return true;
        }
        Material legs = pos.block(w).getType();
        Material head = pos.above().block(w).getType();
        Material ground = pos.below().block(w).getType();
        if (legs == Material.LAVA || ground == Material.LAVA || legs == Material.FIRE || ground == Material.FIRE
                || legs == Material.POWDER_SNOW || ground == Material.POWDER_SNOW || legs == Material.SWEET_BERRY_BUSH) {
            return true;
        }
        if (!legs.isSolid() && !head.isSolid()) {
            return false;
        }
        if (pos.block(w).isPassable()) {
            // pés livres mas a cabeça bate (folha, laje): o bot tem quase 2 blocos de altura e não passa
            return head.isSolid() && !pos.above().block(w).isPassable();
        }
        if (Tag.DOORS.isTagged(legs) || Tag.FENCE_GATES.isTagged(legs)) {
            return false;
        }
        if (legs.isSolid() && !head.isSolid()) {
            return bot.blockPos().above(2).block(w).getType().isSolid();
        }
        return legs.isSolid() && head.isSolid();
    }

    private boolean isLavaBelow(Vec3 from) {
        World w = bot.world();
        RayTraceResult r = w.rayTraceBlocks(from.toLocation(w), new Vector(0, -1, 0), 100, FluidCollisionMode.ALWAYS, true);
        return r != null && r.getHitBlock() != null && r.getHitBlock().getType() == Material.LAVA;
    }

    private Vec3 swarmSeparation() {
        Vec3 sep = Vec3.ZERO;
        Vec3 me = bot.pos();
        for (Bot other : bot.manager().aliveBots()) {
            if (other == bot || other.world() != bot.world()) {
                continue;
            }
            double d2 = other.pos().distanceToSqr(me);
            if (d2 >= SEPARATION_RADIUS * SEPARATION_RADIUS || d2 < 1.0E-6) {
                continue;
            }
            double strength = 1.0 - Math.sqrt(d2) / SEPARATION_RADIUS;
            sep = sep.add(me.subtract(other.pos()).normalize().scale(strength * 0.15));
        }
        return sep.horizontal();
    }

    // ------------------------------------------------------------------ jogo furtivo

    private boolean handleSneakyPlay(Player player) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (--sneakyWindowTimer <= 0) {
            sneakyWindowTimer = 600;
            sneakyChance = r.nextBoolean();
        }
        Vec3 playerPos = Vec3.of(player.getLocation());
        Vec3 playerVel = lastTargetPos == null ? Vec3.ZERO : playerPos.subtract(lastTargetPos);
        lastTargetPos = playerPos;
        if (!sneakyChance) {
            if (sneakyMode) {
                sneakyMode = false;
                bot.setSneaking(false);
            }
            return false;
        }
        boolean canSeePlayer = bot.canSee(eyes(player));
        Vec3 toBot = bot.pos().subtract(playerPos).normalize();
        boolean movingTowards = playerVel.dot(toBot) > 0.01;
        double dist = bot.distanceTo(player);
        if (movingTowards && !canSeePlayer && dist < 35.0) {
            if (!sneakyMode) {
                if (r.nextDouble() < 0.5) {
                    sneakyMode = true;
                    bot.setSneaking(true);
                    double roll = r.nextDouble();
                    sneakyBehavior = roll < 0.5 ? 0 : roll < 0.75 ? 1 : 2;
                } else {
                    sneakyChance = false;
                }
            }
        } else if (canSeePlayer || dist > 45.0) {
            if (sneakyMode) {
                sneakyMode = false;
                sneakyBehavior = -1;
                bot.setSneaking(false);
            }
            return false;
        }
        if (!sneakyMode) {
            return false;
        }
        switch (sneakyBehavior) {
            case 0 -> bot.motor().walk(playerPos.subtract(bot.pos()).normalize().scale(0.5));
            case 1 -> {
                if (sneakyIdleTarget == null || bot.pos().distanceTo(sneakyIdleTarget) < 1.5) {
                    sneakyIdleTarget = bot.pos().add((r.nextDouble() - 0.5) * 10, 0, (r.nextDouble() - 0.5) * 10);
                }
                bot.motor().walk(sneakyIdleTarget.subtract(bot.pos()).normalize().scale(0.5));
            }
            default -> bot.motor().stopWalk();
        }
        if (--sneakyIdleTimer <= 0) {
            sneakyIdleTimer = 50 + r.nextInt(70);
            if (r.nextDouble() < 0.4) {
                List<Entity> near = bot.entity().getNearbyEntities(12, 12, 12);
                if (!near.isEmpty()) {
                    sneakyLookTarget = near.get(r.nextInt(near.size()));
                }
            } else {
                sneakyIdleTarget = bot.pos().add((r.nextDouble() - 0.5) * 15, (r.nextDouble() - 0.5) * 5, (r.nextDouble() - 0.5) * 15);
                sneakyLookTarget = null;
            }
        }
        if (sneakyLookTarget != null && sneakyLookTarget.isValid()) {
            bot.lookAt(sneakyLookTarget instanceof LivingEntity le ? eyes(le) : Vec3.of(sneakyLookTarget.getLocation()));
        } else if (sneakyIdleTarget != null) {
            bot.lookAt(sneakyIdleTarget);
        }
        return true;
    }

    // ------------------------------------------------------------------ redistribuição e pérolas

    /** Bot longe demais (> distância de redistribuição) reaparece escondido a 50–100 blocos do alvo. */
    private void handleAutoRedistribution(Player target) {
        if (!settings().allowRedistribution.on()) {
            return;
        }
        double horizontal = Math.hypot(bot.pos().x() - target.getX(), bot.pos().z() - target.getZ());
        if (horizontal <= settings().redistDistance.value()) {
            return;
        }
        if (redistributionCooldown > 0) {
            redistributionCooldown--;
            return;
        }
        World w = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 15; i++) {
            double distance = 50 + r.nextDouble() * 50;
            double angle = r.nextDouble() * Math.PI * 2;
            int x = (int) (target.getX() + Math.cos(angle) * distance);
            int z = (int) (target.getZ() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Pos spawn = null;
            if (w.getEnvironment() == World.Environment.NETHER) {
                for (int y = 5; y < 120; y++) {
                    Pos c = new Pos(x, y, z);
                    if (WorldUtil.isAir(w, c) && WorldUtil.isAir(w, c.above()) && WorldUtil.blocksMotion(w, c.below()) && !WorldUtil.isLava(w, c.below())) {
                        spawn = c;
                        break;
                    }
                }
            } else {
                Pos c = new Pos(x, WorldUtil.surfaceYNoLeaves(w, x, z), z);
                if (!WorldUtil.isLava(w, c.below())) {
                    spawn = c;
                }
            }
            if (spawn == null) {
                continue;
            }
            Vec3 spawnVec = Vec3.center(spawn);
            Vec3 eye = eyes(target);
            Vec3 look = Vec3.of(target.getLocation().getDirection());
            double dot = look.dot(spawnVec.subtract(eye).normalize());
            boolean obscured = !WorldUtil.clearLine(w, eye, spawnVec);
            boolean behind = dot < -0.5;
            if (!obscured && !behind) {
                continue;
            }
            redistributionCooldown = 1200;
            Location dest = new Location(w, spawn.x() + 0.5, spawn.y() + 0.1, spawn.z() + 0.5, bot.yaw(), bot.pitch());
            bot.manager().teleportBot(bot, dest);
            bot.mining().cancel();
            stuckMiningMode = false;
            bot.lookAt(Vec3.of(target.getLocation()));
            w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
            return;
        }
    }

    private Pos findSafeNetherSpot() {
        World w = bot.world();
        Pos base = bot.blockPos();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 50; i++) {
            int x = base.x() + r.nextInt(60) - 30;
            int z = base.z() + r.nextInt(60) - 30;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = -10; dy < 20; dy++) {
                Pos c = new Pos(x, base.y() + dy, z);
                if (WorldUtil.isAir(w, c) && WorldUtil.isAir(w, c.above()) && WorldUtil.blocksMotion(w, c.below()) && !WorldUtil.isLava(w, c.below())) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Joga uma pérola para cair a 15–30 blocos do alvo (só se ficar mais perto do que está). */
    private void handleOffensivePearl(LivingEntity target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = bot.world();
        double currentDist = bot.distanceTo(target);
        double angle = r.nextDouble() * Math.PI * 2;
        double fromPlayer = 15 + r.nextDouble() * 15;
        double tx = target.getX() + Math.cos(angle) * fromPlayer;
        double tz = target.getZ() + Math.sin(angle) * fromPlayer;
        double ty = target.getY();
        Vec3 landing = new Vec3(tx, ty, tz);
        if (landing.distanceTo(Vec3.of(target.getLocation())) >= currentDist) {
            return;
        }
        Pos base = new Pos((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz));
        if (!w.isChunkLoaded(base.x() >> 4, base.z() >> 4)) {
            return;
        }
        if (WorldUtil.blocksMotion(w, base)) {
            boolean found = false;
            for (int i = 1; i <= 5; i++) {
                if (WorldUtil.isAir(w, base.above(i))) {
                    ty += i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
        }
        landing = new Vec3(tx, ty, tz);
        if (WorldUtil.clearLine(w, bot.eyePos(), landing)) {
            PearlAbility.throwPearlArc(bot, landing);
            setOffensivePearlCooldown();
            return;
        }
        Vec3 high = landing.add(0, 10, 0);
        if (WorldUtil.clearLine(w, bot.eyePos(), high)) {
            PearlAbility.throwPearlArc(bot, high);
            setOffensivePearlCooldown();
        }
    }

    private void setOffensivePearlCooldown() {
        pearlCooldown = (int) (300 * pearlMultiplier()) + ThreadLocalRandom.current().nextInt(201) - 100;
    }

    // ------------------------------------------------------------------ util

    static Vec3 eyes(LivingEntity e) {
        return Vec3.of(e.getEyeLocation());
    }
}
