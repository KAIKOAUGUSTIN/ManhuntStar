package dev.manhuntstar.platform;

/** A projectile in flight (arrow, trident, ender pearl, firework...). */
public interface GameProjectile extends GameEntity {

    /** Who shot it (the kill and the pearl teleport go to them). */
    void setShooter(GameLiving shooter);

    /** Who shot it, when it was an entity; otherwise null. */
    GameEntity shooter();
}
