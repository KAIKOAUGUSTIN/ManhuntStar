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
        logger.info("[mira] diagnóstico de mira ligado");
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
        String head = String.format(Locale.ROOT, "[mira] %s %s a %.1f blocos: ", event.getEntity().getType().name().toLowerCase(Locale.ROOT), shot.shooter(), shot.distance());
        if (event.getHitEntity() != null && event.getHitEntity().getUniqueId().equals(shot.target())) {
            logger.info(head + "ACERTOU");
            return;
        }
        Location at = event.getEntity().getLocation();
        if (target == null || !target.getWorld().equals(at.getWorld())) {
            logger.info(head + "errou (alvo sumiu)");
            return;
        }
        Location t = target.getLocation();
        double horizontal = Math.hypot(at.getX() - t.getX(), at.getZ() - t.getZ());
        double dy = at.getY() - (t.getY() + target.getHeight() * 0.5);
        String what = event.getHitEntity() != null ? "acertou outro: " + event.getHitEntity().getType().name().toLowerCase(Locale.ROOT) : "bateu num bloco";
        logger.info(head + String.format(Locale.ROOT, "errou (%s), %.2f blocos ao lado do alvo, %.2f %s do meio dele",
                what, horizontal, Math.abs(dy), dy >= 0 ? "acima" : "abaixo"));
    }
}
