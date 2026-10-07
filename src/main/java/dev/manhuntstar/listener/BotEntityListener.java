package dev.manhuntstar.listener;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.api.BotEvents;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.util.Vec3;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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

/** Dano, morte, totem, pérola e teleporte dos bots (o que o mod fazia dentro do jogador falso). */
public final class BotEntityListener implements Listener {

    private final ManhuntStarPlugin plugin;

    public BotEntityListener(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private BotManager manager() {
        return plugin.botManager();
    }

    private Settings settings() {
        return plugin.settings();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot == null) {
            return;
        }
        DamageSource src = event.getDamageSource();
        boolean bypass = src.getDamageType().equals(DamageType.OUT_OF_WORLD) || src.getDamageType().equals(DamageType.GENERIC_KILL);
        if (!bypass && settings().countdownInvulnerable.on() && manager().countdown().isRunning()) {
            event.setCancelled(true);
            return;
        }
        if (!bypass && settings().botInvincible.on()) {
            event.setCancelled(true);
            return;
        }
        Entity attacker = src.getCausingEntity();
        if (attacker instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        if (attacker instanceof Player p) {
            BotEvents.BotDamageByPlayerEvent api = new BotEvents.BotDamageByPlayerEvent(bot, p, event.getDamage());
            Bukkit.getPluginManager().callEvent(api);
            if (api.isCancelled()) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(api.getDamage());
        }
        if (src.getDamageType().equals(DamageType.FALL)) {
            BotEvents.BotFallDamageEvent api = new BotEvents.BotFallDamageEvent(bot, event.getDamage());
            Bukkit.getPluginManager().callEvent(api);
            if (api.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }
        if (attacker instanceof LivingEntity le && le != bot.entity()) {
            bot.rememberFireSource(Vec3.of(le.getLocation()));
            Vec3 dir = Vec3.of(le.getLocation()).subtract(bot.pos());
            bot.setYawNow(dir.yaw());
            if (!(le instanceof Player) && !(le instanceof EnderDragon) && manager().byEntity(le) == null) {
                bot.combat().setMobAttacker(le);
            }
        }
        if (bot.combat().isBlocking() && bot.combat().canBlockDamage(event)) {
            event.setCancelled(true);
            Mannequin e = bot.entity();
            e.getWorld().playSound(e.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 0.8f + (float) Math.random() * 0.4f);
            return;
        }
        event.setDamage(event.getDamage() * bot.difficulty().damageTakenMultiplier());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageApplied(EntityDamageByEntityEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot != null && event.getFinalDamage() > 0) {
            bot.brain().onKnockback();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot != null) {
            manager().handleDeath(bot, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Bot killer = manager().byEntity(event.getDamageSource().getCausingEntity());
        if (killer != null) {
            killer.incrementKills();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (bot.isAlive()) {
                    bot.onTotemConsumed();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (bot.isAlive()) {
                    bot.onEntityTeleported();
                }
            });
        }
    }

    /** Pérola do bot: teleporta (se o jogo não o fez) e dá os 5 de dano da pérola, como no jogador. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Mannequin m)) {
            return;
        }
        Bot bot = manager().byEntity(m);
        if (bot == null) {
            return;
        }
        Location hit = pearl.getLocation().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!bot.isAlive()) {
                return;
            }
            Mannequin e = bot.entity();
            if (!e.getWorld().equals(hit.getWorld()) || e.getLocation().distanceSquared(hit) > 4.0) {
                hit.setYaw(e.getLocation().getYaw());
                hit.setPitch(e.getLocation().getPitch());
                manager().teleportBot(bot, hit);
            } else {
                bot.onEntityTeleported();
            }
            bot.motor().resetFallTracking();
            bot.entity().damage(5.0, DamageSource.builder(DamageType.ENDER_PEARL).withDirectEntity(pearl).withCausingEntity(bot.entity()).build());
            hit.getWorld().playSound(hit, Sound.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        });
    }

    /** Entidade do bot removida sem morrer (comando, outro plugin): volta no mesmo lugar. */
    @EventHandler
    public void onRemoved(EntityRemoveFromWorldEvent event) {
        Bot bot = manager().byEntity(event.getEntity());
        if (bot == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> manager().onEntityLost(bot));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            manager().removeOrphan(e);
        }
    }
}
