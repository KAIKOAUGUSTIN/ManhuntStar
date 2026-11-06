package dev.manhuntstar.bot.combat;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.GearScoring;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.debug.AimDebug;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperItems;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.Effect;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Particle;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.RayHit;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.tactics.TacticsManager;
import dev.manhuntstar.util.Ballistics;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.MotionTracker;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.tag.DamageTypeTags;
import org.bukkit.util.Vector;

/**
 * The bot's combat. Melee follows a player's rules (weapon cooldown, critical hits when falling,
 * sweep attack, sprint knockback, enchantments); the mod's humanization controls mistakes and pace.
 */
public final class Combat {

    private final Bot bot;
    private final Random meleeRandom;
    private final int meleeRhythmBiasTicks;

    private boolean hasShield;
    private boolean blocking;
    private long lastAttackTime;
    private long lastSuccessfulPlayerHitTick;
    private long nextMeleeAttackTick;

    private int crossbowCooldown;
    private int crossbowChargeTicks;
    private boolean chargingCrossbow;

    private int hitBoostCooldown;
    private GameLiving mobAttacker;

    private int tridentCooldown;
    private int tridentChargeTicks;
    private boolean chargingTrident;
    private GameEntity thrownTrident;
    private int tridentFlightTicks;
    private final MotionTracker aimTracker = new MotionTracker();

    private boolean hasLavaBucket = true;
    private boolean lavaUsedThisRun;
    private Pos placedLavaPos;
    private GameWorld placedLavaWorld;
    private int lavaCleanupTicks;
    private boolean lavaHitPlayer;

    public Combat(Bot bot) {
        this.bot = bot;
        long seed = bot.id().getLeastSignificantBits() ^ bot.id().getMostSignificantBits();
        this.meleeRandom = new Random(seed);
        this.meleeRhythmBiasTicks = meleeRandom.nextInt(4);
        this.lastSuccessfulPlayerHitTick = Platform.server().currentTick();
    }

    public long lastSuccessfulPlayerHitTick() {
        return lastSuccessfulPlayerHitTick;
    }

    public long lastAttackTime() {
        return lastAttackTime;
    }

    public GameLiving mobAttacker() {
        return mobAttacker;
    }

