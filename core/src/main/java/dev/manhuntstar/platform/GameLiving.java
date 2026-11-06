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
}
