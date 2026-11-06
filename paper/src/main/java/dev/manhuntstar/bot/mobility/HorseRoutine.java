package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.GearScoring;
import dev.manhuntstar.bot.Progression;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Particle;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Vec3;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Horse (phase 4+): with the target more than 32 blocks away, looks for an adult horse within 40 blocks,
 * mounts, tames it (forced after 3 s, as in the mod), saddles it and rides to the target. Gets off 4 blocks
 * from the target and only mounts again after 60 s. A horse stuck for 3 s is marked unreachable for 30 s.
 *
 * <p>With the horseback spear on ({@code combat.mounted-spear}) it doesn't get off: it charges the target
 * with the phase's spear, rides past, keeps going straight, turns around and charges again (see {@link #joust}).
 *
 * <p>In the game only a player steers the horse. With the bot mounted, the horse's AI is turned off
 * ({@code setAware(false)}, see {@code BotManager#takeHorseControl}) and the plugin applies the rider's
 * input as the game does: the horse turns where the rider looks, speeds up with its attribute speed,
 * jumps with its jump strength (with the forward boost) and floats on water.
 */
public final class HorseRoutine {

    private static final double SEARCH_RADIUS = 40.0;
    private static final double INTERACT_RANGE = 2.8;
    private static final double MOUNT_START_DISTANCE = 32.0;
    private static final double DISMOUNT_DISTANCE = 4.0;
    private static final int TAME_TICKS = 60;
    private static final float MAX_TURN_PER_TICK = 30f;
    private static final double MAX_HORIZONTAL_SPEED = 1.5;
    /**
     * Maximum detour (going to the horse and from it to the target, minus going straight) to fetch a horse. The
     * horse gains ~0.075 s per block over sprinting; a 16-block detour (~3 s) plus taming (3 s) already breaks
     * even with the gain on a ~100-block chase.
     */
    private static final double MAX_DETOUR = 16.0;

    /** Raises the spear when there are at most (spear delay + this) ticks left to reach the target. */
    private static final int LANCE_RAISE_MARGIN = 6;
    /** Ticks going straight after passing the target, to gain distance before turning back (~9 blocks at a gallop). */
    private static final int LANCE_RUNOUT_TICKS = 14;
    /** Passes in a row without a hit before dropping the spear and getting off to fight on foot. */
    private static final int LANCE_MAX_MISSES = 3;
    /** Spear raised for longer than this without passing the target (it moved aside): lowers it and counts as a miss. */
    private static final int LANCE_MAX_RAISED_TICKS = 100;
    /** Target higher or lower than this (on top of a pillar, in a hole): gets off and fights on foot. */
    private static final double LANCE_MAX_DY = 2.5;
    /** Charge delay of the game's spears, in case the item doesn't carry the component. */
    private static final int DEFAULT_LANCE_DELAY = 12;

    /** Only for server tests ({@code -Dmanhuntstar.teste.cavalo=true}): more detail in /manhunt info. */
    private static final boolean TEST_DETAIL = Boolean.getBoolean("manhuntstar.teste.cavalo");

    private final Bot bot;
    private int actionCooldown;
    private int knockbackGrace;
    private int noMoveTicks;
    private int jumpCooldown;
    private UUID targetHorse;
    private int lockTicks;
    private UUID untamedHorse;
    private int untamedTicks;
    private Vec3 lastSetVelocity;
    private String detail = "";
    /** Ticks with the spear raised (0 = lowered). */
    private int lanceRaisedTicks;
    private boolean lanceHit;
    private int lanceMisses;
    private int runoutTicks;
    private double runoutX;
    private double runoutZ;
    private ItemStack lance = ItemStack.empty();
    private int lancePhase = -1;
    private int lanceDelay = DEFAULT_LANCE_DELAY;

    public HorseRoutine(Bot bot) {
        this.bot = bot;
    }

    /** Detail for /manhunt info (horse speed, distance to the next point, taming). */
    public String detail() {
        return detail;
    }

    public void tickCooldowns() {
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (knockbackGrace > 0) {
            knockbackGrace--;
        }
        if (lockTicks > 0) {
            lockTicks--;
        }
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
    }

    /** No spear on foot: if the bot got off (or was knocked off) the horse holding it, it goes back to the sword. */
    public void tickLance() {
        if (!bot.isAlive() || (bot.vehicle() instanceof GameHorse ridden && ridden.isHorse())) {
            return;
        }
        if (lanceRaisedTicks > 0 || runoutTicks > 0 || GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            stowLance();
            resetJoust();
        }
    }

    /** This bot's spear charge hit someone (see BotEntityListener). */
    public void onLanceHit() {
        if (lanceRaisedTicks > 0) {
            lanceHit = true;
        }
    }

    /** @return true if the horse controlled the bot this tick. */
    public boolean handle(GameLiving target) {
        GameEntity vehicle = bot.vehicle();
        if (bot.isHorseRidingBlocked()) {
            if (vehicle instanceof GameHorse h && h.isHorse()) {
                dismount(h);
            }
            resetTarget();
            return false;
        }
        if (!bot.settings().allowHorse.on() || bot.progression().phase() < 4 || target == null || target.isDead()) {
            if (vehicle instanceof GameHorse h && h.isHorse()) {
                // no target to chase: gets off (mounted the bot neither walks nor fights properly)
                dismount(h);
                actionCooldown = Math.max(actionCooldown, 10);
            }
            return false;
        }
        if (vehicle instanceof GameHorse mounted && mounted.isHorse()) {
            return handleMounted(mounted, target);
        }
        Vec3 p = bot.pos();
        if (Math.hypot(p.x() - target.position().x(), p.z() - target.position().z()) <= MOUNT_START_DISTANCE) {
            resetTarget();
            return false;
        }
        GameHorse horse = findCandidate(target);
        if (horse == null) {
            resetTarget();
            return false;
        }
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        double dist = bot.distanceTo(horse);
        if (dist > INTERACT_RANGE) {
            moveToward(horse.position(), horse);
            return true;
        }
        bot.motor().stopWalk();
        bot.lookAt(horse.eyePosition());
        if (!horse.isTamed()) {
            if (actionCooldown <= 0) {
                mount(horse);
            }
            return true;
        }
        if (!hasSaddle(horse)) {
            trySaddle(horse);
        }
        if (!bot.isRiding() && actionCooldown <= 0) {
            mount(horse);
        }
        return true;
    }

    private boolean handleMounted(GameHorse horse, GameLiving target) {
        boolean lanceOn = useLance(horse, target);
        if (!lanceOn && bot.distanceTo(target) <= DISMOUNT_DISTANCE) {
            dismount(horse);
            bot.blockHorseRidingForSeconds(60);
            resetTarget();
            actionCooldown = Math.max(actionCooldown, 10);
            return false;
        }
        targetHorse = horse.uuid();
        lockTicks = 120;
        bot.manager().takeHorseControl(bot, horse);
        bot.motor().stopWalk();
        if (!horse.isTamed()) {
            tameTick(horse);
            return true;
        }
        if (!hasSaddle(horse)) {
            dismount(horse);
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.SADDLE));
            actionCooldown = Math.max(actionCooldown, 10);
            return true;
        }
        if (lanceOn) {
            joust(horse, target);
        } else {
            stowLance();
            steer(horse, target.position(), true);
        }
        if (noMoveTicks >= 60) {
            bot.manager().markHorseInaccessible(horse, 600);
            dismount(horse);
            resetTarget();
            actionCooldown = Math.max(actionCooldown, 10);
            return false;
        }
        return true;
    }

    /** Skittish horse: rears and bucks as it does with a player; tamed by force after 3 s (mod). */
    private void tameTick(GameHorse horse) {
        if (!horse.uuid().equals(untamedHorse)) {
            untamedHorse = horse.uuid();
            untamedTicks = 0;
        }
        untamedTicks++;
        detail = "domando " + (untamedTicks * 100 / TAME_TICKS) + "%";
        if (untamedTicks >= TAME_TICKS) {
            horse.setRearing(false);
            horse.setDomestication(horse.maxDomestication());
            horse.setTamed(true);
            horse.world().spawnParticle(Particle.HEART, horse.position().add(0, 1.5, 0), 7, 0.5, 0.5, 0.5, 1.0);
            untamedHorse = null;
            untamedTicks = 0;
            actionCooldown = Math.max(actionCooldown, 10);
            return;
        }
        int phase = untamedTicks % 20;
        if (phase == 1) {
            horse.setRearing(true);
            horse.world().playSound(horse.position(), Sounds.ENTITY_HORSE_ANGRY, dev.manhuntstar.platform.SoundCategory.NEUTRAL, 1f, 1f);
        } else if (phase == 12) {
            horse.setRearing(false);
            float yaw = horse.yaw() + (float) ThreadLocalRandom.current().nextDouble(-35, 35);
            horse.setRotation(yaw, 0);
            horse.setBodyYaw(yaw);
        }
    }

    private static boolean hasSaddle(GameHorse horse) {
        ItemStack saddle = PaperEntities.bukkit(horse).getInventory().getSaddle();
        return saddle != null && saddle.getType() == Material.SADDLE;
    }

    private void mount(GameHorse horse) {
        if (horse.addPassenger(bot.entity())) {
            horse.world().playSound(horse.position(), Sounds.ENTITY_HORSE_SADDLE, dev.manhuntstar.platform.SoundCategory.NEUTRAL, 0.5f, 1f);
        }
        noMoveTicks = 0;
        lastSetVelocity = null;
        actionCooldown = 10;
    }

    private void dismount(GameHorse horse) {
        horse.removePassenger(bot.entity());
        bot.manager().releaseHorse(horse);
        bot.motor().stopWalk();
        noMoveTicks = 0;
        lastSetVelocity = null;
        stowLance();
        resetJoust();
    }

    private void trySaddle(GameHorse horse) {
        if (actionCooldown > 0) {
            return;
        }
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.SADDLE));
        bot.swing();
        PaperEntities.bukkit(horse).getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.world().playSound(horse.position(), Sounds.ENTITY_HORSE_SADDLE, dev.manhuntstar.platform.SoundCategory.NEUTRAL, 0.5f, 1f);
        actionCooldown = 8;
        Bukkit.getScheduler().runTaskLater(bot.manager().plugin(), () -> {
            if (bot.isAlive()) {
                bot.inventory().restorePreferredMainHand();
            }
        }, 2L);
    }

    /**
     * Rider input applied to the horse, as the game does with a mounted player. Without
     * {@code usePlanner} it goes straight to the point (the run after passing the target in the spear charge).
     */
    private void steer(GameHorse horse, Vec3 chaseTarget, boolean usePlanner) {
        Navigator nav = bot.brain().navigator();
        Vec3 navTarget = chaseTarget;
        if (usePlanner && bot.settings().pathMode.get().usesPlanner()) {
            Vec3 wp = nav.waypoint(chaseTarget);
            if (wp != null) {
                navTarget = wp;
            }
        }
        horse.setEatingGrass(false);
        horse.setEating(false);
        Location hl = PaperEntities.bukkit(horse).getLocation();
        Vec3 current = horse.velocity();
        // chunk without physics this tick: don't add again (the velocity would build up and launch the horse)
        boolean stalled = lastSetVelocity != null && sameVelocity(current, lastSetVelocity)
                && lastSetVelocity.x() * lastSetVelocity.x() + lastSetVelocity.z() * lastSetVelocity.z() > 1.0E-8;
        Vec3 to = new Vec3(navTarget.x() - hl.getX(), 0, navTarget.z() - hl.getZ());
        detail = String.format(java.util.Locale.ROOT, "horse at %.1f blocks/s, next point at %.1f (dy %.1f)%s%s%s",
                Math.hypot(current.x(), current.z()) * 20, to.length(), navTarget.y() - hl.getY(),
                noMoveTicks > 0 ? ", stuck " + noMoveTicks : "", stalled ? ", no physics" : "",
                bot.mining().isMining() ? ", digging" : "")
                + (TEST_DETAIL ? " ticks=" + horse.ticksLived() + " chunk=" + hl.getChunk().getLoadLevel() + " ai=" + horse.isAware() : "");
        if (stalled) {
            noMoveTicks++; // counts as stuck: after 3 s the bot gets off and goes on foot
            return;
        }
        if (to.lengthSqr() < 0.09) {
            noMoveTicks = 0;
            return;
        }
        bot.lookAt(navTarget.add(0, 1.2, 0));
        if (horse.noDamageTicks() > 10) {
            knockbackGrace = 12;
        }
        if (knockbackGrace > 0) {
            noMoveTicks = 0;
            lastSetVelocity = null;
            return;
        }
        horse.setRearing(false);

        float delta = Bot.wrapDegrees(to.normalize().yaw() - hl.getYaw());
        float yaw = hl.getYaw() + Math.max(-MAX_TURN_PER_TICK, Math.min(MAX_TURN_PER_TICK, delta));
        horse.setRotation(yaw, 0);
        horse.setBodyYaw(yaw);
        double forward = Math.cos(Math.toRadians(delta)) <= -0.35 ? 0.45 : 1.0;

        double horizontalSpeed = Math.hypot(current.x(), current.z());
        noMoveTicks = horizontalSpeed < 0.05 ? noMoveTicks + 1 : 0;

        boolean onGround = horse.isOnGround();
        boolean inWater = horse.isInWater();
        double accel;
        if (inWater) {
            accel = 0.02;
        } else if (onGround) {
            // game: attribute speed × 0.98 (forward input) × 0.216 / friction³
            float friction = hl.clone().subtract(0, 0.5, 0).getBlock().getType().getSlipperiness();
            accel = movementSpeed(horse) * 0.98 * (0.216 / (friction * friction * friction));
        } else {
            accel = movementSpeed(horse) * 0.98 * 0.1;
        }
        accel *= forward;
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        Vec3 v = current.add(fx * accel, 0, fz * accel);

        if (onGround && !inWater && jumpCooldown <= 0 && shouldJump(horse, hl, navTarget, to, fx, fz)) {
            // the player's charged jump: the horse's jump strength and a forward boost of 0.4
            v = v.withY(horse.jumpStrength()).add(fx * 0.4 * forward, 0, fz * 0.4 * forward);
            horse.world().playSound(Locations.vec(hl), Sounds.ENTITY_HORSE_JUMP, dev.manhuntstar.platform.SoundCategory.NEUTRAL, 0.4f, 1f);
            jumpCooldown = 10;
        }
        if (inWater) {
            // swims like a horse with a rider: stays on the surface
            v = v.withY(Math.min(v.y() + 0.04, 0.3));
        }
        double h = Math.hypot(v.x(), v.z());
        if (h > MAX_HORIZONTAL_SPEED) {
            v = new Vec3(v.x() / h * MAX_HORIZONTAL_SPEED, v.y(), v.z() / h * MAX_HORIZONTAL_SPEED);
        }
        horse.setVelocity(v);
        lastSetVelocity = v;
    }

    // ------------------------------------------------------------------ horseback spear

    /** Charge only with the option on, the target at the horse's level and PvP allowed (with {@code general.respect-disabled-pvp}). */
    private boolean useLance(GameHorse horse, GameLiving target) {
        if (!bot.settings().allowSpear.on() || lanceMisses >= LANCE_MAX_MISSES
                || Math.abs(target.position().y() - horse.position().y()) > LANCE_MAX_DY) {
            return false;
        }
        return !(target instanceof GamePlayer && bot.settings().respectPvp.on()
                && Boolean.FALSE.equals(target.world().gameRule(dev.manhuntstar.platform.GameRule.PVP)));
    }

    /**
     * Joust: holds the phase's spear and raises it when it's almost at the target (the game's charge only
     * counts after the spear's delay). After hitting or passing it, keeps going straight to gain
     * distance, turns around and charges again. Damage, knockback and dismount are those of the game's
     * spear, which uses the horse's speed.
     */
    private void joust(GameHorse horse, GameLiving target) {
        holdLance();
        Location hl = PaperEntities.bukkit(horse).getLocation();
        if (runoutTicks > 0) {
            runoutTicks--;
            steer(horse, new Vec3(runoutX, hl.getY(), runoutZ), false);
            if (noMoveTicks > 5) {
                runoutTicks = 0; // wall ahead: turn back now
            }
            detail += ", passando";
            return;
        }
        Vec3 tp = target.position();
        steer(horse, tp, true);
        double yawRad = Math.toRadians(hl.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double dx = tp.x() - hl.getX();
        double dz = tp.z() - hl.getZ();
        double dist = Math.hypot(dx, dz);
        double facing = dist < 1.0E-3 ? 1.0 : (dx * fx + dz * fz) / dist;
        if (lanceRaisedTicks > 0) {
            lanceRaisedTicks++;
            bot.lookAt(tp.add(0, target.height() * 0.5, 0));
            detail += ", lance raised";
            if (lanceHit || (facing < 0 && dist < 5.0)) {
                endPass(hl, fx, fz);
            } else if (lanceRaisedTicks > LANCE_MAX_RAISED_TICKS) {
                lowerLance();
                lanceMisses++;
            } else if (!bot.mannequin().hasActiveItem()) {
                raiseLance(); // the use was interrupted (dug, switched items): raise it again
            }
            return;
        }
        Vec3 v = horse.velocity();
        double speed = Math.max(Math.hypot(v.x(), v.z()), 0.05);
        if (facing > 0.8 && dist / speed <= lanceDelay + LANCE_RAISE_MARGIN) {
            raiseLance();
        }
    }

    /** End of a pass: lowers the spear and keeps going straight ~12 blocks before turning back. */
    private void endPass(Location hl, double fx, double fz) {
        lanceMisses = lanceHit ? 0 : lanceMisses + 1;
        lanceHit = false;
        lowerLance();
        runoutX = hl.getX() + fx * 12;
        runoutZ = hl.getZ() + fz * 12;
        runoutTicks = LANCE_RUNOUT_TICKS;
    }

    private void raiseLance() {
        BotBody me = bot.entity();
        if (me == null || !GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            return;
        }
        if (PaperEntities.bukkit(me).getAttribute(Attribute.ATTACK_DAMAGE) == null) {
            // the game's charge reads the attack damage of whoever holds the spear and the Mannequin doesn't have that
            // attribute: without it the game throws an error and the server removes the horse
            PaperEntities.bukkit(me).registerAttribute(Attribute.ATTACK_DAMAGE);
            PaperEntities.bukkit(me).getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
        }
        PaperEntities.bukkit(me).startUsingItem(EquipmentSlot.HAND);
        lanceRaisedTicks = Math.max(lanceRaisedTicks, 1);
    }

    private void lowerLance() {
        BotBody me = bot.entity();
        if (me != null && PaperEntities.bukkit(me).hasActiveItem() && GearScoring.isSpear(PaperEntities.bukkit(me).getActiveItem())) {
            PaperEntities.bukkit(me).clearActiveItem();
        }
        lanceRaisedTicks = 0;
    }

    /** Lowers the spear and goes back to the sword. */
    private void stowLance() {
        lowerLance();
        if (GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            bot.inventory().restorePreferredMainHand();
        }
    }

    private void resetJoust() {
        lanceRaisedTicks = 0;
        lanceHit = false;
        lanceMisses = 0;
        runoutTicks = 0;
    }

    private void holdLance() {
        if (bot.mining().isMining() || bot.survival().isEating()) {
            return;
        }
        ItemStack l = lanceForPhase();
        if (!l.isSimilar(bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, l.clone());
        }
    }

    /** Spear matching the phase's sword level: stone (4), iron (5-6), diamond (7-10, Sharpness II from 8), netherite (final). */
    private ItemStack lanceForPhase() {
        int phase = bot.progression().phase();
        if (phase != lancePhase) {
            Material m = phase >= Progression.FINAL_PHASE ? Material.NETHERITE_SPEAR
                    : phase >= 7 ? Material.DIAMOND_SPEAR : phase >= 5 ? Material.IRON_SPEAR : Material.STONE_SPEAR;
            lance = new ItemStack(m);
            if (phase >= 8) {
                lance.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
            }
            KineticWeapon kw = lance.getData(DataComponentTypes.KINETIC_WEAPON);
            lanceDelay = kw == null ? DEFAULT_LANCE_DELAY : kw.delayTicks();
            lancePhase = phase;
        }
        return lance;
    }

    /**
     * Jumps when stuck on an obstacle, when the path's next point is too high for the horse's
     * step (1 block) or when there is a gap right ahead.
     */
    private boolean shouldJump(GameHorse horse, Location hl, Vec3 navTarget, Vec3 to, double fx, double fz) {
        if (to.lengthSqr() <= 1.0) {
            return false;
        }
        if (noMoveTicks >= 4) {
            return true;
        }
        if (navTarget.y() > hl.getY() + 1.15 && to.lengthSqr() < 9.0) {
            return true;
        }
        if (navTarget.y() < hl.getY() - 0.5) {
            return false;
        }
        Block ahead = hl.clone().add(fx * 1.2, -0.5, fz * 1.2).getBlock();
        Block ahead2 = ahead.getRelative(0, -1, 0);
        return ahead.isPassable() && ahead2.isPassable() && !ahead.isLiquid() && to.lengthSqr() > 4.0;
    }

    /** Bukkit's Vector.equals: the same velocity up to 1e-6 on each axis. */
    private static boolean sameVelocity(Vec3 a, Vec3 b) {
        return Math.abs(a.x() - b.x()) < 1.0E-6 && Math.abs(a.y() - b.y()) < 1.0E-6 && Math.abs(a.z() - b.z()) < 1.0E-6;
    }

    private static double movementSpeed(GameHorse horse) {
        AttributeInstance a = PaperEntities.bukkit(horse).getAttribute(Attribute.MOVEMENT_SPEED);
        return a == null ? 0.225 : a.getValue();
    }

    /** The horse with the smallest detour on the way to the target; none if they all need going back too far. */
    private GameHorse findCandidate(GameLiving target) {
        if (targetHorse != null && lockTicks > 0 && Platform.server().entity(targetHorse) instanceof GameHorse locked && locked.isHorse() && eligible(locked)) {
            return locked;
        }
        Vec3 me = bot.pos();
        Vec3 goal = target.position();
        double direct = me.distanceTo(goal);
        GameHorse best = null;
        double bestDetour = MAX_DETOUR;
        for (GameEntity nearby : bot.world().nearbyEntities(bot.pos(), SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            if (!(nearby instanceof GameHorse h) || !h.isHorse() || !eligible(h)) {
                continue;
            }
            Vec3 hp = h.position();
            double detour = me.distanceTo(hp) + hp.distanceTo(goal) - direct;
            if (detour < bestDetour) {
                bestDetour = detour;
                best = h;
            }
        }
        if (best != null) {
            targetHorse = best.uuid();
            lockTicks = 120;
        }
        return best;
    }

    private boolean eligible(GameHorse h) {
        if (h == null || h.isDead() || !h.isAdult() || bot.manager().isHorseInaccessible(h)) {
            return false;
        }
        return h.passengers().isEmpty() || h.passengers().getFirst() == bot.entity();
    }

    private void moveToward(Vec3 target, GameHorse horse) {
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
        bot.brain().dumbStarMove(dest, hasPath, horse);
    }

    private void resetTarget() {
        targetHorse = null;
        lockTicks = 0;
        noMoveTicks = 0;
    }
}
