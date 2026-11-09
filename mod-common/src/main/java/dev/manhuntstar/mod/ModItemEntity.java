package dev.manhuntstar.mod;

import dev.manhuntstar.mod.mixin.ItemEntityAccessor;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameItemEntity;
import net.minecraft.world.entity.item.ItemEntity;

/** A Minecraft {@link ItemEntity} as a core {@link GameItemEntity}, doing what Bukkit's {@code CraftItem} does. */
public final class ModItemEntity extends ModEntity implements GameItemEntity {

    ModItemEntity(ItemEntity item) {
        super(item);
    }

    @Override
    public ItemEntity vanilla() {
        return (ItemEntity) super.vanilla();
    }

    @Override
    public GameItem item() {
        return ModItem.of(vanilla().getItem());
    }

    @Override
    public void setItem(GameItem item) {
        vanilla().setItem(ModItem.stack(item).copy());
    }

    @Override
    public int pickupDelay() {
        return ((ItemEntityAccessor) vanilla()).manhuntstar$pickupDelay();
    }

    @Override
    public void setPickupDelay(int ticks) {
        vanilla().setPickUpDelay(ticks);
    }

    /** Vanilla has no way to keep mobs from picking up an item (Paper adds one), so they always may. */
    @Override
    public boolean canMobPickup() {
        return true;
    }
}
