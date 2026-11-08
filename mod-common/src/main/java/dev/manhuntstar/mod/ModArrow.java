package dev.manhuntstar.mod;

import dev.manhuntstar.mod.mixin.AbstractArrowAccessor;
import dev.manhuntstar.platform.GameArrow;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.Pickup;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** A Minecraft {@link AbstractArrow} (arrow, trident) as a core {@link GameArrow}, doing what Bukkit's {@code CraftAbstractArrow} does. */
public final class ModArrow extends ModProjectile implements GameArrow {

    ModArrow(AbstractArrow arrow) {
        super(arrow);
    }

    @Override
    public AbstractArrow vanilla() {
        return (AbstractArrow) super.vanilla();
    }

    @Override
    public void setPickup(Pickup pickup) {
        vanilla().pickup = switch (pickup) {
            case DISALLOWED -> AbstractArrow.Pickup.DISALLOWED;
            case ALLOWED -> AbstractArrow.Pickup.ALLOWED;
            case CREATIVE_ONLY -> AbstractArrow.Pickup.CREATIVE_ONLY;
        };
    }

    @Override
    public void setCritical(boolean critical) {
        vanilla().setCritArrow(critical);
    }

    @Override
    public void setItem(GameItem item) {
        ((AbstractArrowAccessor) vanilla()).manhuntstar$setPickupItemStack(ModItem.stack(item).copy());
    }
}
