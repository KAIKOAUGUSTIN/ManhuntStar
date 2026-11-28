package dev.manhuntstar.bot.world;

import dev.manhuntstar.bot.perception.PerceptionTuning;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Blocks placed by players (to recognize towers and skybases). Fixes the mod, which recorded
 * any click as a "placed block", didn't separate worlds and never forgot: here only what was
 * really placed ({@code BlockPlaceEvent}) goes in, per world, and it expires in 30 minutes.
 *
 * <p>Each column also keeps the heights of its blocks, so the tall towers (at least
 * {@link PerceptionTuning#TOWER_MIN_BLOCKS} placed blocks one on top of the other, counted from the top) are known
 * without looking through every block.
 */
public final class PlayerBlockTracker {

    private static final long TTL_MS = 30L * 60L * 1000L;
    private static final int MAX_PER_WORLD = 50_000;

    private static final class WorldBlocks {
        final Map<Long, Long> expiry = new HashMap<>();
        final Map<Long, TreeSet<Integer>> columns = new HashMap<>();
        /** The tall columns and the top of each. */
        final Map<Long, Pos> towers = new HashMap<>();
    }

    private final Map<UUID, WorldBlocks> placed = new HashMap<>();

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public void markPlaced(GameWorld world, Pos pos) {
        if (world == null) {
            return;
        }
        WorldBlocks wb = placed.computeIfAbsent(world.id(), k -> new WorldBlocks());
        if (wb.expiry.size() >= MAX_PER_WORLD) {
            purge();
            if (wb.expiry.size() >= MAX_PER_WORLD) {
                Iterator<Long> it = wb.expiry.keySet().iterator();
                List<Long> dropped = new ArrayList<>();
                for (int i = 0; i < MAX_PER_WORLD / 10 && it.hasNext(); i++) {
                    dropped.add(it.next());
                    it.remove();
                }
                for (long key : dropped) {
                    removeFromColumn(wb, Pos.unpack(key));
                }
            }
        }
        wb.expiry.put(pos.asLong(), System.currentTimeMillis() + TTL_MS);
        long col = column(pos.x(), pos.z());
        wb.columns.computeIfAbsent(col, k -> new TreeSet<>()).add(pos.y());
        updateTower(wb, col, pos.x(), pos.z());
    }

    public void unmark(GameWorld world, Pos pos) {
        WorldBlocks wb = world == null ? null : placed.get(world.id());
        if (wb != null && wb.expiry.remove(pos.asLong()) != null) {
            removeFromColumn(wb, pos);
        }
    }

    public boolean isPlayerPlaced(GameWorld world, Pos pos) {
        WorldBlocks wb = world == null ? null : placed.get(world.id());
        if (wb == null) {
            return false;
        }
        Long until = wb.expiry.get(pos.asLong());
        return until != null && until > System.currentTimeMillis();
    }

    /** The tops of the tall towers of players in this world. */
    public Collection<Pos> towers(GameWorld world) {
        WorldBlocks wb = placed.get(world.id());
        return wb == null ? List.of() : wb.towers.values();
    }

    public void purge() {
        long now = System.currentTimeMillis();
        for (WorldBlocks wb : placed.values()) {
            Iterator<Map.Entry<Long, Long>> it = wb.expiry.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Long> e = it.next();
                if (e.getValue() <= now) {
                    it.remove();
                    removeFromColumn(wb, Pos.unpack(e.getKey()));
                }
            }
        }
    }

    public void clear() {
        placed.clear();
    }

    private static void removeFromColumn(WorldBlocks wb, Pos pos) {
        long col = column(pos.x(), pos.z());
        TreeSet<Integer> ys = wb.columns.get(col);
        if (ys == null) {
            return;
        }
        ys.remove(pos.y());
        if (ys.isEmpty()) {
            wb.columns.remove(col);
        }
        updateTower(wb, col, pos.x(), pos.z());
    }

    /** Counts the placed blocks one on top of the other from the column's top down. */
    private static void updateTower(WorldBlocks wb, long col, int x, int z) {
        TreeSet<Integer> ys = wb.columns.get(col);
        if (ys == null || ys.size() < PerceptionTuning.TOWER_MIN_BLOCKS) {
            wb.towers.remove(col);
            return;
        }
        int top = ys.last();
        int run = 0;
        for (int y = top; ys.contains(y); y--) {
            run++;
        }
        if (run >= PerceptionTuning.TOWER_MIN_BLOCKS) {
            wb.towers.put(col, new Pos(x, top, z));
        } else {
            wb.towers.remove(col);
        }
    }
}
