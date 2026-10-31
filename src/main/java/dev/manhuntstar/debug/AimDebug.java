package dev.manhuntstar.debug;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Diagnóstico da mira, ligado só com {@code -Dmanhuntstar.teste.mira=true} na inicialização do servidor:
 * para cada projétil disparado por um bot, escreve no console se acertou o alvo ou por quanto errou.
 */
public final class AimDebug implements Listener {

    public static final boolean ENABLED = Boolean.getBoolean("manhuntstar.teste.mira");

    private static final Map<UUID, Shot> SHOTS = new HashMap<>();
    private static Logger logger;

    private record Shot(String shooter, UUID target, double distance) {
    }

    public static void install(org.bukkit.plugin.Plugin plugin) {
        if (!ENABLED) {
            return;
        }
        logger = plugin.getLogger();
        Bukkit.getPluginManager().registerEvents(new AimDebug(), plugin);
        logger.info("[aim] aim diagnostics on");
    }

    /** Chance de sacar besta/tridente por tick; no diagnóstico é maior para gerar vários tiros. */
    public static double shotChance(double normal) {
        return ENABLED ? 0.25 : normal;
    }

    public static void track(Projectile p, String shooter, LivingEntity target) {
        if (!ENABLED || p == null || target == null) {
            return;
        }
        if (SHOTS.size() > 500) {
            SHOTS.clear();
        }
        SHOTS.put(p.getUniqueId(), new Shot(shooter, target.getUniqueId(), p.getLocation().distance(target.getLocation())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        Shot shot = SHOTS.remove(event.getEntity().getUniqueId());
        if (shot == null) {
            return;
        }
        Entity target = Bukkit.getEntity(shot.target());
        String head = String.format(Locale.ROOT, "[aim] %s %s at %.1f blocks: ", event.getEntity().getType().name().toLowerCase(Locale.ROOT), shot.shooter(), shot.distance());
        if (event.getHitEntity() != null && event.getHitEntity().getUniqueId().equals(shot.target())) {
            logger.info(head + "HIT");
            return;
        }
        Location at = event.getEntity().getLocation();
        if (target == null || !target.getWorld().equals(at.getWorld())) {
            logger.info(head + "missed (target gone)");
            return;
        }
        Location t = target.getLocation();
        double horizontal = Math.hypot(at.getX() - t.getX(), at.getZ() - t.getZ());
        double dy = at.getY() - (t.getY() + target.getHeight() * 0.5);
        String what = event.getHitEntity() != null ? "hit another: " + event.getHitEntity().getType().name().toLowerCase(Locale.ROOT) : "hit a block";
        logger.info(head + String.format(Locale.ROOT, "missed (%s), %.2f blocks beside the target, %.2f %s its middle",
                what, horizontal, Math.abs(dy), dy >= 0 ? "above" : "below"));
    }
}
