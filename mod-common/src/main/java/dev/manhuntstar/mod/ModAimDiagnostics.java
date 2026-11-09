package dev.manhuntstar.mod;

import dev.manhuntstar.host.AimDiagnostics;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Aim diagnostics, on only with {@code -Dmanhuntstar.teste.mira=true} at server start: for each projectile a bot
 * shoots, the log says whether it hit the target or by how much it missed.
 */
public final class ModAimDiagnostics implements AimDiagnostics {

    public static final boolean ENABLED = Boolean.getBoolean("manhuntstar.teste.mira");

    private final Logger logger;
    private final Map<UUID, Shot> shots = new HashMap<>();

    private record Shot(String shooter, UUID target, double distance) {
    }

    public ModAimDiagnostics(Logger logger) {
        this.logger = logger;
        if (ENABLED) {
            logger.info("[aim] aim diagnostics on");
        }
    }

    /** Chance to draw a crossbow/trident per tick; higher in the diagnostics, for more shots. */
    @Override
    public double shotChance(double normal) {
        return ENABLED ? 0.25 : normal;
    }

    @Override
    public void track(GameProjectile projectile, String shooter, GameLiving target) {
        if (!ENABLED || projectile == null || target == null) {
            return;
        }
        if (shots.size() > 500) {
            shots.clear();
        }
        shots.put(projectile.uuid(), new Shot(shooter, target.uuid(), projectile.position().distanceTo(target.position())));
    }

    private static String type(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().toLowerCase(Locale.ROOT);
    }

    /** A projectile hit something. */
    void hit(Projectile projectile, HitResult result) {
        if (!ENABLED) {
            return;
        }
        Shot shot = shots.remove(projectile.getUUID());
        if (shot == null) {
            return;
        }
        Entity hitEntity = result instanceof EntityHitResult er ? er.getEntity() : null;
        Entity target = ModServer.get() == null ? null : ModEntities.vanilla(ModServer.get().entity(shot.target()));
        String head = String.format(Locale.ROOT, "[aim] %s %s at %.1f blocks: ", type(projectile), shot.shooter(), shot.distance());
        if (hitEntity != null && hitEntity.getUUID().equals(shot.target())) {
            logger.info(head + "HIT");
            return;
        }
        if (target == null || target.level() != projectile.level()) {
            logger.info(head + "missed (target gone)");
            return;
        }
        double horizontal = Math.hypot(projectile.getX() - target.getX(), projectile.getZ() - target.getZ());
        double dy = projectile.getY() - (target.getY() + target.getBbHeight() * 0.5);
        String what = hitEntity != null ? "hit another: " + type(hitEntity) : "hit a block";
        logger.info(head + String.format(Locale.ROOT, "missed (%s), %.2f blocks beside the target, %.2f %s its middle",
                what, horizontal, Math.abs(dy), dy >= 0 ? "above" : "below"));
    }
}
