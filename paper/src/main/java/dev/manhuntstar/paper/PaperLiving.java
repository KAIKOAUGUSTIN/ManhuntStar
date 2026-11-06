package dev.manhuntstar.paper;

import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.Effect;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameLiving;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;

/** A Bukkit {@link LivingEntity} as a core {@link GameLiving}: every call is the Bukkit one. */
public class PaperLiving extends PaperEntity implements GameLiving {

    PaperLiving(LivingEntity entity) {
        super(entity);
    }

    @Override
    public LivingEntity bukkit() {
        return (LivingEntity) entity;
    }

    private LivingEntity living() {
        return (LivingEntity) entity;
    }

    @Override
    public double eyeHeight() {
        return living().getEyeHeight();
    }

    @Override
    public double health() {
        return living().getHealth();
    }

    @Override
    public double maxHealth() {
        AttributeInstance a = living().getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    @Override
    public void setHealth(double health) {
        living().setHealth(health);
    }

    @Override
    public boolean hasLineOfSight(GameEntity other) {
        return living().hasLineOfSight(PaperEntities.bukkit(other));
    }

    @Override
    public boolean isGliding() {
        return living().isGliding();
    }

    @Override
    public void setGliding(boolean gliding) {
        living().setGliding(gliding);
    }

    @Override
    public boolean isClimbing() {
        return living().isClimbing();
    }

    @Override
    public int remainingAir() {
        return living().getRemainingAir();
    }

    @Override
    public int maximumAir() {
        return living().getMaximumAir();
    }

    @Override
    public void setRemainingAir(int ticks) {
        living().setRemainingAir(ticks);
    }

    @Override
    public int noDamageTicks() {
        return living().getNoDamageTicks();
    }

    @Override
    public int maximumNoDamageTicks() {
        return living().getMaximumNoDamageTicks();
    }

    @Override
    public void swingMainHand() {
        living().swingMainHand();
    }

    @Override
    public void knockback(double strength, double directionX, double directionZ) {
        living().knockback(strength, directionX, directionZ);
    }

    @Override
    public boolean hasEffect(Effect effect) {
        return living().hasPotionEffect(PaperEffects.type(effect));
    }

    @Override
    public int effectAmplifier(Effect effect) {
        PotionEffect e = living().getPotionEffect(PaperEffects.type(effect));
        return e == null ? -1 : e.getAmplifier();
    }

    @Override
    public void addEffect(Effect effect, int durationTicks, int amplifier) {
        living().addPotionEffect(new PotionEffect(PaperEffects.type(effect), durationTicks, amplifier));
    }

    @Override
    public void setBodyYaw(float yaw) {
        living().setBodyYaw(yaw);
    }

    @Override
    public void damage(double amount, DamageKind kind, GameEntity causing, GameEntity direct) {
        DamageSource.Builder b = DamageSource.builder(PaperDamage.type(kind));
        if (causing != null) {
            b.withCausingEntity(PaperEntities.bukkit(causing));
        }
        if (direct != null) {
            b.withDirectEntity(PaperEntities.bukkit(direct));
        }
        living().damage(amount, b.build());
    }
}
