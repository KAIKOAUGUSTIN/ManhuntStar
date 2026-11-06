package dev.manhuntstar.paper;

import dev.manhuntstar.debug.AimDebug;
import dev.manhuntstar.host.AimDiagnostics;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;

/** {@link AimDebug} (the {@code manhuntstar.teste.mira} system property) as core's aim diagnostics. */
public final class PaperAimDiagnostics implements AimDiagnostics {

    @Override
    public double shotChance(double normal) {
        return AimDebug.shotChance(normal);
    }

    @Override
    public void track(GameProjectile projectile, String shooter, GameLiving target) {
        AimDebug.track(PaperEntities.bukkit(projectile), shooter, PaperEntities.bukkit(target));
    }
}
