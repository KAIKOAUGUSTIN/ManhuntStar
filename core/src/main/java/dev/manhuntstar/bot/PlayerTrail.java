package dev.manhuntstar.bot;

import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Vec3;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Where each online player was in the last ticks, recorded once per server tick for all the bots: a bot's melee
 * hit on a player also checks where the player was a few ticks before ({@code MeleeReach}).
 */
public final class PlayerTrail {

    /** Ticks kept per player. */
    static final int LENGTH = 8;

    private final Map<UUID, Trail> trails = new HashMap<>();

    /** One player's last positions, newest at {@code head}, all from consecutive ticks in the same world. */
    private static final class Trail {
        final Vec3[] positions = new Vec3[LENGTH];
        Object world;
        long lastTick;
        int head;
        int count;
    }

    /** Records where every online player is on this tick, and forgets the ones who left. */
    public void tick(Collection<? extends GamePlayer> players, long now) {
        Set<UUID> online = new HashSet<>();
        for (GamePlayer p : players) {
            online.add(p.uuid());
            record(p.uuid(), p.world(), p.position(), now);
        }
        trails.keySet().retainAll(online);
    }

    /** Records a position; a missed tick or another world starts the trail over. */
    void record(UUID player, Object world, Vec3 position, long now) {
        Trail t = trails.computeIfAbsent(player, k -> new Trail());
        if (t.count > 0 && t.lastTick == now && Objects.equals(t.world, world)) {
            // recorded twice on the same tick: the newest position stands
            t.positions[t.head] = position;
            return;
        }
        if (t.count > 0 && (t.lastTick != now - 1 || !Objects.equals(t.world, world))) {
            t.count = 0;
        }
        t.head = (t.head + 1) % LENGTH;
        t.positions[t.head] = position;
        t.count = Math.min(LENGTH, t.count + 1);
        t.world = world;
        t.lastTick = now;
    }

    /**
     * Where the player was {@code ticksAgo} ticks before this one (0: this tick), or null when that isn't known: the
     * player wasn't recorded on every tick since (just joined, changed world) or this tick wasn't recorded yet.
     */
    public Vec3 at(UUID player, int ticksAgo, long now) {
        Trail t = trails.get(player);
        if (t == null || t.lastTick != now || ticksAgo < 0 || ticksAgo >= t.count) {
            return null;
        }
        return t.positions[Math.floorMod(t.head - ticksAgo, LENGTH)];
    }
}
