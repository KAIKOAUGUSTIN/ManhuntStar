package dev.manhuntstar.mod;

import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.Effect;
import dev.manhuntstar.platform.EffectsSnapshot;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameItemEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.platform.ProjectileKind;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A Minecraft {@link LivingEntity} as a core {@link GameLiving}, doing what Bukkit's {@code CraftLivingEntity} does. */
public class ModLiving extends ModEntity implements GameLiving {

    ModLiving(LivingEntity entity) {
        super(entity);
    }

    @Override
    public LivingEntity vanilla() {
        return (LivingEntity) super.vanilla();
    }

    /** Bukkit's yaw of a living entity is where its head turns. */
    @Override
    public float yaw() {
        return vanilla().getYHeadRot();
    }

    @Override
    public double eyeHeight() {
        return vanilla().getEyeHeight();
    }

    @Override
    public double health() {
        return vanilla().getHealth();
    }

    @Override
    public double maxHealth() {
        AttributeInstance a = vanilla().getAttribute(Attributes.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    @Override
    public void setHealth(double health) {
        LivingEntity e = vanilla();
        health = (float) health;
        if (health < 0 || health > e.getMaxHealth()) {
            throw new IllegalArgumentException("Health value (" + health + ") must be between 0 and " + e.getMaxHealth());
        }
        e.setHealth((float) health);
        if (health == 0) {
            e.die(e.damageSources().generic());
        }
    }

    @Override
    public boolean hasLineOfSight(GameEntity other) {
        return vanilla().hasLineOfSight(ModEntities.vanilla(other));
    }

    @Override
    public boolean isGliding() {
        return vanilla().isFallFlying();
    }

    @Override
    public void setGliding(boolean gliding) {
        ((EntityExt) vanilla()).manhuntstar$setSharedFlag(7, gliding);
    }

    @Override
    public boolean isClimbing() {
        return vanilla().onClimbable();
    }

    @Override
    public int remainingAir() {
        return vanilla().getAirSupply();
    }

    @Override
    public int maximumAir() {
        return vanilla().getMaxAirSupply();
    }

    @Override
    public void setRemainingAir(int ticks) {
        vanilla().setAirSupply(ticks);
    }

    @Override
    public int noDamageTicks() {
        return MinecraftCompat.damageCooldown(vanilla());
    }

    /** The game's damage cooldown, which Paper lets plugins change and the mod doesn't. */
    @Override
    public int maximumNoDamageTicks() {
        return 20;
    }

    @Override
    public void swingMainHand() {
        MinecraftCompat.swingMainHand(vanilla());
    }

    @Override
    public void knockback(double strength, double directionX, double directionZ) {
        if (strength <= 0) {
            throw new IllegalArgumentException("Knockback strength must be > 0");
        }
        LivingEntity e = vanilla();
        e.knockback(strength, directionX, directionZ, e.damageSources().generic(), 0.0F);
    }

    @Override
    public boolean hasEffect(Effect effect) {
        return vanilla().hasEffect(ModRegistry.effect(effect));
    }

    @Override
    public int effectAmplifier(Effect effect) {
        MobEffectInstance e = vanilla().getEffect(ModRegistry.effect(effect));
        return e == null ? -1 : e.getAmplifier();
    }

    @Override
    public void addEffect(Effect effect, int durationTicks, int amplifier) {
        vanilla().addEffect(new MobEffectInstance(ModRegistry.effect(effect), durationTicks, amplifier));
    }

    @Override
    public void setBodyYaw(float yaw) {
        vanilla().setYBodyRot(yaw);
    }

    @Override
    public void damage(double amount, DamageKind kind, GameEntity causing, GameEntity direct) {
        if (causing != null && direct == null) {
            // as Paper's DamageSource builder
            throw new IllegalArgumentException("Direct entity must be set if causing entity is set");
        }
        LivingEntity e = vanilla();
        DamageSource source = new DamageSource(ModRegistry.damageType(kind), ModEntities.vanilla(direct), ModEntities.vanilla(causing));
        e.hurtServer((ServerLevel) e.level(), source, (float) amount);
    }

    @Override
    public GameItem item(EquipSlot slot) {
        return ModItem.of(vanilla().getItemBySlot(ModRegistry.slot(slot)).copy());
    }

    @Override
    public void setItem(EquipSlot slot, GameItem item, boolean silent) {
        LivingEntity e = vanilla();
        ItemStack stack = ModItem.stack(item).copy();
        if (!silent || e.isSilent()) {
            e.setItemSlot(ModRegistry.slot(slot), stack);
            return;
        }
        // the equip sound is skipped for a silent entity
        e.setSilent(true);
        try {
            e.setItemSlot(ModRegistry.slot(slot), stack);
        } finally {
            e.setSilent(false);
        }
    }

    static InteractionHand hand(EquipSlot slot) {
        return switch (slot) {
            case HAND -> InteractionHand.MAIN_HAND;
            case OFF_HAND -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("not a hand: " + slot);
        };
    }

    @Override
    public void startUsingItem(EquipSlot hand) {
        vanilla().startUsingItem(hand(hand));
    }

    @Override
    public boolean hasActiveItem() {
        return vanilla().isUsingItem();
    }

    @Override
    public GameItem activeItem() {
        return ModItem.of(vanilla().getUseItem());
    }

    @Override
    public void clearActiveItem() {
        vanilla().stopUsingItem();
    }

    @Override
    public double attribute(Attr attribute, double fallback) {
        AttributeInstance a = vanilla().getAttribute(ModRegistry.attribute(attribute));
        return a == null ? fallback : a.getValue();
    }

    @Override
    public void ensureAttribute(Attr attribute, double base) {
        Holder<Attribute> a = ModRegistry.attribute(attribute);
        LivingEntity e = vanilla();
        if (e.getAttribute(a) == null) {
            ((AttributeMapExt) e.getAttributes()).manhuntstar$register(a);
            e.getAttribute(a).setBaseValue(base);
        }
    }

    @Override
    public void setAttributeBase(Attr attribute, double base) {
        AttributeInstance a = vanilla().getAttribute(ModRegistry.attribute(attribute));
        if (a != null) {
            a.setBaseValue(base);
        }
    }

    @Override
    public void playPickupAnimation(GameItemEntity item, int amount) {
        vanilla().take((ItemEntity) ModEntities.vanilla(item), amount);
    }

    @Override
    public GameProjectile launchProjectile(ProjectileKind kind, Vec3 velocity) {
        LivingEntity e = vanilla();
        ServerLevel level = (ServerLevel) e.level();
        Projectile launch = switch (kind) {
            case ARROW -> new Arrow(level, e, new ItemStack(Items.ARROW), null);
            case TRIDENT -> new ThrownTrident(level, e, new ItemStack(Items.TRIDENT));
            case ENDER_PEARL -> new ThrownEnderpearl(level, e, new ItemStack(Items.ENDER_PEARL));
        };
        float power = switch (kind) {
            case ARROW -> 3.0F;
            case TRIDENT -> 2.5F;
            case ENDER_PEARL -> 1.5F;
        };
        launch.shootFromRotation(e, e.getXRot(), e.getYRot(), 0.0F, power, 1.0F);
        if (velocity != null) {
            launch.setDeltaMovement(ModConvert.vanilla(velocity));
            MinecraftCompat.syncVelocity(launch);
        }
        level.addFreshEntity(launch);
        return (GameProjectile) ModEntities.of((Entity) launch);
    }

    @Override
    public EffectsSnapshot saveEffects() {
        List<MobEffectInstance> copies = new ArrayList<>();
        for (MobEffectInstance e : vanilla().getActiveEffects()) {
            copies.add(new MobEffectInstance(e));
        }
        return new SavedEffects(copies);
    }

    @Override
    public void restoreEffects(EffectsSnapshot effects) {
        for (MobEffectInstance e : ((SavedEffects) effects).effects()) {
            vanilla().addEffect(new MobEffectInstance(e));
        }
    }

    private record SavedEffects(List<MobEffectInstance> effects) implements EffectsSnapshot {
    }
}
