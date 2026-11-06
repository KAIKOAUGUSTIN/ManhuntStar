package dev.manhuntstar.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.GameEvents;
import dev.manhuntstar.paper.PaperBotDeath;
import dev.manhuntstar.paper.PaperDamageInfo;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.util.Locations;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Bot damage, death, totem, pearl and teleport (what the mod did inside the fake player), handed to {@link GameEvents}. */
public final class BotEntityListener implements Listener {

    private final ManhuntStarPlugin plugin;

    public BotEntityListener(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private BotManager manager() {
        return plugin.botManager();
    }

    private GameEvents events() {
        return plugin.gameEvents();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot == null) {
            return;
        }
        GameEvents.DamageDecision d = events().botDamaged(bot, new PaperDamageInfo(event.getDamageSource()), event.getDamage());
        if (d.cancelled()) {
            event.setCancelled(true);
        } else {
            event.setDamage(d.damage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBotSpear(EntityDamageEvent event) {
        DamageSource src = event.getDamageSource();
        if (!src.getDamageType().equals(DamageType.SPEAR)) {
            return;
        }
        Bot bot = manager().byEntity(PaperEntities.of(src.getCausingEntity()));
        if (bot == null) {
            return;
        }
        GameEvents.DamageDecision d = events().botSpearHits(bot, PaperEntities.of(event.getEntity()), event.getDamage());
        if (d.cancelled()) {
            event.setCancelled(true);
        } else {
            event.setDamage(d.damage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotSpearHit(EntityDamageEvent event) {
        DamageSource src = event.getDamageSource();
        if (src.getDamageType().equals(DamageType.SPEAR)) {
            Bot bot = manager().byEntity(PaperEntities.of(src.getCausingEntity()));
            if (bot != null) {
                events().botSpearLanded(bot);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageApplied(EntityDamageByEntityEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot != null && event.getFinalDamage() > 0) {
            events().botKnockedBack(bot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot != null) {
            manager().handleDeath(bot, new PaperBotDeath(event));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        events().playerDied(PaperEntities.of(event.getDamageSource().getCausingEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot != null) {
            events().botUsedTotem(bot);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot != null) {
            events().botTeleported(bot);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Mannequin m)) {
            return;
        }
        Bot bot = manager().byEntity(PaperEntities.of(m));
        if (bot == null) {
            return;
        }
        events().botPearlLanded(bot, (GameProjectile) PaperEntities.of(pearl), Locations.place(pearl.getLocation()));
    }

    @EventHandler
    public void onRemoved(EntityRemoveFromWorldEvent event) {
        Bot bot = manager().byEntity(PaperEntities.of(event.getEntity()));
        if (bot == null) {
            return;
        }
        events().botBodyRemoved(bot);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            manager().removeOrphan(PaperEntities.of(e));
        }
    }
}
