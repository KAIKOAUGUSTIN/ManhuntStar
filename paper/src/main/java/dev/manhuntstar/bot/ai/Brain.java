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
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.pathfinding.AStar;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameMob;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.RayHit;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/**
 * The bot's brain (the mod's {@code runAI}): picks the target (player, hostile mob or bot from an enemy
 * group, weighted by {@code combat.target-priority}), decides between fleeing, riding, shooting, digging, building,
 * melee fighting or following the path, and handles pearls, dimensions and redistribution.
 */
public final class Brain {

    private static final double SEPARATION_RADIUS = 3.0;
    /** Only for tests on a server without players: the horse also chases enemy bots. */
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
    private GameEntity sneakyLookTarget;
    private int sneakyWindowTimer;
    private boolean sneakyChance;
    private int sneakyBehavior = -1;
    private Vec3 lastTargetPos;
    private String state = "none";
    private String stateTarget = "";
    private String stateDetail = "";

    public Brain(Bot bot) {
        this.bot = bot;
        this.navigator = new Navigator(bot);
        this.towering = new Towering(bot);
        this.mlg = new MlgClutch(bot);
        this.horse = new HorseRoutine(bot);
        this.boat = new BoatRoutine(bot);
        this.elytra = new ElytraFlight(bot);
    }

    /**
     * What the AI decided on the last tick (shown in /manhunt info): the language file's {@code state.<state>}
     * key, with the target ({@code <target>}) and a detail ({@code <detail>}).
     */
    public String state() {
        return state;
    }

    public String stateTarget() {
        return stateTarget;
    }

    public String stateDetail() {
        return stateDetail;
    }

    private void state(String key) {
        state(key, "", "");
    }

