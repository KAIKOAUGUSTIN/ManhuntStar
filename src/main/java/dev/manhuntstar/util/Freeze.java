package dev.manhuntstar.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Entidade parada onde estava (até no ar) enquanto a caçada está pausada: sem gravidade, sem
 * velocidade e devolvida ao lugar se alguém a empurrar. Ao soltar, volta a gravidade, a velocidade e
 * a altura de queda que ela tinha, e o movimento continua de onde parou.
 */
public final class Freeze {

    private static final double DRIFT_SQ = 1.0E-4;

    private final Entity entity;
    private final Location at;
    private final Vector velocity;
    private final boolean gravity;
    private final float fallDistance;
    private final boolean wasInvulnerable;
    private final boolean protect;

    private Freeze(Entity entity, boolean protect) {
        this.entity = entity;
        this.at = entity.getLocation();
        this.velocity = entity.getVelocity();
        this.gravity = entity.hasGravity();
        this.fallDistance = entity.getFallDistance();
        this.wasInvulnerable = entity.isInvulnerable();
        this.protect = protect;
    }

    /**
     * Congela a entidade agora.
     *
     * @param protect deixa a entidade invulnerável enquanto congelada (o cavalo ou o barco do bot)
     */
    public static Freeze capture(Entity e, boolean protect) {
        Freeze f = new Freeze(e, protect);
        e.setGravity(false);
        e.setVelocity(new Vector());
        if (protect) {
            e.setInvulnerable(true);
        }
        return f;
    }

    public Entity entity() {
        return entity;
    }

    /**
     * A cada tick: zera a velocidade e devolve ao lugar quem foi empurrado. O passageiro segue o
     * veículo; o veículo (com o bot em cima) volta pela velocidade, sem teleporte.
     */
    public void hold() {
        if (!entity.isValid()) {
            return;
        }
        entity.setGravity(false);
        Location now = entity.getLocation();
        boolean drifted = !entity.isInsideVehicle() && now.getWorld().equals(at.getWorld()) && now.distanceSquared(at) > DRIFT_SQ;
        if (drifted && entity.getPassengers().isEmpty()) {
            entity.setVelocity(new Vector());
            entity.teleport(at);
        } else if (drifted) {
            entity.setVelocity(at.toVector().subtract(now.toVector()));
        } else {
            entity.setVelocity(new Vector());
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
