package dev.manhuntstar.bot.world;

import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.WorldUtil;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Blocks the bots place only for a while: leaves for the water bridge, MLG and fire water, bucket
 * lava and the ambush TNT before it's lit. Whoever places it normally picks the block up; this
 * registry makes sure it disappears even if the bot dies, is removed or changes worlds first, and
 * undoes everything when the server shuts down.
 */
public final class TemporaryBlocks {

    private record Key(UUID world, Pos pos) {
    }

    private record Entry(Material material, long expiresAt) {
    }

    private final Supplier<PathService> paths;
    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    public TemporaryBlocks(Supplier<PathService> paths) {
        this.paths = paths;
    }

    /** The {@code material} block at {@code p} goes back to air in {@code ticks}, if it's still there. */
    public void add(World w, Pos p, Material material, int ticks) {
        entries.put(new Key(w.getUID(), p), new Entry(material, (long) Bukkit.getCurrentTick() + ticks));
    }

    /** Whoever placed it has already picked the block up. */
    public void remove(World w, Pos p) {
        entries.remove(new Key(w.getUID(), p));
    }

    public void tick() {
        long now = Bukkit.getCurrentTick();
        if (entries.isEmpty() || now % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Entry> e = it.next();
            if (now < e.getValue().expiresAt()) {
                continue;
            }
            World w = Bukkit.getWorld(e.getKey().world());
            if (w != null && !WorldUtil.isLoaded(w, e.getKey().pos())) {
                // waits for the chunk to come back instead of loading it just for this
                continue;
            }
            it.remove();
            revert(w, e.getKey().pos(), e.getValue().material(), true);
        }
    }

    /** Shutdown: undoes everything right away. */
    public void revertAll() {
        for (Map.Entry<Key, Entry> e : entries.entrySet()) {
            revert(Bukkit.getWorld(e.getKey().world()), e.getKey().pos(), e.getValue().material(), false);
        }
        entries.clear();
    }

    private void revert(World w, Pos p, Material material, boolean sound) {
        if (w == null) {
            return;
        }
        Block b = Locations.block(p, w);
        if (b.getType() != material) {
            return;
        }
        BlockData data = b.getBlockData();
        b.setType(Material.AIR);
        if (sound && material.isSolid()) {
            w.playSound(b.getLocation().add(0.5, 0.5, 0.5), data.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
        PathService ps = paths.get();
        if (ps != null) {
            ps.invalidate(PaperWorld.of(w), p);
        }
    }
}
