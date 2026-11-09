package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BotDeath;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GameItem;
import org.bukkit.event.entity.EntityDeathEvent;

/** A Bukkit {@link EntityDeathEvent} of a bot's body as a core {@link BotDeath}: every call is the Bukkit one. */
public record PaperBotDeath(EntityDeathEvent event) implements BotDeath {

    @Override
    public DamageInfo source() {
        return new PaperDamageInfo(event.getDamageSource());
    }

    @Override
    public void clearDrops() {
        event.getDrops().clear();
    }

    @Override
    public void addDrop(GameItem item) {
        event.getDrops().add(PaperItems.stack(item));
    }

    @Override
    public void setDroppedExp(int exp) {
        event.setDroppedExp(exp);
    }
}
