package dev.manhuntstar.platform;

/** An item lying on the ground (Bukkit's Item). */
public interface GameItemEntity extends GameEntity {

    GameItem item();

    void setItem(GameItem item);

    int pickupDelay();

    void setPickupDelay(int ticks);

    /** Mobs may pick it up. */
    boolean canMobPickup();
}
