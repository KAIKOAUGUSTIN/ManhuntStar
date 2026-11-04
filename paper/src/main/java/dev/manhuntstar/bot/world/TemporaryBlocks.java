package dev.manhuntstar.bot.world;

import dev.manhuntstar.pathfinding.PathService;
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
 * Blocos que os bots colocam só por um tempo: folhas da ponte na água, água do MLG e do fogo, lava
 * do balde e a TNT da emboscada antes de acender. Quem coloca normalmente recolhe o bloco; este
 * registro garante que ele some mesmo se o bot morrer, for removido ou trocar de mundo antes, e
 * desfaz tudo ao desligar o servidor.
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

    /** O bloco {@code material} em {@code p} volta a ser ar daqui a {@code ticks}, se ainda estiver lá. */
    public void add(World w, Pos p, Material material, int ticks) {
        entries.put(new Key(w.getUID(), p), new Entry(material, (long) Bukkit.getCurrentTick() + ticks));
    }

    /** Quem colocou já recolheu o bloco. */
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
                // espera o chunk voltar em vez de carregá-lo só para isso
                continue;
            }
            it.remove();
            revert(w, e.getKey().pos(), e.getValue().material(), true);
        }
    }

    /** Desligamento: desfaz tudo na hora. */
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
        Block b = p.block(w);
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
            ps.invalidate(w, p);
        }
    }
}
