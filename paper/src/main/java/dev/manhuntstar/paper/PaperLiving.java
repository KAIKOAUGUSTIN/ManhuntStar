package dev.manhuntstar.paper;

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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

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

    @Override
    public GameItem item(EquipSlot slot) {
        EntityEquipment eq = living().getEquipment();
        return PaperItems.of(eq == null ? null : eq.getItem(PaperItems.slot(slot)));
    }

    @Override
    public void setItem(EquipSlot slot, GameItem item, boolean silent) {
        living().getEquipment().setItem(PaperItems.slot(slot), PaperItems.stack(item), silent);
    }

    @Override
    public void startUsingItem(EquipSlot hand) {
        living().startUsingItem(PaperItems.slot(hand));
    }

    @Override
    public boolean hasActiveItem() {
        return living().hasActiveItem();
    }

    @Override
    public GameItem activeItem() {
        return PaperItems.of(living().getActiveItem());
    }

    @Override
    public void clearActiveItem() {
        living().clearActiveItem();
    }

    @Override
    public double attribute(Attr attribute, double fallback) {
        AttributeInstance a = living().getAttribute(PaperItems.attribute(attribute));
        return a == null ? fallback : a.getValue();
    }

    @Override
    public void ensureAttribute(Attr attribute, double base) {
        Attribute a = PaperItems.attribute(attribute);
        if (living().getAttribute(a) == null) {
            living().registerAttribute(a);
            living().getAttribute(a).setBaseValue(base);
        }
    }

    @Override
    public void setAttributeBase(Attr attribute, double base) {
        AttributeInstance a = living().getAttribute(PaperItems.attribute(attribute));
        if (a != null) {
            a.setBaseValue(base);
        }
    }

    @Override
    public GameProjectile launchProjectile(ProjectileKind kind, Vec3 velocity) {
        Class<? extends Projectile> type = switch (kind) {
            case ARROW -> Arrow.class;
            case TRIDENT -> Trident.class;
            case ENDER_PEARL -> EnderPearl.class;
        };
        return (GameProjectile) PaperEntities.of(living().launchProjectile(type, new Vector(velocity.x(), velocity.y(), velocity.z())));
    }

    @Override
    public EffectsSnapshot saveEffects() {
        return new SavedEffects(new ArrayList<>(living().getActivePotionEffects()));
    }

    @Override
    public void restoreEffects(EffectsSnapshot effects) {
        living().addPotionEffects(((SavedEffects) effects).effects());
    }

    private record SavedEffects(List<PotionEffect> effects) implements EffectsSnapshot {
    }

    @Override
    public void playPickupAnimation(GameItemEntity item, int amount) {
        living().playPickupItemAnimation(((PaperItemEntity) item).bukkit(), amount);
    }
}
