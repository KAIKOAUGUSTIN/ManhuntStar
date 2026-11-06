package dev.manhuntstar.bot.world;

import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.SoundGroup;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Blocks the bots place only for a while: leaves for the water bridge, MLG and fire water, bucket
 * lava and the ambush TNT before it's lit. Whoever places it normally picks the block up; this
 * registry makes sure it disappears even if the bot dies, is removed or changes worlds first, and
 * undoes everything when the server shuts down.
 */
public final class TemporaryBlocks {

    private record Key(UUID world, Pos pos) {
    }

    private record Entry(BlockType material, long expiresAt) {
    }

    private final GameServer server;
    private final Supplier<PathService> paths;
    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    public TemporaryBlocks(GameServer server, Supplier<PathService> paths) {
        this.server = server;
        this.paths = paths;
    }

    /** The {@code material} block at {@code p} goes back to air in {@code ticks}, if it's still there. */
    public void add(GameWorld w, Pos p, BlockType material, int ticks) {
        entries.put(new Key(w.id(), p), new Entry(material, (long) server.currentTick() + ticks));
    }

    /** Whoever placed it has already picked the block up. */
    public void remove(GameWorld w, Pos p) {
        entries.remove(new Key(w.id(), p));
    }

    public void tick() {
        long now = server.currentTick();
        if (entries.isEmpty() || now % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Entry> e = it.next();
            if (now < e.getValue().expiresAt()) {
                continue;
            }
            GameWorld w = server.world(e.getKey().world());
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
            revert(server.world(e.getKey().world()), e.getKey().pos(), e.getValue().material(), false);
        }
        entries.clear();
    }

    private void revert(GameWorld w, Pos p, BlockType material, boolean sound) {
        if (w == null) {
            return;
        }
        GameBlock b = w.block(p);
        if (b.type() != material) {
            return;
        }
        SoundGroup sounds = b.soundGroup();
        b.setType(Blocks.AIR);
        if (sound && material.is(BlockTrait.SOLID)) {
            w.playSound(Vec3.center(p), sounds.breakSound(), SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
        PathService ps = paths.get();
        if (ps != null) {
            ps.invalidate(w, p);
        }
    }
}
