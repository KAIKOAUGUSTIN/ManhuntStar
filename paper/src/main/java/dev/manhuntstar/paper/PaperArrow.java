package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameArrow;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.Pickup;
import org.bukkit.entity.AbstractArrow;

/** A Bukkit {@link AbstractArrow} (arrow, trident) as a core {@link GameArrow}: every call is the Bukkit one. */
public final class PaperArrow extends PaperProjectile implements GameArrow {

    PaperArrow(AbstractArrow arrow) {
        super(arrow);
    }

    @Override
    public AbstractArrow bukkit() {
        return (AbstractArrow) entity;
    }

    @Override
    public void setPickup(Pickup pickup) {
        bukkit().setPickupStatus(switch (pickup) {
            case DISALLOWED -> AbstractArrow.PickupStatus.DISALLOWED;
            case ALLOWED -> AbstractArrow.PickupStatus.ALLOWED;
            case CREATIVE_ONLY -> AbstractArrow.PickupStatus.CREATIVE_ONLY;
        });
    }

    @Override
    public void setCritical(boolean critical) {
        bukkit().setCritical(critical);
    }

    @Override
    public void setItem(GameItem item) {
        bukkit().setItemStack(PaperItems.stack(item));
    }
}
