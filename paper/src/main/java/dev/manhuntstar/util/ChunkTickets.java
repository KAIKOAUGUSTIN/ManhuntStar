package dev.manhuntstar.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Tickets de chunk do plugin com contagem de referências. O Paper guarda um único ticket por chunk
 * para o plugin inteiro: se dois bots seguram o mesmo chunk e um deles se afasta, a remoção soltava
 * o chunk do outro, que congelava (com o cavalo dele) quando não havia jogador perto.
 */
public final class ChunkTickets {

    private final Plugin plugin;
    private final Map<UUID, Map<Long, Integer>> counts = new HashMap<>();

    public ChunkTickets(Plugin plugin) {
        this.plugin = plugin;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void acquire(World w, long key) {
        Map<Long, Integer> m = counts.computeIfAbsent(w.getUID(), k -> new HashMap<>());
        if (m.merge(key, 1, Integer::sum) == 1) {
            w.addPluginChunkTicket((int) (key >> 32), (int) key, plugin);
        }
    }

    public void release(World w, long key) {
        Map<Long, Integer> m = counts.get(w.getUID());
        if (m == null) {
            return;
        }
        Integer n = m.get(key);
        if (n == null) {
            return;
        }
        if (n <= 1) {
            m.remove(key);
            w.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
        } else {
            m.put(key, n - 1);
        }
    }

    /** Desligamento: solta tudo. */
    public void releaseAll() {
        for (World w : Bukkit.getWorlds()) {
            w.removePluginChunkTickets(plugin);
        }
        counts.clear();
    }
}
