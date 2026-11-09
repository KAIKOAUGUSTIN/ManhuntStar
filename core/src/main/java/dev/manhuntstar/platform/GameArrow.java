package dev.manhuntstar.platform;

/** An arrow or a trident (the game's AbstractArrow). */
public interface GameArrow extends GameProjectile {

    void setPickup(Pickup pickup);

    /** A critical arrow (from a fully drawn bow) does more damage and leaves a trail. */
    void setCritical(boolean critical);

    /** The item it gives back when picked up (a trident's own item, with its enchantments). */
    void setItem(GameItem item);
}
