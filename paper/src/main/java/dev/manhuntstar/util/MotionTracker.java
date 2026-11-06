package dev.manhuntstar.util;

import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import java.util.UUID;

/**
 * A target's horizontal velocity measured from the change in position between ticks. On the server a
 * walking player's velocity isn't reliable, so aiming uses this estimate.
 */
public final class MotionTracker {

    private UUID entity;
    private GameWorld world;
    private Vec3 last;
    private long lastTick;
    private Vec3 velocity = Vec3.ZERO;

    public void observe(GameEntity e) {
        long now = Platform.server().currentTick();
        Vec3 p = e.position();
        if (last == null || !e.uuid().equals(entity) || e.world() != world || now - lastTick > 10) {
            entity = e.uuid();
            world = e.world();
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