    public void setMobAttacker(GameLiving e) {
        mobAttacker = e;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public boolean isChargingCrossbow() {
        return chargingCrossbow;
    }

    public boolean isChargingTrident() {
        return chargingTrident;
    }

    public void setHasShield(boolean v) {
        hasShield = v;
    }

    // ------------------------------------------------------------------ shield

    public void setShield(boolean enable) {
        if (bot.shouldHoldTotemInOffhand()) {
            hasShield = false;
            if (blocking) {
                stopBlock();
            }
            bot.setItem(EquipSlot.OFF_HAND, GameItem.of(Items.TOTEM_OF_UNDYING));
            return;
        }
        if (!bot.progression().canUseShield()) {
            hasShield = false;
            if (blocking) {
                stopBlock();
            }
            bot.setItem(EquipSlot.OFF_HAND, GameItem.empty());
            return;
        }
        hasShield = enable;
        bot.setItem(EquipSlot.OFF_HAND, enable ? GameItem.of(Items.SHIELD) : GameItem.empty());
    }

    public boolean hasUsableShield() {
        return hasShield && bot.getItem(EquipSlot.OFF_HAND).type() == Items.SHIELD && !bot.shouldHoldTotemInOffhand();
    }

    public void startBlock() {
        if (!hasUsableShield() || blocking || bot.entity() == null) {
            return;
        }
        blocking = true;
        bot.entity().startUsingItem(EquipSlot.OFF_HAND);
    }

    public void stopBlock() {
        blocking = false;
        if (bot.entity() != null) {
            bot.entity().clearActiveItem();
        }
    }

    /** Blocks for a while and lowers it (API used by abilities). */
    public void block(int time, int cooldown) {
        if (!hasShield || blocking) {
            return;
        }
        startBlock();
        Platform.server().runLater(this::stopBlock, Math.max(1, time));
    }

    /**
     * Like in the game, the shield only blocks attacks from the front, with a point of origin and that don't
     * bypass shields. Suffocation, cactus, magma, freezing and the wither effect have no origin and go through.
     */
    public boolean canBlockDamage(EntityDamageEvent event) {
        DamageSource src = event.getDamageSource();
        if (DamageTypeTags.BYPASSES_SHIELD.isTagged(src.getDamageType())
                || src.getDamageType().equals(DamageType.FALL) || src.getDamageType().equals(DamageType.ON_FIRE)
                || src.getDamageType().equals(DamageType.IN_FIRE) || src.getDamageType().equals(DamageType.LAVA)
                || src.getDamageType().equals(DamageType.OUT_OF_WORLD) || src.getDamageType().equals(DamageType.GENERIC_KILL)
                || src.getDamageType().equals(DamageType.MAGIC) || src.getDamageType().equals(DamageType.INDIRECT_MAGIC)
                || src.getDamageType().equals(DamageType.STARVE) || src.getDamageType().equals(DamageType.DROWN)) {
            return false;
        }
        Location from = src.getSourceLocation();
        if (from == null) {
            return false;
        }
        Vec3 toSource = Locations.vec(from).subtract(bot.pos()).horizontal().normalize();
        return bot.forwardVector().dot(toSource) > 0.0;
    }

    public void cancelWeaponUseForCapacity() {
        if (blocking) {
            stopBlock();
        }
        chargingCrossbow = false;
        crossbowChargeTicks = 0;
        chargingTrident = false;
        tridentChargeTicks = 0;
        if (bot.entity() != null) {
            bot.entity().clearActiveItem();
        }
    }

    /** The target disappeared (died, left) in the middle of the charge: lowers the crossbow or the trident and goes back to the usual weapon. */
    public void cancelChargeWithoutTarget() {
        if (!chargingCrossbow && !chargingTrident) {
            return;
        }
        cancelWeaponUseForCapacity();
        clearSwimStateIfDry();
        bot.inventory().restorePreferredMainHand();
    }

    // ------------------------------------------------------------------ melee

    public void attack(GameEntity target) {
        if (target instanceof GameLiving living) {
            performMeleeAttack(living);
        }
    }

    private void performMeleeAttack(GameLiving target) {
        if (target == null || target.isDead() || !target.isValid() || bot.entity() == null) {
            return;
        }
        long now = Platform.server().currentTick();
        if (now < nextMeleeAttackTick) {
            return;
        }
        Settings s = bot.settings();
        double humanize = s.humanize.value();
        double rangePenalty = s.meleeReachVariance.value() * humanize * meleeRandom.nextDouble();
        double allowedRange = Math.max(2.2, 3.2 - rangePenalty);
        if (bot.distanceTo(target) > allowedRange) {
            return;
        }
        prepareMainHand();
        Vec3 targetPos = target.position().add(0, target.height() / 2.0, 0);
        double aimOffset = s.meleeAimOffset.value();
        if (humanize > 0 && aimOffset > 0) {
            double v = aimOffset * humanize;
            targetPos = targetPos.add((meleeRandom.nextDouble() * 2 - 1) * v, 0, (meleeRandom.nextDouble() * 2 - 1) * v);
        }
        if (!bot.canSee(targetPos)) {
            return;
        }
        if (humanize > 0 && meleeRandom.nextDouble() < 0.3 * humanize) {
            nextMeleeAttackTick = now + 1 + meleeRandom.nextInt(4);
            return;
        }
        GameItem weapon = bot.getItem(EquipSlot.HAND);
        int weaponCooldown = (int) Math.ceil(20.0 / GearScoring.attackSpeed(weapon));
        float strength = attackStrength(now, weaponCooldown);
        nextMeleeAttackTick = now + Math.max(computeDynamicCooldown(humanize, s.meleeDelayJitter.value(), s.meleeSpamVariance.value()), weaponCooldown);
        lastAttackTime = now;
        bot.swing();
        double missChance = Math.min(0.9, s.meleeMissChance.value() * humanize);
        if (missChance > 0 && meleeRandom.nextDouble() < missChance) {
            playSound(Sounds.ENTITY_PLAYER_ATTACK_NODAMAGE);
            return;
        }
        if (s.respectPvp.on() && target instanceof GamePlayer && Boolean.FALSE.equals(target.world().gameRule(dev.manhuntstar.platform.GameRule.PVP))) {
            return;
        }
        dealPlayerLikeHit(target, weapon, strength);
    }

    private float attackStrength(long now, int cooldownTicks) {
        double since = now - lastAttackTime;
        return (float) Math.max(0, Math.min(1, (since + 0.5) / Math.max(1, cooldownTicks)));
    }

    private void dealPlayerLikeHit(GameLiving target, GameItem weapon, float strength) {
        BotBody me = bot.entity();
        double base = 1.0 + GearScoring.weaponDamage(weapon);
        int str = me.effectAmplifier(Effect.STRENGTH);
        if (str >= 0) {
            base += 3.0 * (str + 1);
        }
        int weak = me.effectAmplifier(Effect.WEAKNESS);
        if (weak >= 0) {
            base -= 4.0 * (weak + 1);
        }
        double enchant = enchantmentBonus(weapon, target);
        double damage = Math.max(0, base) * (0.2 + strength * strength * 0.8);
        enchant *= strength;
        boolean strong = strength > 0.9f;
        boolean sprintHit = strong && bot.motor().sprinting();
        boolean crit = strong && !bot.onGround() && bot.velocity().y() < 0 && !bot.isInWater() && !bot.isRiding()
                && !bot.motor().isOnClimbable() && !me.hasEffect(Effect.BLINDNESS) && !sprintHit;
        if (crit) {
            damage *= 1.5;
        }
        damage += enchant;
        damage *= bot.difficulty().attackMultiplier();
        boolean sweep = strong && !crit && !sprintHit && bot.onGround() && GearScoring.isSword(weapon) && bot.velocity().horizontalLength() < 0.35;

        double before = target.health();
        target.damage(damage, DamageKind.PLAYER_ATTACK, me, me);
        boolean hurt = target.health() < before - 1.0E-4 || target.isDead();
        if (target instanceof GamePlayer && hurt) {
            lastSuccessfulPlayerHitTick = Platform.server().currentTick();
        }
        if (!hurt) {
            playSound(Sounds.ENTITY_PLAYER_ATTACK_NODAMAGE);
            return;
        }
        double kb = weapon.enchantLevel(Enchant.KNOCKBACK) + (sprintHit ? 1.0 : 0.0);
        if (kb > 0) {
            double yawRad = Math.toRadians(bot.yaw());
            target.knockback(kb * 0.5, Math.sin(yawRad), -Math.cos(yawRad));
            bot.motor().addFriction(0.6);
            bot.motor().setSprinting(false);
        }
        int fire = weapon.enchantLevel(Enchant.FIRE_ASPECT);
        if (fire > 0) {
            target.setFireTicks(Math.max(target.fireTicks(), fire * 80));
        }
        GameWorld w = target.world();
        Vec3 tl = target.position();
        if (sweep) {
            doSweep(target, damage);
        }
        if (crit) {
            playSound(Sounds.ENTITY_PLAYER_ATTACK_CRIT);
            w.spawnParticle(Particle.CRIT, new Vec3(tl.x(), tl.y() + target.height() * 0.5, tl.z()), 12, 0.3, 0.4, 0.3, 0.2);
        } else if (sprintHit) {
            playSound(Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK);
        } else if (!sweep) {
            playSound(strong ? Sounds.ENTITY_PLAYER_ATTACK_STRONG : Sounds.ENTITY_PLAYER_ATTACK_WEAK);
        }
        if (enchant > 0) {
            w.spawnParticle(Particle.ENCHANTED_HIT, new Vec3(tl.x(), tl.y() + target.height() * 0.5, tl.z()), 8, 0.3, 0.4, 0.3, 0.2);
        }
        // Player and bot kills are counted by the death events; here only mobs.
        if (!(target instanceof GamePlayer) && target.isDead() && !bot.manager().isBotEntity(target)) {
            bot.incrementKills();
        }
    }

    private static double enchantmentBonus(GameItem weapon, GameLiving target) {
        double bonus = 0;
        int sharp = weapon.enchantLevel(Enchant.SHARPNESS);
        if (sharp > 0) {
            bonus += 0.5 * sharp + 0.5;
        }
        int smite = weapon.enchantLevel(Enchant.SMITE);
        if (smite > 0 && target.is(EntityTrait.SENSITIVE_TO_SMITE)) {
            bonus += 2.5 * smite;
        }
        int bane = weapon.enchantLevel(Enchant.BANE_OF_ARTHROPODS);
        if (bane > 0 && target.is(EntityTrait.SENSITIVE_TO_BANE)) {
            bonus += 2.5 * bane;
        }
        return bonus;
    }

    private void doSweep(GameLiving mainTarget, double damage) {
        BotBody me = bot.entity();
        GameItem weapon = bot.getItem(EquipSlot.HAND);
        int sweeping = weapon.enchantLevel(Enchant.SWEEPING_EDGE);
        double ratio = sweeping > 0 ? 1.0 - 1.0 / (sweeping + 1.0) : 0;
        double sweepDamage = 1.0 + ratio * damage;
        Box box = mainTarget.boundingBox().expand(1.0, 0.25, 1.0);
        for (GameEntity e : mainTarget.world().nearbyEntities(box)) {
            if (e == me || e == mainTarget || !(e instanceof GameLiving le) || le.isDead() || e.is(EntityTrait.ARMOR_STAND)) {
                continue;
            }
            if (bot.manager().isAlly(bot, e)) {
                continue;
            }
            if (bot.distanceTo(e) > 3.0) {
                continue;
            }
            double yawRad = Math.toRadians(bot.yaw());
            le.knockback(0.4, Math.sin(yawRad), -Math.cos(yawRad));
            le.damage(sweepDamage, DamageKind.PLAYER_ATTACK, me, me);
        }
        playSound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP);
        double yawRad = Math.toRadians(bot.yaw());
        Vec3 p = bot.pos();
        me.world().spawnParticle(Particle.SWEEP_ATTACK, new Vec3(p.x() - Math.sin(yawRad), p.y() + bot.height() * 0.5, p.z() + Math.cos(yawRad)), 1, 0, 0, 0, 0);
    }

