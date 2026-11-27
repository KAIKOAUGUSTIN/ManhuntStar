package dev.manhuntstar.bot.perception;

import dev.manhuntstar.platform.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who already went to the Nether or the End in this match. With the advancement that announces it taken out of the
 * server (a datapack, the server's settings), the first trip of each player to each of them in the match tells the
 * bots in its place.
 */
public final class FirstTrips {

    private final Set<String> done = new HashSet<>();

    /** The player's first trip to this dimension in the match: true once, then false. */
    public boolean first(UUID player, Dimension dimension) {
        return done.add(player + ":" + dimension.name());
    }

    public void clear() {
        done.clear();
    }

    /** For saving with the match. */
    public List<String> save() {
        return new ArrayList<>(done);
    }

    public void load(Collection<String> saved) {
        done.clear();
        done.addAll(saved);
    }

    public boolean isEmpty() {
        return done.isEmpty();
    }
}
