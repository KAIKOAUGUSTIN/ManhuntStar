package dev.manhuntstar.platform;

import dev.manhuntstar.util.Vec3;

/** A living entity (players, mobs, the bots' bodies), like Bukkit's LivingEntity. */
public interface GameLiving extends GameEntity {

    double eyeHeight();

    default Vec3 eyePosition() {
        return position().add(0, eyeHeight(), 0);
    }

    double health();

    double maxHealth();

    void setHealth(double health);

    boolean hasLineOfSight(GameEntity other);

    boolean isGliding();

    void setGliding(boolean gliding);

    boolean isClimbing();

    int remainingAir();

    int maximumAir();

    void setRemainingAir(int ticks);

    int noDamageTicks();

    int maximumNoDamageTicks();

    void swingMainHand();

    void knockback(double strength, double directionX, double directionZ);

    boolean hasEffect(Effect effect);

    /** The amplifier of an active effect (0 = level I), or -1 when the effect isn't active. */
    int effectAmplifier(Effect effect);

    void addEffect(Effect effect, int durationTicks, int amplifier);

    void setBodyYaw(float yaw);

    /**
     * Deals damage from a source (like an attack by {@code causing}, hitting with {@code direct}); either may
     * be null.
     */
    void damage(double amount, DamageKind kind, GameEntity causing, GameEntity direct);

    /** A copy of what the entity holds or wears in a slot (empty when there's nothing). */
    GameItem item(EquipSlot slot);

    /** Puts an item in a slot; {@code silent} skips the equip sound. */
    void setItem(EquipSlot slot, GameItem item, boolean silent);

    /** Starts using the item in a hand (eating, drawing a bow, raising a shield...). */
    void startUsingItem(EquipSlot hand);

    boolean hasActiveItem();

    /** The item being used (empty when none). */
    GameItem activeItem();

    void clearActiveItem();

    /** The current value of an attribute, or {@code fallback} when the entity doesn't have it. */
    double attribute(Attr attribute, double fallback);

    /** Gives the entity an attribute it doesn't have yet, with this base value. */
    void ensureAttribute(Attr attribute, double base);

    /** Sets the base value of an attribute the entity has. */
    void setAttributeBase(Attr attribute, double base);

    /** Shows the entity picking up an item lying on the ground. */
    void playPickupAnimation(GameItemEntity item, int amount);
}
