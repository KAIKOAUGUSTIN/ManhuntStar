package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameItemEntity;
import org.bukkit.entity.Item;

/** A Bukkit {@link Item} as a core {@link GameItemEntity}: every call is the Bukkit one. */
public final class PaperItemEntity extends PaperEntity implements GameItemEntity {

    PaperItemEntity(Item item) {
        super(item);
    }

    @Override
    public Item bukkit() {
        return (Item) entity;
    }

    @Override
    public GameItem item() {
        return PaperItems.of(bukkit().getItemStack());
    }

    @Override
    public void setItem(GameItem item) {
        bukkit().setItemStack(PaperItems.stack(item));
    }

    @Override
    public int pickupDelay() {
        return bukkit().getPickupDelay();
    }

    @Override
    public void setPickupDelay(int ticks) {
        bukkit().setPickupDelay(ticks);
    }

    @Override
    public boolean canMobPickup() {
        return bukkit().canMobPickup();
    }
}
