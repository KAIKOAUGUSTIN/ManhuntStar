package dev.manhuntstar.manhunt;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decide quem saiu da roda: só conta quem esteve dentro dela (até {@code leaveRadius} do centro) e
 * depois ficou fora por {@value #OUTSIDE_TICKS} ticks seguidos, para um empurrão não disparar.
 * Quem nunca entrou não dispara nada.
 */
public final class RingExit {

    public static final int OUTSIDE_TICKS = 4;

    private final double leaveRadius;
    private final Set<UUID> inside = new HashSet<>();
    private final Map<UUID, Integer> outside = new HashMap<>();

    public RingExit(double leaveRadius) {
        this.leaveRadius = leaveRadius;
    }

    /**
     * Distância do jogador ao centro neste tick.
     *
     * @return true se ele acabou de sair da roda
     */
    public boolean observe(UUID player, double distance) {
        if (distance <= leaveRadius) {
            inside.add(player);
            outside.remove(player);
            return false;
        }
        if (!inside.contains(player)) {
            return false;
        }
        return outside.merge(player, 1, Integer::sum) >= OUTSIDE_TICKS;
    }

    /** Saiu do servidor, virou espectador ou a lista não deixa mais: deixa de contar. */
    public void forget(UUID player) {
        inside.remove(player);
        outside.remove(player);
    }

    /** Jogadores que estão (ou estavam, antes de sair por pouco tempo) dentro da roda. */
    public Set<UUID> inside() {
        return Set.copyOf(inside);
    }

    public void restoreInside(Collection<UUID> players) {
        inside.addAll(players);
    }
}
