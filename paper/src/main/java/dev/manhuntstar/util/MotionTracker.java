package dev.manhuntstar.util;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * A target's horizontal velocity measured from the change in position between ticks. On the server a
 * walking player's velocity isn't reliable, so aiming uses this estimate.
 */
public final class MotionTracker {

    private UUID entity;
    private World world;
    private Vec3 last;
    private long lastTick;
    private Vec3 velocity = Vec3.ZERO;

    public void observe(Entity e) {
        long now = Bukkit.getCurrentTick();
        Vec3 p = Locations.vec(e.getLocation());
        if (last == null || !e.getUniqueId().equals(entity) || e.getWorld() != world || now - lastTick > 10) {
            entity = e.getUniqueId();
            world = e.getWorld();
            last = p;
            lastTick = now;
            velocity = Vec3.ZERO;
            return;
        }
        long dt = now - lastTick;
        if (dt <= 0) {
            return;
        }
        Vec3 delta = p.subtract(last).horizontal().scale(1.0 / dt);
        // teleport, pearl etc.: not movement to lead
        velocity = delta.horizontalLengthSqr() > 1.0 ? Vec3.ZERO : velocity.scale(0.6).add(delta.scale(0.4));
        last = p;
        lastTick = now;
    }

    /** Estimated horizontal velocity (blocks/tick) of the last observed target. */
    public Vec3 velocity() {
        return velocity;
    }

    public void reset() {
        entity = null;
        world = null;
        last = null;
        velocity = Vec3.ZERO;
    }
}
