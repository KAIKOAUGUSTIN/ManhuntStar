package dev.manhuntstar.bot.perception;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * The hunters' "call": what any bot sees or hears goes at once to every bot of its group and of the groups that
 * aren't its enemies. Each group keeps the best clue about each player ({@link Sighting#replaces}); a bot asks for
 * the best one among its own group and its allies ({@code allies} says whether two groups, by name, share).
 *
 * <p>A clue a bot got to without finding the player is dropped for the team's search ({@link #best}), but it is still
 * where the team last noticed them: the teleports near the player go by it ({@link #last}).
 *
 * <p>It also keeps who knows that a player left their world through a portal: everyone, when the game announces it
 * (the advancement for entering the Nether or the End), or a group that found the portal the compass points at.
 */
public final class TeamIntel {

    private final Map<String, Map<UUID, Sighting>> clues = new HashMap<>();
    /** Per group, the clues about each player up to this tick were followed up and found empty. */
    private final Map<String, Map<UUID, Long>> dismissed = new HashMap<>();
    private final Map<String, Map<UUID, Long>> foundPortals = new HashMap<>();
    private final Map<UUID, Long> announced = new HashMap<>();

    private static String key(String group) {
        return group == null ? "" : group.toLowerCase(Locale.ROOT);
    }

    /** What a bot of {@code group} just noticed. */
    public void report(String group, Sighting s) {
        Map<UUID, Sighting> m = clues.computeIfAbsent(key(group), k -> new HashMap<>());
        Sighting old = m.get(s.player());
        if (s.replaces(old)) {
            m.put(s.player(), s);
        }
    }

    /**
     * The best clue about the player that a bot of {@code group} still has to follow up, no older than {@code maxAge}
     * ticks; or null.
     */
    public Sighting best(String group, BiPredicate<String, String> allies, UUID player, long now, long maxAge) {
        return find(group, allies, player, now, maxAge, false);
    }

    /**
     * Where the team of {@code group} last saw or heard the player, no longer than {@code maxAge} ticks ago, also when
     * a bot already got there and they weren't there anymore; or null.
     */
    public Sighting last(String group, BiPredicate<String, String> allies, UUID player, long now, long maxAge) {
        return find(group, allies, player, now, maxAge, true);
    }

    private Sighting find(String group, BiPredicate<String, String> allies, UUID player, long now, long maxAge, boolean withDismissed) {
        Sighting best = null;
        for (Map.Entry<String, Map<UUID, Sighting>> e : clues.entrySet()) {
            if (!shares(group, e.getKey(), allies)) {
                continue;
            }
            Sighting s = e.getValue().get(player);
            if (s == null || now - s.tick() > maxAge || (!withDismissed && isDismissed(e.getKey(), s))) {
                continue;
            }
            if (s.replaces(best)) {
                best = s;
            }
        }
        return best;
    }

    private boolean isDismissed(String groupKey, Sighting s) {
        Map<UUID, Long> m = dismissed.get(groupKey);
        Long upTo = m == null ? null : m.get(s.player());
        return upTo != null && s.tick() <= upTo;
    }

    /**
     * A bot of {@code group} got to the clue and the player wasn't there: the clues up to {@code upToTick} are no longer
     * followed up by the whole team (they stay as where the player was last noticed, see {@link #last}).
     */
    public void dismiss(String group, BiPredicate<String, String> allies, UUID player, long upToTick) {
        for (String groupKey : clues.keySet()) {
            if (shares(group, groupKey, allies)) {
                dismissed.computeIfAbsent(groupKey, k -> new HashMap<>()).merge(player, upToTick, Math::max);
            }
        }
    }

    /** The game told everyone that the player went to another dimension (on {@code tick}). */
    public void announce(UUID player, long tick) {
        announced.put(player, tick);
    }

    /** A bot of {@code group} found the portal of the player's departure on {@code departureTick}. */
    public void discover(String group, UUID player, long departureTick) {
        foundPortals.computeIfAbsent(key(group), k -> new HashMap<>()).put(player, departureTick);
    }

    /** A bot of {@code group} knows the player went away through the portal of the departure on {@code departureTick}. */
    public boolean knowsDeparture(String group, BiPredicate<String, String> allies, UUID player, long departureTick) {
        Long a = announced.get(player);
        if (a != null && Math.abs(a - departureTick) <= 40) {
            return true;
        }
        for (Map.Entry<String, Map<UUID, Long>> e : foundPortals.entrySet()) {
            if (shares(group, e.getKey(), allies)) {
                Long t = e.getValue().get(player);
                if (t != null && t == departureTick) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Forgets the clues older than {@code maxAge} ticks. */
    public void purge(long now, long maxAge) {
        for (Map<UUID, Sighting> m : clues.values()) {
            Iterator<Sighting> it = m.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().tick() > maxAge) {
                    it.remove();
                }
            }
        }
        for (Map<UUID, Long> m : dismissed.values()) {
            m.values().removeIf(t -> now - t > maxAge);
        }
    }

    /** A new hunt: nothing is known. */
    public void clear() {
        clues.clear();
        dismissed.clear();
        foundPortals.clear();
        announced.clear();
    }

    private static boolean shares(String group, String otherKey, BiPredicate<String, String> allies) {
        String mine = key(group);
        return mine.equals(otherKey) || allies.test(mine, otherKey);
    }
}
