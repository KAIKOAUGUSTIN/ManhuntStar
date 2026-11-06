package dev.manhuntstar.util;

import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWorld;

/**
 * Holds an entity where it was (even mid-air) while the hunt is paused: no gravity, no velocity, and
 * put back in place if someone pushes it. On release, the gravity, velocity and fall distance it had
 * come back, and the movement carries on from where it stopped.
 */
public final class Freeze {

    private static final double DRIFT_SQ = 1.0E-4;

    private final GameEntity entity;
    private final GameWorld atWorld;
    private final Vec3 at;
    private final float atYaw;
    private final float atPitch;
    private final Vec3 velocity;
    private final boolean gravity;
    private final float fallDistance;
    private final boolean wasInvulnerable;
    private final boolean protect;

    private Freeze(GameEntity entity, boolean protect) {
        this.entity = entity;
        this.atWorld = entity.world();
        this.at = entity.position();
        this.atYaw = entity.yaw();
        this.atPitch = entity.pitch();
        this.velocity = entity.velocity();
        this.gravity = entity.hasGravity();
        this.fallDistance = entity.fallDistance();
        this.wasInvulnerable = entity.isInvulnerable();
        this.protect = protect;
    }

    /**
     * Freezes the entity now.
     *
     * @param protect makes the entity invulnerable while frozen (the bot's horse or boat)
     */
    public static Freeze capture(GameEntity e, boolean protect) {
        Freeze f = new Freeze(e, protect);
        e.setGravity(false);
        e.setVelocity(Vec3.ZERO);
        if (protect) {
            e.setInvulnerable(true);
        }
        return f;
    }

    public GameEntity entity() {
        return entity;
    }

    /**
     * Every tick: clears the velocity and puts back whoever was pushed. The passenger follows the
     * vehicle; the vehicle (with the bot on it) goes back through its velocity, without a teleport.
     */
    public void hold() {
        if (!entity.isValid()) {
            return;
        }
        entity.setGravity(false);
        Vec3 now = entity.position();
        boolean drifted = !entity.isInsideVehicle() && entity.world().equals(atWorld) && now.distanceToSqr(at) > DRIFT_SQ;
        if (drifted && entity.passengers().isEmpty()) {
            entity.setVelocity(Vec3.ZERO);
            entity.teleport(atWorld, at, atYaw, atPitch);
        } else if (drifted) {
            entity.setVelocity(at.subtract(now));
        } else {
            entity.setVelocity(Vec3.ZERO);
        }
    }

    public void release() {
        if (!entity.isValid()) {
            return;
        }
        entity.setGravity(gravity);
        entity.setFallDistance(fallDistance);
        entity.setVelocity(velocity);
        if (protect) {
            entity.setInvulnerable(wasInvulnerable);
        }
    }
}
