package dev.manhuntstar.bot.world;

import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks placed by players (to recognize towers and skybases). Fixes the mod, which recorded
 * any click as a "placed block", didn't separate worlds and never forgot: here only what was
 * really placed ({@code BlockPlaceEvent}) goes in, per world, and it expires in 30 minutes.
 */
public final class PlayerBlockTracker {

    private static final long TTL_MS = 30L * 60L * 1000L;
    private static final int MAX_PER_WORLD = 50_000;

    private final Map<UUID, Map<Long, Long>> placed = new HashMap<>();

    public void markPlaced(GameWorld world, Pos pos) {
        Map<Long, Long> m = placed.computeIfAbsent(world.id(), k -> new HashMap<>());
        if (m.size() >= MAX_PER_WORLD) {
            purge();
            if (m.size() >= MAX_PER_WORLD) {
                Iterator<Long> it = m.keySet().iterator();
                for (int i = 0; i < MAX_PER_WORLD / 10 && it.hasNext(); i++) {
                    it.next();
                    it.remove();
                }
            }
        }
        m.put(pos.asLong(), System.currentTimeMillis() + TTL_MS);
    }

    public void unmark(GameWorld world, Pos pos) {
        Map<Long, Long> m = placed.get(world.id());
        if (m != null) {
            m.remove(pos.asLong());
        }
    }

    public boolean isPlayerPlaced(GameWorld world, Pos pos) {
        Map<Long, Long> m = placed.get(world.id());
        if (m == null) {
            return false;
        }
        Long until = m.get(pos.asLong());
        return until != null && until > System.currentTimeMillis();
    }

    public void purge() {
        long now = System.currentTimeMillis();
        for (Map<Long, Long> m : placed.values()) {
            m.values().removeIf(t -> t <= now);
        }
    }

    public void clear() {
        placed.clear();
    }
}
