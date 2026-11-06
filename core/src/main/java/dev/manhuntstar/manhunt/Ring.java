package dev.manhuntstar.manhunt;

import dev.manhuntstar.util.Vec3;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * Uma roda de bots esperando a caçada começar. Não tem dono: qualquer jogador que estava dentro e
 * sai dispara a contagem. Cada roda tem a sua contagem e a sua barra.
 */
public final class Ring {

    enum Phase { WAITING, COUNTING }

    final UUID id;
    final UUID worldId;
    final Vec3 center;
    final double radius;
    /** Contagem definida quando a roda foi criada. */
    final int seconds;
    final Set<UUID> bots = new LinkedHashSet<>();
    final RingExit exit;
    final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    final Set<UUID> viewers = new HashSet<>();
    Phase phase = Phase.WAITING;
    int ticksLeft;

    Ring(UUID id, UUID worldId, Vec3 center, double radius, int seconds, double leaveRadius) {
        this.id = id;
        this.worldId = worldId;
        this.center = center;
        this.radius = radius;
        this.seconds = seconds;
        this.exit = new RingExit(leaveRadius);
        this.ticksLeft = seconds * 20;
    }

    public UUID id() {
        return id;
    }

    public UUID worldId() {
        return worldId;
    }

    public Vec3 center() {
        return center;
    }

    public boolean isCounting() {
        return phase == Phase.COUNTING;
    }

    int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }
}
