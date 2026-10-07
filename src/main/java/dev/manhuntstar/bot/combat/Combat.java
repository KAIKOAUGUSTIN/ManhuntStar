package dev.manhuntstar.bot.combat;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.GearScoring;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.debug.AimDebug;
import dev.manhuntstar.tactics.TacticsManager;
import dev.manhuntstar.util.Ballistics;
import dev.manhuntstar.util.MotionTracker;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Combate do bot. O corpo a corpo segue as regras de um jogador (recarga da arma, crítico ao cair,
 * golpe de varredura, repulsão de corrida, encantamentos); a humanização do mod controla erros e ritmo.
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
    private LivingEntity mobAttacker;

    private int tridentCooldown;
    private int tridentChargeTicks;
    private boolean chargingTrident;
    private Trident thrownTrident;
    private int tridentFlightTicks;
    private final MotionTracker aimTracker = new MotionTracker();

    private boolean hasLavaBucket = true;
    private boolean lavaUsedThisRun;
    private Pos placedLavaPos;
    private int lavaCleanupTicks;
    private boolean lavaHitPlayer;

    public Combat(Bot bot) {
        this.bot = bot;
        long seed = bot.id().getLeastSignificantBits() ^ bot.id().getMostSignificantBits();
        this.meleeRandom = new Random(seed);
        this.meleeRhythmBiasTicks = meleeRandom.nextInt(4);
        this.lastSuccessfulPlayerHitTick = Bukkit.getCurrentTick();
    }

    public long lastSuccessfulPlayerHitTick() {
        return lastSuccessfulPlayerHitTick;
    }

    public long lastAttackTime() {
        return lastAttackTime;
    }

    public LivingEntity mobAttacker() {
        return mobAttacker;
    }

    public void setMobAttacker(LivingEntity e) {
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

    // ------------------------------------------------------------------ escudo

    public void setShield(boolean enable) {
        if (bot.shouldHoldTotemInOffhand()) {
            hasShield = false;
            if (blocking) {
                stopBlock();
            }
            bot.setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.TOTEM_OF_UNDYING));
            return;
        }
        if (!bot.progression().canUseShield()) {
            hasShield = false;
            if (blocking) {
                stopBlock();
            }
            bot.setItem(EquipmentSlot.OFF_HAND, ItemStack.empty());
            return;
        }
        hasShield = enable;
        bot.setItem(EquipmentSlot.OFF_HAND, enable ? new ItemStack(Material.SHIELD) : ItemStack.empty());
    }

    public boolean hasUsableShield() {
        return hasShield && bot.getItem(EquipmentSlot.OFF_HAND).getType() == Material.SHIELD && !bot.shouldHoldTotemInOffhand();
    }

    public void startBlock() {
        if (!hasUsableShield() || blocking || bot.entity() == null) {
            return;
        }
        blocking = true;
        bot.entity().startUsingItem(EquipmentSlot.OFF_HAND);
    }

    public void stopBlock() {
        blocking = false;
        if (bot.entity() != null) {
            bot.entity().clearActiveItem();
        }
    }

    /** Bloqueia por um tempo e solta (API usada por capacidades). */
    public void block(int time, int cooldown) {
        if (!hasShield || blocking) {
            return;
        }
        startBlock();
        Bukkit.getScheduler().runTaskLater(bot.manager().plugin(), this::stopBlock, Math.max(1, time));
    }

    /** O escudo só bloqueia ataques vindos da frente e que não ignoram escudo (fogo, queda etc.). */
    public boolean canBlockDamage(EntityDamageEvent event) {
        DamageSource src = event.getDamageSource();
        if (src.getDamageType().equals(DamageType.FALL) || src.getDamageType().equals(DamageType.ON_FIRE)
                || src.getDamageType().equals(DamageType.IN_FIRE) || src.getDamageType().equals(DamageType.LAVA)
                || src.getDamageType().equals(DamageType.OUT_OF_WORLD) || src.getDamageType().equals(DamageType.GENERIC_KILL)
                || src.getDamageType().equals(DamageType.MAGIC) || src.getDamageType().equals(DamageType.INDIRECT_MAGIC)
                || src.getDamageType().equals(DamageType.STARVE) || src.getDamageType().equals(DamageType.DROWN)) {
            return false;
        }
        Location from = src.getSourceLocation();
        if (from == null) {
            return true;
        }
        Vec3 toSource = Vec3.of(from).subtract(bot.pos()).normalize();
        return bot.lookVector().dot(toSource) > 0.0;
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

    // ------------------------------------------------------------------ corpo a corpo

    public void attack(Entity target) {
        if (target instanceof LivingEntity living) {
            performMeleeAttack(living);
        }
    }

    private void performMeleeAttack(LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid() || bot.entity() == null) {
            return;
        }
        long now = Bukkit.getCurrentTick();
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
        Vec3 targetPos = Vec3.of(target.getLocation()).add(0, target.getHeight() / 2.0, 0);
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
        ItemStack weapon = bot.getItem(EquipmentSlot.HAND);
        int weaponCooldown = (int) Math.ceil(20.0 / GearScoring.attackSpeed(weapon));
        float strength = attackStrength(now, weaponCooldown);
        nextMeleeAttackTick = now + Math.max(computeDynamicCooldown(humanize, s.meleeDelayJitter.value(), s.meleeSpamVariance.value()), weaponCooldown);
        lastAttackTime = now;
        bot.swing();
        double missChance = Math.min(0.9, s.meleeMissChance.value() * humanize);
        if (missChance > 0 && meleeRandom.nextDouble() < missChance) {
            playSound(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
            return;
        }
        if (s.respectPvp.on() && target instanceof Player && Boolean.FALSE.equals(target.getWorld().getGameRuleValue(org.bukkit.GameRules.PVP))) {
            return;
        }
        dealPlayerLikeHit(target, weapon, strength);
    }

    private float attackStrength(long now, int cooldownTicks) {
        double since = now - lastAttackTime;
        return (float) Math.max(0, Math.min(1, (since + 0.5) / Math.max(1, cooldownTicks)));
    }

    private void dealPlayerLikeHit(LivingEntity target, ItemStack weapon, float strength) {
        Mannequin me = bot.entity();
        double base = 1.0 + GearScoring.weaponDamage(weapon);
        PotionEffect str = me.getPotionEffect(PotionEffectType.STRENGTH);
        if (str != null) {
            base += 3.0 * (str.getAmplifier() + 1);
        }
        PotionEffect weak = me.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weak != null) {
            base -= 4.0 * (weak.getAmplifier() + 1);
        }
        double enchant = enchantmentBonus(weapon, target);
        double damage = Math.max(0, base) * (0.2 + strength * strength * 0.8);
        enchant *= strength;
        boolean strong = strength > 0.9f;
        boolean sprintHit = strong && bot.motor().sprinting();
        boolean crit = strong && !bot.onGround() && bot.velocity().y() < 0 && !bot.isInWater() && !bot.isRiding()
                && !bot.motor().isOnClimbable() && !me.hasPotionEffect(PotionEffectType.BLINDNESS) && !sprintHit;
        if (crit) {
            damage *= 1.5;
        }
        damage += enchant;
        damage *= bot.difficulty().attackMultiplier();
        boolean sweep = strong && !crit && !sprintHit && bot.onGround() && GearScoring.isSword(weapon) && bot.velocity().horizontalLength() < 0.35;

        double before = target.getHealth();
        DamageSource src = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(me).withDirectEntity(me).build();
        target.damage(damage, src);
        boolean hurt = target.getHealth() < before - 1.0E-4 || target.isDead();
        if (target instanceof Player && hurt) {
            lastSuccessfulPlayerHitTick = Bukkit.getCurrentTick();
        }
        if (!hurt) {
            playSound(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
            return;
        }
        double kb = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK) + (sprintHit ? 1.0 : 0.0);
        if (kb > 0) {
            double yawRad = Math.toRadians(bot.yaw());
            target.knockback(kb * 0.5, Math.sin(yawRad), -Math.cos(yawRad));
            bot.motor().addFriction(0.6);
            bot.motor().setSprinting(false);
        }
        int fire = weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fire > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), fire * 80));
        }
        World w = target.getWorld();
        Location tl = target.getLocation();
        if (sweep) {
            doSweep(target, damage);
        }
        if (crit) {
            playSound(Sound.ENTITY_PLAYER_ATTACK_CRIT);
            w.spawnParticle(Particle.CRIT, tl.getX(), tl.getY() + target.getHeight() * 0.5, tl.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
        } else if (sprintHit) {
            playSound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK);
        } else if (!sweep) {
            playSound(strong ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_WEAK);
        }
        if (enchant > 0) {
            w.spawnParticle(Particle.ENCHANTED_HIT, tl.getX(), tl.getY() + target.getHeight() * 0.5, tl.getZ(), 8, 0.3, 0.4, 0.3, 0.2);
        }
        // Mortes de jogadores e de bots são contadas pelos eventos de morte; aqui só mobs.
        if (!(target instanceof Player) && target.isDead() && !bot.manager().isBotEntity(target)) {
            bot.incrementKills();
        }
    }

    private static double enchantmentBonus(ItemStack weapon, LivingEntity target) {
        double bonus = 0;
        int sharp = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharp > 0) {
            bonus += 0.5 * sharp + 0.5;
        }
        int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
        if (smite > 0 && Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(target.getType())) {
            bonus += 2.5 * smite;
        }
        int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        if (bane > 0 && Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(target.getType())) {
            bonus += 2.5 * bane;
        }
        return bonus;
    }

    private void doSweep(LivingEntity mainTarget, double damage) {
        Mannequin me = bot.entity();
        ItemStack weapon = bot.getItem(EquipmentSlot.HAND);
        int sweeping = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
        double ratio = sweeping > 0 ? 1.0 - 1.0 / (sweeping + 1.0) : 0;
        double sweepDamage = 1.0 + ratio * damage;
        BoundingBox box = mainTarget.getBoundingBox().expand(1.0, 0.25, 1.0);
        DamageSource src = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(me).withDirectEntity(me).build();
        for (Entity e : mainTarget.getWorld().getNearbyEntities(box)) {
            if (e == me || e == mainTarget || !(e instanceof LivingEntity le) || le.isDead() || e instanceof ArmorStand) {
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
            le.damage(sweepDamage, src);
        }
        playSound(Sound.ENTITY_PLAYER_ATTACK_SWEEP);
        double yawRad = Math.toRadians(bot.yaw());
        Vec3 p = bot.pos();
        me.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.x() - Math.sin(yawRad), p.y() + bot.height() * 0.5, p.z() + Math.cos(yawRad), 1, 0, 0, 0, 0);
    }

    private void prepareMainHand() {
        ItemStack preferred = bot.inventory().preferredMainHand();
        ItemStack current = bot.getItem(EquipmentSlot.HAND);
        if (!preferred.isEmpty()) {
            if (!preferred.isSimilar(current)) {
                bot.setItem(EquipmentSlot.HAND, preferred);
            }
        } else if (GearScoring.isTool(current)) {
            bot.setItem(EquipmentSlot.HAND, ItemStack.empty());
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

    private void playSound(Sound s) {
        Vec3 p = bot.pos();
        bot.world().playSound(p.toLocation(bot.world()), s, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------ besta

    public boolean handleCrossbowCombat(LivingEntity target, double dist) {
        if (!bot.progression().canUseCrossbow()) {
            return false;
        }
        if (crossbowCooldown > 0) {
            crossbowCooldown--;
            return false;
        }
        if (!chargingCrossbow) {
            if (dist >= 12 && dist <= 20 && bot.canSee(Vec3.of(target.getLocation())) && ThreadLocalRandom.current().nextDouble() < AimDebug.shotChance(0.01)) {
                startCrossbowCharge();
                return true;
            }
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(Vec3.of(target.getLocation()).add(0, target.getEyeHeight() * 0.8, 0));
        aimTracker.observe(target);
        swimWhileCharging(target);
        crossbowChargeTicks++;
        if (crossbowChargeTicks == 1 || crossbowChargeTicks == 10 || crossbowChargeTicks == 20) {
            bot.world().playSound(bot.location(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        if (crossbowChargeTicks >= 25) {
            performCrossbowShot(target);
            resetCrossbow();
        }
        return true;
    }

    private void swimWhileCharging(LivingEntity target) {
        if (bot.isInWater()) {
            bot.setSneaking(false);
            bot.setSwimmingPose(true);
            bot.motor().setShouldBeSwimming(true);
            Vec3 p = bot.pos();
            bot.motor().walk(new Vec3(target.getX() - p.x(), 0, target.getZ() - p.z()).normalize());
        }
    }

    private void startCrossbowCharge() {
        chargingCrossbow = true;
        crossbowChargeTicks = 0;
        aimTracker.reset();
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.CROSSBOW));
        bot.entity().startUsingItem(EquipmentSlot.HAND);
        bot.world().playSound(bot.location(), Sound.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * Velocidade de disparo de flecha/tridente rumo ao meio do corpo do alvo, com a trajetória
     * calculada pela física do jogo e o alvo antecipado. O erro é o do mod: um desvio no ponto
     * mirado que cresce com a distância, mais o ruído do disparo do jogo.
     */
    private Vector aimProjectile(LivingEntity target, double speed, double scatterPerBlock) {
        Mannequin me = bot.entity();
        Vec3 from = new Vec3(me.getX(), me.getEyeLocation().getY() - 0.1, me.getZ());
        Vec3 point = new Vec3(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        double horiz = point.horizontalDistance(from);
        double inaccuracy = bot.settings().projInaccuracy.value();
        double scatter = (0.05 + horiz * scatterPerBlock) * inaccuracy;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        point = point.add((r.nextDouble() - 0.5) * scatter, (r.nextDouble() - 0.5) * scatter, (r.nextDouble() - 0.5) * scatter);
        Vec3 v = Ballistics.aimArrow(from, point, aimTracker.velocity(), speed);
        return TacticsManager.shootVector(v.toVector(), (float) speed, (float) inaccuracy);
    }

    private void performCrossbowShot(LivingEntity target) {
        Mannequin me = bot.entity();
        me.clearActiveItem();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector dir = aimProjectile(target, 1.6, 0.015);
        Arrow arrow = me.launchProjectile(Arrow.class, dir);
        AimDebug.track(arrow, bot.name(), target);
        arrow.setShooter(me);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        me.getWorld().playSound(me.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f / (r.nextFloat() * 0.4f + 0.8f));
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

    // ------------------------------------------------------------------ impulso arriscado

    /** Às vezes usa um bot aliado como "trampolim" para avançar de repente em direção ao jogador. */
    public void handleRiskyHitBoost(Player player) {
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
            Vec3 toPlayer = Vec3.of(player.getLocation()).subtract(me).normalize();
            Vec3 fromBooster = me.subtract(booster.pos()).normalize();
            if (toPlayer.dot(fromBooster) <= 0.8) {
                continue;
            }
            bot.motor().jump();
            booster.swing();
            booster.lookAt(me);
            Vec3 boost = toPlayer.scale(0.8);
            bot.motor().setVelocity(new Vec3(boost.x(), 0.35, boost.z()));
            bot.world().playSound(bot.location(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.5f, 1.5f);
            return;
        }
    }

    // ------------------------------------------------------------------ tridente

    public boolean handleTridentCombat(LivingEntity target, double dist) {
        if (!bot.progression().canUseTrident()) {
            return false;
        }
        if (tridentCooldown > 0) {
            tridentCooldown--;
            return false;
        }
        if (!chargingTrident) {
            if (dist >= 15 && dist <= 25 && bot.canSee(Vec3.of(target.getLocation())) && ThreadLocalRandom.current().nextDouble() < AimDebug.shotChance(0.01)) {
                startTridentCharge();
                return true;
            }
            return false;
        }
        bot.motor().stopWalk();
        bot.lookAt(Vec3.of(target.getLocation()).add(0, target.getEyeHeight() * 0.8, 0));
        aimTracker.observe(target);
        swimWhileCharging(target);
        tridentChargeTicks++;
        if (tridentChargeTicks == 1 || tridentChargeTicks == 10) {
            bot.world().playSound(bot.location(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.5f, 0.8f);
        }
        if (tridentChargeTicks >= 20) {
            performTridentThrow(target);
            resetTrident();
        }
        return true;
    }

    private static ItemStack loyaltyTrident() {
        ItemStack s = new ItemStack(Material.TRIDENT);
        s.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
        return s;
    }

    private void startTridentCharge() {
        chargingTrident = true;
        tridentChargeTicks = 0;
        aimTracker.reset();
        bot.setItem(EquipmentSlot.HAND, loyaltyTrident());
        bot.entity().startUsingItem(EquipmentSlot.HAND);
    }

    private void performTridentThrow(LivingEntity target) {
        Mannequin me = bot.entity();
        me.clearActiveItem();
        Vector dir = aimProjectile(target, 2.5, 0.02);
        Trident t = me.launchProjectile(Trident.class, dir);
        AimDebug.track(t, bot.name(), target);
        t.setShooter(me);
        t.setItemStack(loyaltyTrident());
        t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        me.getWorld().playSound(me.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        thrownTrident = t;
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

    /** O tridente com lealdade volta: quando chega perto do bot ou para, é recolhido. */
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
        Vec3 toBot = bot.pos().subtract(Vec3.of(thrownTrident.getLocation()));
        Vec3 vel = Vec3.of(thrownTrident.getVelocity());
        double speed = vel.length();
        boolean stopped = thrownTrident.isOnGround() || speed < 0.1;
        boolean returning = toBot.lengthSqr() > 1.0E-6 && speed > 1.0E-6 && vel.normalize().dot(toBot.normalize()) > 0.35;
        if (returning && thrownTrident.getBoundingBox().expand(0.35).overlaps(bot.boundingBox().expand(0.9))) {
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
            bot.world().playSound(bot.location(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
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

    // ------------------------------------------------------------------ balde de lava

    public boolean handleLavaBucketCombat(LivingEntity target, double dist) {
        tickLavaCleanup(target);
        if (!bot.progression().canUseLavaBucket() || !hasLavaBucket || lavaUsedThisRun || placedLavaPos != null) {
            return false;
        }
        if (dist >= 3 && dist <= 6 && bot.canSee(Vec3.of(target.getLocation())) && ThreadLocalRandom.current().nextDouble() < 0.005) {
            return attemptLavaPlace(target);
        }
        return false;
    }

    private boolean attemptLavaPlace(LivingEntity target) {
        World w = bot.world();
        Vec3 eye = bot.eyePos();
        Vec3 dir = Vec3.of(target.getLocation()).subtract(eye).normalize();
        RayTraceResult hit = WorldUtil.rayBlocks(w, eye, eye.add(dir.scale(4.0)), true);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return false;
        }
        Block placeAt = hit.getHitBlock().getRelative(hit.getHitBlockFace());
        Pos p = Pos.of(placeAt);
        if (!placeAt.isReplaceable()) {
            return false;
        }
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.LAVA_BUCKET));
        placeAt.setType(Material.LAVA);
        bot.swing();
        w.playSound(placeAt.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.PLAYERS, 1.0f, 1.0f);
        placedLavaPos = p;
        lavaCleanupTicks = 0;
        lavaHitPlayer = false;
        hasLavaBucket = false;
        bot.manager().plugin().pathService().invalidate(w, p);
        bot.inventory().restorePreferredMainHand();
        return true;
    }

    private void tickLavaCleanup(LivingEntity target) {
        if (placedLavaPos == null) {
            return;
        }
        lavaCleanupTicks++;
        if (!lavaHitPlayer && target instanceof Player p && (p.isInLava() || p.getFireTicks() > 0)) {
            lavaHitPlayer = true;
        }
        int cleanupTime = lavaHitPlayer ? 5 : 60;
        if (lavaCleanupTicks < cleanupTime) {
            return;
        }
        World w = bot.world();
        Vec3 c = Vec3.center(placedLavaPos);
        boolean canReach = bot.distanceTo(c) <= 5.0 && bot.canSee(c);
        Block b = placedLavaPos.block(w);
        if (b.getType() == Material.LAVA) {
            b.setType(Material.AIR);
            w.playSound(b.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 1.0f, 1.0f);
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