    private void prepareMainHand() {
        GameItem preferred = bot.inventory().preferredMainHand();
        GameItem current = bot.getItem(EquipSlot.HAND);
        if (!preferred.isEmpty()) {
            if (!preferred.isSimilar(current)) {
                bot.setItem(EquipSlot.HAND, preferred);
            }
        } else if (GearScoring.isTool(current)) {
            bot.setItem(EquipSlot.HAND, GameItem.empty());
        }
    }

    private int computeDynamicCooldown(double humanize, double delayJitterBase, double spamVarianceBase) {
        int cooldown = 10 + meleeRhythmBiasTicks;
        cooldown += (int) Math.round(delayJitterBase * humanize * meleeRandom.nextDouble());
        if (humanize > 0 && spamVarianceBase > 0 && meleeRandom.nextDouble() < 0.5 * humanize) {
            cooldown += (int) Math.round(spamVarianceBase * humanize * meleeRandom.nextDouble());
        }
        return Math.max(6, cooldown);
    }

    private void playSound(GameSound s) {
        Vec3 p = bot.pos();
        bot.world().playSound(p, s, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------ crossbow

    public boolean handleCrossbowCombat(GameLiving target, double dist) {
        if (!bot.progression().canUseCrossbow()) {
            return false;
        }
        if (crossbowCooldown > 0) {
            crossbowCooldown--;
            return false;
        }
        if (!chargingCrossbow) {
            if (dist >= 12 && dist <= 20 && bot.canSee(target.position()) && ThreadLocalRandom.current().nextDouble() < AimDebug.shotChance(0.01)) {
                startCrossbowCharge();
                return true;
            }
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(target.position().add(0, target.eyeHeight() * 0.8, 0));
        aimTracker.observe(target);
        swimWhileCharging(target);
        crossbowChargeTicks++;
        if (crossbowChargeTicks == 1 || crossbowChargeTicks == 10 || crossbowChargeTicks == 20) {
            bot.world().playSound(bot.pos(), Sounds.ITEM_CROSSBOW_LOADING_MIDDLE, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        if (crossbowChargeTicks >= 25) {
            performCrossbowShot(target);
            resetCrossbow();
        }
        return true;
    }

    private void swimWhileCharging(GameLiving target) {
        if (bot.isInWater()) {
            bot.setSneaking(false);
            bot.setSwimmingPose(true);
            bot.motor().setShouldBeSwimming(true);
            Vec3 p = bot.pos();
            bot.motor().walk(new Vec3(target.position().x() - p.x(), 0, target.position().z() - p.z()).normalize());
        }
    }

    private void startCrossbowCharge() {
        chargingCrossbow = true;
        crossbowChargeTicks = 0;
        aimTracker.reset();
        bot.setItem(EquipSlot.HAND, GameItem.of(Items.CROSSBOW));
        bot.entity().startUsingItem(EquipSlot.HAND);
        bot.world().playSound(bot.pos(), Sounds.ITEM_CROSSBOW_LOADING_START, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * Arrow/trident shot velocity toward the middle of the target's body, with the trajectory
     * computed with the game's physics and the target led. The error is the mod's: an offset on the aimed
     * point that grows with distance, plus the game's shot spread.
     */
    private Vector aimProjectile(GameLiving target, double speed, double scatterPerBlock) {
        BotBody me = bot.entity();
        Vec3 from = new Vec3(me.position().x(), me.eyePosition().y() - 0.1, me.position().z());
        Vec3 point = new Vec3(target.position().x(), target.position().y() + target.height() * 0.5, target.position().z());
        double horiz = point.horizontalDistance(from);
        double inaccuracy = bot.settings().projInaccuracy.value();
        double scatter = (0.05 + horiz * scatterPerBlock) * inaccuracy;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        point = point.add((r.nextDouble() - 0.5) * scatter, (r.nextDouble() - 0.5) * scatter, (r.nextDouble() - 0.5) * scatter);
        Vec3 v = Ballistics.aimArrow(from, point, aimTracker.velocity(), speed);
        return TacticsManager.shootVector(Locations.vector(v), (float) speed, (float) inaccuracy);
    }

    private void performCrossbowShot(GameLiving target) {
        BotBody me = bot.entity();
        me.clearActiveItem();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector dir = aimProjectile(target, 1.6, 0.015);
        Arrow arrow = PaperEntities.bukkit(me).launchProjectile(Arrow.class, dir);
        AimDebug.track(arrow, bot.name(), PaperEntities.bukkit(target));
        arrow.setShooter(PaperEntities.bukkit(me));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        me.world().playSound(me.position(), Sounds.ITEM_CROSSBOW_SHOOT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f / (r.nextFloat() * 0.4f + 0.8f));
        bot.swing();
    }

    private void resetCrossbow() {
        chargingCrossbow = false;
        crossbowChargeTicks = 0;
        crossbowCooldown = 100;
        if (bot.entity() != null) {
            bot.entity().clearActiveItem();
        }
        clearSwimStateIfDry();
        bot.inventory().restorePreferredMainHand();
    }

    // ------------------------------------------------------------------ risky boost

    /** Sometimes uses an allied bot as a "springboard" to suddenly lunge toward the player. */
    public void handleRiskyHitBoost(GamePlayer player) {
        if (hitBoostCooldown > 0) {
            hitBoostCooldown--;
            return;
        }
        hitBoostCooldown = 50;
        if (ThreadLocalRandom.current().nextFloat() > 0.15f) {
            return;
        }
        double d = bot.distanceTo(player);
        if (d > 12 || d < 4 || !bot.onGround()) {
            return;
        }
        Vec3 me = bot.pos();
        for (Bot booster : bot.manager().aliveBots()) {
            if (booster == bot) {
                continue;
            }
            double db = me.distanceTo(booster.pos());
            if (db <= 1.2 || db >= 3.0) {
                continue;
            }
            Vec3 toPlayer = player.position().subtract(me).normalize();
            Vec3 fromBooster = me.subtract(booster.pos()).normalize();
            if (toPlayer.dot(fromBooster) <= 0.8) {
                continue;
            }
            bot.motor().jump();
            booster.swing();
            booster.lookAt(me);
            Vec3 boost = toPlayer.scale(0.8);
            bot.motor().setVelocity(new Vec3(boost.x(), 0.35, boost.z()));
            bot.world().playSound(bot.pos(), Sounds.ENTITY_PLAYER_HURT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.5f, 1.5f);
            return;
        }
    }

    // ------------------------------------------------------------------ trident

    public boolean handleTridentCombat(GameLiving target, double dist) {
        if (!bot.progression().canUseTrident()) {
            return false;
        }
        if (tridentCooldown > 0) {
            tridentCooldown--;
            return false;
        }
        if (!chargingTrident) {
            if (dist >= 15 && dist <= 25 && bot.canSee(target.position()) && ThreadLocalRandom.current().nextDouble() < AimDebug.shotChance(0.01)) {
                startTridentCharge();
                return true;
            }
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(target.position().add(0, target.eyeHeight() * 0.8, 0));
        aimTracker.observe(target);
        swimWhileCharging(target);
        tridentChargeTicks++;
        if (tridentChargeTicks == 1 || tridentChargeTicks == 10) {
            bot.world().playSound(bot.pos(), Sounds.ITEM_TRIDENT_THROW, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.5f, 0.8f);
        }
        if (tridentChargeTicks >= 20) {
            performTridentThrow(target);
            resetTrident();
        }
        return true;
    }

    private static GameItem loyaltyTrident() {
        GameItem s = GameItem.of(Items.TRIDENT);
        s.addEnchant(Enchant.LOYALTY, 3);
        return s;
    }

    private void startTridentCharge() {
        chargingTrident = true;
        tridentChargeTicks = 0;
        aimTracker.reset();
        bot.setItem(EquipSlot.HAND, loyaltyTrident());
        bot.entity().startUsingItem(EquipSlot.HAND);
    }

    private void performTridentThrow(GameLiving target) {
        BotBody me = bot.entity();
        me.clearActiveItem();
        Vector dir = aimProjectile(target, 2.5, 0.02);
        Trident t = PaperEntities.bukkit(me).launchProjectile(Trident.class, dir);
        AimDebug.track(t, bot.name(), PaperEntities.bukkit(target));
        t.setShooter(PaperEntities.bukkit(me));
        t.setItemStack(PaperItems.stack(loyaltyTrident()));
        t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        me.world().playSound(me.position(), Sounds.ITEM_TRIDENT_THROW, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
        thrownTrident = PaperEntities.of(t);
        tridentFlightTicks = 0;
        bot.swing();
    }

    private void resetTrident() {
        chargingTrident = false;
        tridentChargeTicks = 0;
        tridentCooldown = 120;
        if (bot.entity() != null) {
            bot.entity().clearActiveItem();
        }
        clearSwimStateIfDry();
        bot.inventory().restorePreferredMainHand();
    }

    /** A loyalty trident comes back: when it gets close to the bot or stops, it's picked up. */
    public void tickTridentPickup() {
        if (thrownTrident == null) {
            return;
        }
        tridentFlightTicks++;
        if (!thrownTrident.isValid()) {
            thrownTrident = null;
            return;
        }
        if (tridentFlightTicks > 160) {
            clearTrident(true);
            return;
        }
        if (tridentFlightTicks < 20) {
            return;
        }
        Vec3 toBot = bot.pos().subtract(thrownTrident.position());
        Vec3 vel = thrownTrident.velocity();
        double speed = vel.length();
        boolean stopped = thrownTrident.isOnGround() || speed < 0.1;
        boolean returning = toBot.lengthSqr() > 1.0E-6 && speed > 1.0E-6 && vel.normalize().dot(toBot.normalize()) > 0.35;
        if (returning && thrownTrident.boundingBox().expand(0.35).overlaps(bot.boundingBox().expand(0.9))) {
            clearTrident(true);
            return;
        }
        if (stopped || (returning && tridentFlightTicks >= 90)) {
            if (toBot.length() <= 3.0) {
                clearTrident(true);
            }
        }
    }

    private void clearTrident(boolean sound) {
        if (thrownTrident == null) {
            return;
        }
        if (sound && bot.world() != null) {
            bot.world().playSound(bot.pos(), Sounds.ENTITY_ITEM_PICKUP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.5f, 1.0f);
        }
        thrownTrident.remove();
        thrownTrident = null;
        tridentFlightTicks = 0;
    }

    private void clearSwimStateIfDry() {
        if (bot.isInWater() || bot.isInLava() || bot.entity() == null) {
            return;
        }
        bot.motor().setShouldBeSwimming(false);
        bot.setSwimmingPose(false);
        bot.motor().setSprinting(false);
        bot.setSneaking(false);
        Vec3 v = bot.motor().velocity();
        if (!bot.onGround() && v.y() > 0) {
            bot.motor().setVelocity(v.withY(0));
        }
    }

    // ------------------------------------------------------------------ lava bucket

    public boolean handleLavaBucketCombat(GameLiving target, double dist) {
        if (!bot.progression().canUseLavaBucket() || !hasLavaBucket || lavaUsedThisRun || placedLavaPos != null) {
            return false;
        }
        if (dist >= 3 && dist <= 6 && bot.canSee(target.position()) && ThreadLocalRandom.current().nextDouble() < 0.005) {
            return attemptLavaPlace(target);
        }
        return false;
    }

    private boolean attemptLavaPlace(GameLiving target) {
        GameWorld w = bot.world();
        Vec3 eye = bot.eyePos();
        Vec3 dir = target.position().subtract(eye).normalize();
        RayHit hit = WorldUtil.rayBlocks(w, eye, eye.add(dir.scale(4.0)), true);
        if (hit == null || hit.face() == null) {
            return false;
        }
        GameBlock placeAt = w.block(hit.block()).relative(hit.face());
        Pos p = placeAt.pos();
        if (!placeAt.isReplaceable()) {
            return false;
        }
        bot.setItem(EquipSlot.HAND, GameItem.of(Items.LAVA_BUCKET));
        placeAt.setType(Blocks.LAVA);
        bot.swing();
        w.playSound(Vec3.of(p), Sounds.ITEM_BUCKET_EMPTY_LAVA, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
        placedLavaPos = p;
        placedLavaWorld = w;
        lavaCleanupTicks = 0;
        lavaHitPlayer = false;
        hasLavaBucket = false;
        // if the bot dies or disappears before picking it up, the lava doesn't stay in the world forever
        bot.manager().temporaryBlocks().add(w, p, Blocks.LAVA, 60 + 40);
        bot.manager().plugin().pathService().invalidate(w, p);
        bot.inventory().restorePreferredMainHand();
        return true;
    }

    /**
     * Picks up the placed lava. Runs every tick, whatever the AI decided: it used to run only while the bot
     * stayed in bucket combat, and the lava stayed if it fled or mounted.
     */
    public void tickLavaCleanup() {
        if (placedLavaPos == null) {
            return;
        }
        lavaCleanupTicks++;
        GamePlayer target = bot.target();
        if (!lavaHitPlayer && target != null && (target.isInLava() || target.fireTicks() > 0)) {
            lavaHitPlayer = true;
        }
        int cleanupTime = lavaHitPlayer ? 5 : 60;
        if (lavaCleanupTicks < cleanupTime) {
            return;
        }
        GameWorld w = placedLavaWorld;
        boolean sameWorld = w.equals(bot.world());
        Vec3 c = Vec3.center(placedLavaPos);
        boolean canReach = sameWorld && bot.distanceTo(c) <= 5.0 && bot.canSee(c);
        GameBlock b = w.block(placedLavaPos);
        // in another world the bot can't reach it: the temporary block registry picks it up
        if (sameWorld && b.type() == Blocks.LAVA) {
            bot.manager().temporaryBlocks().remove(w, placedLavaPos);
            b.setType(Blocks.AIR);
            w.playSound(Vec3.of(placedLavaPos), Sounds.ITEM_BUCKET_FILL_LAVA, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
            bot.manager().plugin().pathService().invalidate(w, placedLavaPos);
            if (canReach) {
                hasLavaBucket = true;
            } else {
                lavaUsedThisRun = true;
            }
        } else {
            lavaUsedThisRun = true;
        }
        placedLavaPos = null;
        lavaCleanupTicks = 0;
        lavaHitPlayer = false;
    }
}
