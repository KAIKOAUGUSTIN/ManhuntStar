package dev.manhuntstar.manhunt;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who left the ring: it only counts players who were inside it (up to {@code leaveRadius} from
 * the center) and then stayed outside for {@value #OUTSIDE_TICKS} ticks in a row, so a push doesn't set
 * it off. Players who never went in don't set anything off.
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
     * The player's distance to the center this tick.
     *
     * @return true if they have just left the ring
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

    /** Left the server, became a spectator or the list no longer allows them: stops counting. */
    public void forget(UUID player) {
        inside.remove(player);
        outside.remove(player);
    }

    /** Players who are (or were, before stepping out for a short time) inside the ring. */
    public Set<UUID> inside() {
        return Set.copyOf(inside);
    }

    public void restoreInside(Collection<UUID> players) {
        inside.addAll(players);
    }
}
