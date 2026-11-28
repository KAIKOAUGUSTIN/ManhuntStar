package dev.manhuntstar.bot.perception;

import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The players' trips between worlds, recorded once per tick for all the bots: where each one left a world (and the
 * portal there, if they went through one) and where they showed up in the other. The bots' compass points at that
 * portal, and a bot that goes through it comes out where the player did.
 */
public final class PlayerTravel {

    /** A player left {@code from} (at {@code fromPos}, through {@code portal} or null) and showed up in {@code to} at {@code toPos}. */
    public record Departure(UUID player, GameWorld from, Vec3 fromPos, Pos portal, GameWorld to, Vec3 toPos, long tick) {

        public boolean viaPortal() {
            return portal != null;
        }
    }

    private static final class Last {
        GameWorld world;
        Vec3 pos;
        long tick = Long.MIN_VALUE;
    }

    private final Map<UUID, Last> last = new HashMap<>();
    /** Per player, the last departure from each world (by its id). */
    private final Map<UUID, Map<UUID, Departure>> departures = new HashMap<>();

    /** Records where every online player is on this tick; returns the trips between worlds that happened on it. */
    public List<Departure> tick(Collection<? extends GamePlayer> players, long now) {
        List<Departure> out = new ArrayList<>(0);
        Set<UUID> online = new HashSet<>();
        for (GamePlayer p : players) {
            online.add(p.uuid());
            Departure d = record(p.uuid(), p.world(), p.position(), now);
            if (d != null) {
                out.add(d);
            }
        }
        last.keySet().retainAll(online);
        return out;
    }

    /** Records one player's position; a change of world from the tick before is a trip. */
    Departure record(UUID player, GameWorld world, Vec3 pos, long now) {
        Last l = last.computeIfAbsent(player, k -> new Last());
        Departure d = null;
        if (l.world != null && l.tick == now - 1 && !l.world.id().equals(world.id())) {
            d = new Departure(player, l.world, l.pos, findPortal(l.world, l.pos), world, pos, now);
            departures.computeIfAbsent(player, k -> new HashMap<>()).put(l.world.id(), d);
        }
        l.world = world;
        l.pos = pos;
        l.tick = now;
        return d;
    }

    /** The player's last departure from this world, or null. */
    public Departure departureFrom(UUID player, UUID world) {
        Map<UUID, Departure> m = departures.get(player);
        return m == null ? null : m.get(world);
    }

    /** The player's last arrival in this world (from any other), or null. */
    public Departure arrivalIn(UUID player, UUID world) {
        Map<UUID, Departure> m = departures.get(player);
        if (m == null) {
            return null;
        }
        Departure best = null;
        for (Departure d : m.values()) {
            if (d.to().id().equals(world) && (best == null || d.tick() > best.tick())) {
                best = d;
            }
        }
        return best;
    }

    /**
     * A new hunt: the old trips don't count, and the positions of the last one aren't where a player was the tick
     * before (the first sample of the hunt is where a trip can start from).
     */
    public void clear() {
        departures.clear();
        last.clear();
    }

    /** The Nether or End portal block the player was standing in (or falling into) at {@code at}, or null. */
    public static Pos findPortal(GameWorld w, Vec3 at) {
        Pos c = at.toPos();
        if (!w.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
            return null;
        }
        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Pos p = c.offset(dx, dy, dz);
                    BlockType t = w.type(p.x(), p.y(), p.z());
                    if (t != Blocks.NETHER_PORTAL && t != Blocks.END_PORTAL) {
                        continue;
                    }
                    double d = Vec3.center(p).distanceToSqr(at);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }
}
