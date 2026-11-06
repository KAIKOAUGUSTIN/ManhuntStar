package dev.manhuntstar.util;

import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The plugin's chunk tickets with reference counting. Paper keeps a single ticket per chunk for the
 * whole plugin: if two bots held the same chunk and one of them moved away, the removal released the
 * other's chunk, which froze (with its horse) when no player was near.
 */
public final class ChunkTickets {

    private final Map<UUID, Map<Long, Integer>> counts = new HashMap<>();

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void acquire(GameWorld w, long key) {
        Map<Long, Integer> m = counts.computeIfAbsent(w.id(), k -> new HashMap<>());
        if (m.merge(key, 1, Integer::sum) == 1) {
            w.addChunkTicket((int) (key >> 32), (int) key);
        }
    }

    public void release(GameWorld w, long key) {
        Map<Long, Integer> m = counts.get(w.id());
        if (m == null) {
            return;
        }
        Integer n = m.get(key);
        if (n == null) {
            return;
        }
        if (n <= 1) {
            m.remove(key);
            w.removeChunkTicket((int) (key >> 32), (int) key);
        } else {
            m.put(key, n - 1);
        }
    }

    /** Shutdown: releases everything. */
    public void releaseAll() {
        for (GameWorld w : Platform.server().worlds()) {
            w.removeAllChunkTickets();
        }
        counts.clear();
    }
}
