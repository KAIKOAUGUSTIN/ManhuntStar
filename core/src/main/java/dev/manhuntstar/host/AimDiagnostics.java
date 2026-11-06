package dev.manhuntstar.host;

import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;

/** Aim diagnostics for tests: logs whether the bots' shots hit (off in normal use). */
public interface AimDiagnostics {

    /** The chance per tick of starting a ranged attack; higher while diagnosing, to get more shots. */
    double shotChance(double normal);

    /** Follows a shot to log where it lands. */
    void track(GameProjectile projectile, String shooter, GameLiving target);
}