    private void state(String key, String target, String detail) {
        state = key;
        stateTarget = target;
        stateDetail = detail;
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

    /** Tower/bridge and A* blocks only within 3 blocks (horizontally) of the target, as in the mod. */
    public boolean canPlaceToweringBlocksNearTarget() {
        return settings().allowTowering.on() && bot.isWithinToweringRange(bot.target(), 3.0);
    }

    private boolean plannerEnabled() {
        return settings().pathMode.get().usesPlanner();
    }

    // ------------------------------------------------------------------ external events

    /** The bot took knockback: the AI stays idle for 10 ticks (the player has no control in the air either). */
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

    /** After a teleport the bot stays still for 15 ticks, with no velocity, until the ground "settles". */
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
            state("ability");
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
        horse.tickLance();
        boat.tickCooldowns();
        mlg.tick(navigator);
        if (!bot.isAlive()) {
            return;
        }
        if (knockbackTicks > 0) {
            knockbackTicks--;
            state("knockback");
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
        GameWorld w = bot.world();
        return WorldUtil.isLava(w, bot.blockPos()) || WorldUtil.isLava(w, bot.eyePos().toPos());
    }

    private double pearlMultiplier() {
        return bot.progression().pearlCooldownMultiplier() * settings().pearlCooldownMult.value();
    }

    private void runAI() {
        GamePlayer playerTarget = bot.manager().resolvePlayerTarget(bot);
        if (playerTarget != null) {
            bot.setTargetPlayer(playerTarget.uuid());
        }
        if (stabilizeTicks > 0) {
            stabilizeTicks--;
            state("stabilizing");
            bot.motor().stopWalk();
            bot.motor().setSprinting(false);
            if (!bot.isInWater() && bot.velocity().y() > 0) {
                bot.motor().setVelocity(bot.velocity().withY(0));
            }
            clearSwimStateIfDry();
            return;
        }
        if (bot.survival().isBusy()) {
            state(bot.survival().isEating() ? "eating" : "extinguishing");
            bot.motor().stopWalk();
            return;
        }
        if (escapeSuffocation()) {
            state("suffocating");
            return;
        }
        GamePlayer chosenTarget = playerTarget;
        if (playerTarget == null || playerTarget.isDead()) {
            playerTarget = bot.manager().isTrackingPlayers(bot) ? bot.fallbackTarget() : null;
        }
        IdleBehavior idle = bot.idle();
        if (idle != null && idle.tick()) {
            state(bot.manager().rings().isCounting(bot) ? "countdown" : "waiting-in-ring");
            return;
        }
        BotGroup group = bot.group();
        boolean escaping = group != null && group.role() == BotRole.ESCAPE;
        // in the ring the bot doesn't take off, and an escaper doesn't fly toward the target
        elytra.tick(escaping ? null : chosenTarget);
        if (playerTarget != null) {
            if (!playerTarget.world().equals(bot.world())) {
                state("target-in-other-dimension");
                dimensionStuckTimer++;
                if (dimensionStuckTimer > 20 && !teleporting) {
                    bot.manager().requestDimensionChange(playerTarget, bot.bukkitWorld());
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
                state("sneaky");
                return;
            }
            if (!escaping && bot.distanceTo(playerTarget) > 75.0 && pearlCooldown <= 0 && PearlAbility.canUsePearl(bot)) {
                handleOffensivePearl(playerTarget);
            }
        }

        GameLiving mobTarget = updateMobTarget();
        Bot enemyBot = updateEnemyBot();
        GameLiving enemyBotEntity = enemyBot == null ? null : enemyBot.entity();
        GameLiving active = chooseTarget(playerTarget, mobTarget, enemyBotEntity);
        if (active == null) {
            horse.handle(null);
            state("no-target");
            // without this the bot stood still holding the crossbow "loading" until it found another target
            bot.combat().cancelChargeWithoutTarget();
            if (bot.idle() == null) {
                bot.motor().stopWalk();
            }
            return;
        }
        String who = active instanceof GamePlayer pl ? pl.name() : bot.manager().byEntity(active) != null
                ? bot.manager().byEntity(active).name() : active.kind().name().toLowerCase(java.util.Locale.ROOT);
        if (escaping) {
            state("fleeing", who, "");
            flee(active, plannerEnabled());
            return;
        }
        if (boat.handle()) {
            state(boat.isCrossing() ? "boat-crossing" : "boat");
            return;
        }
        GameLiving horseTarget = playerTarget;
        if (horseTarget == null && HORSE_ANY_TARGET) {
            horseTarget = enemyBotEntity;
        }
        if (horse.handle(horseTarget)) {
            state(bot.isRiding() ? "riding" : "getting-horse", who, bot.isRiding() ? horse.detail() : "");
            return;
        }
        double dist = bot.distanceTo(active);
        boolean los = hasFullLineOfSight(active);
        if (active instanceof GamePlayer p && tryTriggerNonHitBowPressure(p, dist)) {
            state("bow-burst");
            return;
        }
        updateStrafe(dist);
        updateStuckCheck(dist, los);
        if (dist < 4.0 || los) {
            stuckMiningMode = false;
        }
        if (bot.combat().handleCrossbowCombat(active, dist) || bot.combat().handleTridentCombat(active, dist)
                || bot.combat().handleLavaBucketCombat(active, dist)) {
            state("shooting", who, "");
            return;
        }
        if (bot.health() <= 6f) {
            state("fleeing-low-health");
            bot.combat().stopBlock();
            flee(active, plannerEnabled());
            return;
        }
        if (active instanceof GamePlayer p && forceTunnelForLineOfSight(p, dist, los)) {
            state("tunneling");
            return;
        }
        if (towering.handleSpeedBridging(canPlaceToweringBlocksNearTarget())) {
            state("speed-bridging");
            return;
        }
        if (playerTarget != null) {
            bot.combat().handleRiskyHitBoost(playerTarget);
        }
        // In the water the eyes are at the height of the bank and it blocks the view: digging the bank opens a
        // channel of flowing water that traps the bot. It gets out by swimming and jumping (melee does that).
        boolean inFluid = bot.isInWater() || bot.isInLava();
        if (dist < 4.5 && !los && !inFluid && openPassageTo(active)) {
            state("breaking-through", who, "");
            return;
        }
        if (dist < 4.5) {
            state("fighting", who, "");
            melee(active, dist);
            return;
        }
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        if (stuckMiningMode && active instanceof GamePlayer p) {
            state("stuck-digging");
            bot.mining().executeHardcodedMining(p);
            if (bot.isInWater()) {
                bot.motor().setShouldBeSwimming(true);
                bot.setSwimmingPose(true);
                Vec3 flat = new Vec3(p.position().x(), bot.pos().y(), p.position().z());
                bot.motor().walk(flat.subtract(bot.pos()).normalize());
                if (p.position().y() > bot.pos().y() + 0.5) {
                    bot.motor().jump();
                }
            }
            return;
        }
        if (active instanceof GamePlayer sky && towering.handle(sky, navigator)) {
            state("towering", who, "");
            return;
        }
        if (bot.isInWater()) {
            if (active instanceof GamePlayer wp && handleWaterBuilding(wp)) {
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
        if (!usePlanner && active instanceof GamePlayer p0 && navigator.hasIndestructibleBlockInPath(eyes(p0))) {
            usePlanner = true;
            stuckMiningMode = false;
        }
        Vec3 targetLoc = active.position();
        boolean hasPath = false;
        if (usePlanner) {
            if (navigator.isParkouring()) {
                state("parkour");
                navigator.executeParkourTick();
                return;
            }
            Vec3 wp = navigator.waypoint(targetLoc);
            if (wp != null) {
                targetLoc = wp;
                // with no computed path the point is only a direction: walks around obstacles
                hasPath = navigator.onPath();
            }
            Pos crossing = navigator.boatCrossingGoal(false);
            if (crossing != null) {
                // the A* chose to cross the water by boat: boards when it reaches the edge
                boat.requestCrossing(crossing, active);
            }
        }
        state(hasPath ? "following-path" : "walking-straight", who, String.format(java.util.Locale.ROOT, "%.0f", dist));
        dumbStarMove(targetLoc, hasPath, active);
        if (!stuckMiningMode && bot.distanceTo(active) > 6.0) {
            bot.motor().addVelocity(swarmSeparation());
        }
    }

    /** Target close but behind a block: digs the block in the line of sight (as a player would). */
    private boolean openPassageTo(GameLiving target) {
        if (bot.mining().isMining()) {
            return true;
        }
        GameWorld w = bot.world();
        RayHit hit = WorldUtil.rayBlocks(w, bot.eyePos(), eyes(target), false);
        if (hit == null) {
            hit = WorldUtil.rayBlocks(w, bot.eyePos(), target.position().add(0, 0.5, 0), false);
        }
        if (hit == null || w.block(hit.block()).type().hardness() < 0) {
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAtBlock(hit.block());
        return bot.mining().startNoStop(hit.block());
    }

    /**
     * The game pushes a player out of a block they got stuck in; it doesn't do that for the Mannequin. If the
     * head or the feet are inside a solid block, the bot digs its way out (as the player would).
     */
    private boolean escapeSuffocation() {
        GameWorld w = bot.world();
        Pos feet = bot.blockPos();
        Pos head = bot.eyePos().toPos();
        for (Pos p : new Pos[]{head, feet}) {
            if (!WorldUtil.collides(w, p)) {
                continue;
            }
            GameBlock b = w.block(p);
            if (b.type().hardness() < 0 || !b.boundingBox().overlaps(bot.boundingBox().expand(-0.05))) {
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

    // ------------------------------------------------------------------ target choice

    private GameLiving updateMobTarget() {
        GameLiving m = bot.combat().mobAttacker();
        double priority = settings().targetPriority.value();
        if (priority < 5.0 && (m == null || m.isDead() || !m.isValid()) && bot.tickDelay(10)) {
            GameLiving closest = null;
            double closestDist = Double.MAX_VALUE;
            Location loc = bot.location();
            boolean end = bot.world().dimension() == Dimension.THE_END;
            BotGroup group = bot.group();
            Set<EntityKind> allowed = group == null ? null : group.allowedMobTargets();
            for (GameEntity nearby : bot.world().nearbyEntities(Locations.vec(loc), 30.0, 30.0, 30.0)) {
                if (!(nearby instanceof GameMob mob)) {
                    continue;
                }
                if (!mob.is(EntityTrait.ENEMY) || mob.isDead() || mob.is(EntityTrait.ENDER_DRAGON)) {
                    continue;
                }
                if (end && mob.is(EntityTrait.ENDERMAN)) {
                    continue;
                }
                if (allowed != null && !allowed.contains(mob.kind())) {
                    continue;
                }
                if (!bot.canSee(eyes(mob))) {
                    continue;
                }
                double d = bot.pos().distanceToSqr(mob.position());
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
        if ((m != null && m.is(EntityTrait.ENDER_DRAGON)) || (m != null && (m.isDead() || !m.isValid() || !m.world().equals(bot.world())))) {
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

    /** The mod's weights: player and enemy bot weigh (5 - priority), mob weighs (5 + priority); lowest score wins. */
    private GameLiving chooseTarget(GamePlayer player, GameLiving mob, GameLiving enemyBot) {
        double priority = settings().targetPriority.value();
        GameLiving active;
        if (priority >= 5.0) {
            active = player;
        } else if (priority <= -5.0) {
            active = mob;
        } else {
            double wPlayer = 5.0 + priority;
            double wMob = 5.0 - priority;
            GameLiving best = null;
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
        if (active != null && active.is(EntityTrait.ENDER_DRAGON)) {
            active = player;
        }
        return active;
    }

    // ------------------------------------------------------------------ fleeing

    private void flee(GameLiving threat, boolean usePlanner) {
        Vec3 threatPos = threat.position();
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

    // ------------------------------------------------------------------ melee

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

    private void melee(GameLiving target, double dist) {
        stuckMiningMode = false;
        bot.lookAt(target.position().add(0, target.eyeHeight() * 0.7, 0));
        boolean aggressive = bot.health() > 14f;
        bot.inventory().restorePreferredMainHand();
        long now = Bukkit.getCurrentTick();
        Vec3 forward = target.position().subtract(bot.pos()).horizontal().normalize();
        Vec3 left = new Vec3(-forward.z(), 0, forward.x()).normalize();
        Vec3 strafe = left.scale(strafeDirection * 0.25);
        // In the water walking doesn't climb the bank: swims to the target and jumps to get out (without this it got stuck at the edge).
        boolean inFluid = bot.isInWater() || bot.isInLava();
        // Target knocked off the ledge: the bot walked after it and fell too.
        boolean ledge = !inFluid && dropAhead(forward);
        if (!inFluid && dropAhead(strafe)) {
            strafe = Vec3.ZERO;
        }
        if (aggressive) {
            if (bot.combat().isBlocking()) {
                bot.combat().stopBlock();
            }
            if (inFluid) {
                bot.motor().setSprinting(true);
                swimToward(target.position(), target);
            } else if (dist > 2.5 && !ledge) {
                bot.motor().walk(forward.add(strafe));
                bot.motor().setSprinting(true);
            } else {
                if (ledge) {
                    holdAtLedge(strafe);
                } else {
                    bot.motor().walk(strafe);
                }
                bot.motor().setSprinting(false);
            }
            if (!ledge) {
                jumpForCritIfNeeded(target, dist);
            }
            boolean falling = bot.velocity().y() < 0 && !bot.onGround();
            if (falling || now - bot.combat().lastAttackTime() > 12) {
                bot.combat().attack(target);
            }
            return;
        }
        boolean canShield = bot.progression().canUseShield() && !bot.shouldHoldTotemInOffhand();
        if (canShield) {
            if (inFluid) {
                swimToward(target.position(), target);
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
        bot.motor().setSprinting(!ledge);
        if (inFluid) {
            swimToward(target.position(), target);
        } else if (ledge) {
            holdAtLedge(strafe);
        } else {
            bot.motor().walk(forward.add(strafe));
        }
        if (!ledge) {
            jumpForCritIfNeeded(target, dist);
        }
        if (now - bot.combat().lastAttackTime() > 10) {
            bot.combat().attack(target);
        }
    }

    /** On the ledge: kills the momentum ({@code walk} only adds acceleration to the current velocity) and at most walks sideways. */
    private void holdAtLedge(Vec3 strafe) {
        bot.motor().stopWalk();
        if (strafe.horizontal().lengthSqr() >= 1.0E-4) {
            bot.motor().walk(strafe);
        }
    }

    /**
     * Walking in this direction leads to a ledge with a fall bigger than the path accepts
     * ({@link AStar#MAX_DIG_FALL}), or into lava. Also applies in the air: the bot reached the ledge jumping and
     * the jump's momentum carried it over.
     */
    private boolean dropAhead(Vec3 dir) {
        if (dir.horizontal().lengthSqr() < 1.0E-4) {
            return false;
        }
        GameWorld w = bot.world();
        // already in the air over the gap (jumping a hole), stopping would only make it fall in
        if (!bot.onGround() && isDrop(w, bot.blockPos())) {
            return false;
        }
        return isDrop(w, bot.pos().add(dir.horizontal().normalize().scale(0.8)).toPos());
    }

    private static boolean isDrop(GameWorld w, Pos feet) {
        if (WorldUtil.collides(w, feet)) {
            return false;
        }
        for (int i = 1; i <= AStar.MAX_DIG_FALL + 1; i++) {
            Pos p = feet.below(i);
            if (WorldUtil.hasFluid(w, p)) {
                return WorldUtil.isLava(w, p);
            }
            if (WorldUtil.collides(w, p)) {
                return false;
            }
        }
        return true;
    }

    private void jumpForCritIfNeeded(GameLiving target, double dist) {
        if (bot.onGround() && (bot.horizontalCollision() || target.position().y() > bot.pos().y() + 0.5 || dist < 3.0)) {
            bot.motor().jump();
        }
    }

    // ------------------------------------------------------------------ bow pressure and tunnel

    private boolean tryTriggerNonHitBowPressure(GamePlayer target, double dist) {
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

    private boolean isLikelyToweringOrSkybase(GamePlayer target, double dist) {
        if (dist > 40.0) {
            return false;
        }
        double yDiff = target.position().y() - bot.pos().y();
        if (yDiff < 3.0) {
            return false;
        }
        Pos feet = target.blockPos();
        boolean playerSupport = bot.manager().blockTracker().isPlayerPlaced(PaperWorld.of(PaperWorld.bukkit(target.world())), feet.below());
        boolean rising = target.velocity().y() > 0.08;
        int surface = WorldUtil.surfaceYNoLeaves(PaperWorld.of(PaperWorld.bukkit(target.world())), feet.x(), feet.z());
        boolean high = target.position().y() - surface >= 6.0;
        return playerSupport || rising || high;
    }

    private boolean hasFullLineOfSight(GameLiving target) {
        if (target == null || target.isDead()) {
            return false;
        }
        Vec3 feet = target.position();
        Vec3 chest = feet.add(0, Math.max(0.7, target.height() * 0.5), 0);
        return bot.canSee(feet) && bot.canSee(chest) && bot.canSee(eyes(target));
    }

    private boolean forceTunnelForLineOfSight(GamePlayer target, double dist, boolean los) {
        if (los || bot.isRiding() || bot.isInWater() || bot.isInLava()) {
            return false;
        }
        double horizontal = Math.hypot(target.position().x() - bot.pos().x(), target.position().z() - bot.pos().z());
        double yDiff = target.position().y() - bot.pos().y();
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

    // ------------------------------------------------------------------ water

    private boolean handleWaterBuilding(GamePlayer target) {
        if (target.position().y() <= bot.pos().y() + 1.0) {
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

    private boolean isTargetOutOfWater(GameLiving target) {
        GameWorld w = PaperWorld.of(PaperWorld.bukkit(target.world()));
        Pos feet = target.blockPos();
        return !WorldUtil.isWater(w, feet) && !WorldUtil.isWater(w, feet.below()) && !target.isInWater();
    }

    /** Swims to the point; with the target out of the water, aims above the bank and jumps to climb it. */
    private void swimToward(Vec3 finalTarget, GameLiving lookTarget) {
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
        GameWorld w = bot.world();
        Pos ahead = Locations.relative(bot.blockPos(), bot.facing());
        Pos below = ahead.below();
        boolean clearFeet = !WorldUtil.hasCollision(w, ahead) && !WorldUtil.hasFluid(w, ahead);
        boolean clearHead = !WorldUtil.hasCollision(w, ahead.above()) && !WorldUtil.hasFluid(w, ahead.above());
        GameBlock b = w.block(below);
        boolean solidBelow = !b.type().is(BlockTrait.AIR) && !WorldUtil.hasFluid(w, below) && (b.type().is(BlockTrait.SOLID) || WorldUtil.isSturdyTop(w, below));
        return clearFeet && clearHead && solidBelow;
    }

    // ------------------------------------------------------------------ "Dumb*" movement

    /** Walks to the point avoiding obstacles, opening doors, jumping steps and swimming. */
    public void dumbStarMove(Vec3 finalTarget, boolean plannerActive, GameLiving lookTarget) {
        if (plannerActive) {
            // a detour from when there was no path no longer applies
            detourPos = null;
        } else if (detourPos != null) {
            if (bot.pos().distanceTo(detourPos) < 2.0 || --detourTimer <= 0) {
                detourPos = null;
            } else {
                finalTarget = detourPos;
            }
        }
        GameWorld w = bot.world();
        double distToFinal = bot.pos().distanceTo(finalTarget);
        if (bot.horizontalCollision()) {
            Pos front = Locations.relative(bot.blockPos(), bot.facing());
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
        // On a computed path it only checks up to the next node: the A* already avoided the lava, and looking past it
        // made the bot back away forever from a safe way down next to a lava lake.
        Vec3 lookAhead = moveDir.scale(plannerActive ? Math.min(1.5, toTarget.length()) : 1.5);
        Pos lookAheadBlock = bot.pos().add(lookAhead).toPos();
        boolean cliff = !WorldUtil.blocksMotion(w, lookAheadBlock.below());
        if (w.dimension() == Dimension.NETHER && cliff
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
        Pos ahead = Locations.relative(pos, bot.facing());
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
        GameWorld w = bot.world();
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
        GameWorld w = bot.world();
        if (!WorldUtil.isLoaded(w, pos)) {
            return true;
        }
        BlockType legs = w.block(pos).type();
        BlockType head = w.block(pos.above()).type();
        BlockType ground = w.block(pos.below()).type();
        if (legs == Blocks.LAVA || ground == Blocks.LAVA || legs == Blocks.FIRE || ground == Blocks.FIRE
                || legs == Blocks.POWDER_SNOW || ground == Blocks.POWDER_SNOW || legs == Blocks.SWEET_BERRY_BUSH) {
            return true;
        }
        if (!legs.is(BlockTrait.SOLID) && !head.is(BlockTrait.SOLID)) {
            return false;
        }
        if (w.block(pos).isPassable()) {
            // feet free but the head bumps (leaves, slab): the bot is almost 2 blocks tall and doesn't fit
            return head.is(BlockTrait.SOLID) && !w.block(pos.above()).isPassable();
        }
        if (legs.is(BlockTrait.DOOR) || legs.is(BlockTrait.FENCE_GATE)) {
            return false;
        }
        if (legs.is(BlockTrait.SOLID) && !head.is(BlockTrait.SOLID)) {
            return w.block(bot.blockPos().above(2)).type().is(BlockTrait.SOLID);
        }
        return legs.is(BlockTrait.SOLID) && head.is(BlockTrait.SOLID);
    }

    private boolean isLavaBelow(Vec3 from) {
        GameWorld w = bot.world();
        RayHit r = w.rayTraceBlocks(from, new Vec3(0, -1, 0), 100, true, true);
        return r != null && w.block(r.block()).type() == Blocks.LAVA;
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

    // ------------------------------------------------------------------ sneaky play

    private boolean handleSneakyPlay(GamePlayer player) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (--sneakyWindowTimer <= 0) {
            sneakyWindowTimer = 600;
            sneakyChance = r.nextBoolean();
        }
        Vec3 playerPos = player.position();
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
                List<GameEntity> near = bot.entity().nearbyEntities(12, 12, 12);
                if (!near.isEmpty()) {
                    sneakyLookTarget = near.get(r.nextInt(near.size()));
                }
            } else {
                sneakyIdleTarget = bot.pos().add((r.nextDouble() - 0.5) * 15, (r.nextDouble() - 0.5) * 5, (r.nextDouble() - 0.5) * 15);
                sneakyLookTarget = null;
            }
        }
        if (sneakyLookTarget != null && sneakyLookTarget.isValid()) {
            bot.lookAt(sneakyLookTarget instanceof GameLiving le ? eyes(le) : sneakyLookTarget.position());
        } else if (sneakyIdleTarget != null) {
            bot.lookAt(sneakyIdleTarget);
        }
        return true;
    }

    // ------------------------------------------------------------------ redistribution and pearls

    /** A bot that is too far (> redistribution distance) reappears hidden 50–100 blocks from the target. */
    private void handleAutoRedistribution(GamePlayer target) {
        if (!settings().allowRedistribution.on()) {
            return;
        }
        double horizontal = Math.hypot(bot.pos().x() - target.position().x(), bot.pos().z() - target.position().z());
        if (horizontal <= settings().redistDistance.value()) {
            return;
        }
        if (redistributionCooldown > 0) {
            redistributionCooldown--;
            return;
        }
        World w = PaperWorld.bukkit(target.world());
        GameWorld gw = PaperWorld.of(w);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 15; i++) {
            double distance = 50 + r.nextDouble() * 50;
            double angle = r.nextDouble() * Math.PI * 2;
            int x = (int) (target.position().x() + Math.cos(angle) * distance);
            int z = (int) (target.position().z() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Pos spawn = null;
            if (gw.dimension() == Dimension.NETHER) {
                for (int y = 5; y < 120; y++) {
                    Pos c = new Pos(x, y, z);
                    if (WorldUtil.isAir(gw, c) && WorldUtil.isAir(gw, c.above()) && WorldUtil.blocksMotion(gw, c.below()) && !WorldUtil.isLava(gw, c.below())) {
                        spawn = c;
                        break;
                    }
                }
            } else {
                Pos c = new Pos(x, WorldUtil.surfaceYNoLeaves(gw, x, z), z);
                if (!WorldUtil.isLava(gw, c.below())) {
                    spawn = c;
                }
            }
            if (spawn == null) {
                continue;
            }
            Vec3 spawnVec = Vec3.center(spawn);
            Vec3 eye = eyes(target);
            Vec3 look = target.direction();
            double dot = look.dot(spawnVec.subtract(eye).normalize());
            boolean obscured = !WorldUtil.clearLine(gw, eye, spawnVec);
            boolean behind = dot < -0.5;
            if (!obscured && !behind) {
                continue;
            }
            redistributionCooldown = 1200;
            Location dest = new Location(w, spawn.x() + 0.5, spawn.y() + 0.1, spawn.z() + 0.5, bot.yaw(), bot.pitch());
            bot.manager().teleportBot(bot, dest);
            bot.mining().cancel();
            stuckMiningMode = false;
            bot.lookAt(target.position());
            w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
            return;
        }
    }

    private Pos findSafeNetherSpot() {
        GameWorld w = bot.world();
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

    /** Throws a pearl to land 15–30 blocks from the target (only if that's closer than it is now). */
    private void handleOffensivePearl(GameLiving target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        GameWorld w = bot.world();
        double currentDist = bot.distanceTo(target);
        double angle = r.nextDouble() * Math.PI * 2;
        double fromPlayer = 15 + r.nextDouble() * 15;
        double tx = target.position().x() + Math.cos(angle) * fromPlayer;
        double tz = target.position().z() + Math.sin(angle) * fromPlayer;
        double ty = target.position().y();
        Vec3 landing = new Vec3(tx, ty, tz);
        if (landing.distanceTo(target.position()) >= currentDist) {
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

    static Vec3 eyes(GameLiving e) {
        return e.eyePosition();
    }
}
