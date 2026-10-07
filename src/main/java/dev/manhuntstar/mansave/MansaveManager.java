package dev.manhuntstar.mansave;

import dev.manhuntstar.ManhuntStarPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Protetores Mansave ativos ({@code /mansave spawn|clear}). Não são salvos ao reiniciar, como no mod. */
public final class MansaveManager {

    private final ManhuntStarPlugin plugin;
    private final NamespacedKey key;
    private final List<MansaveBot> protectors = new ArrayList<>();

    public MansaveManager(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "mansave");
    }

    public List<MansaveBot> protectors() {
        return Collections.unmodifiableList(protectors);
    }

    public int spawn(Location loc, String name, int count) {
        for (int i = 0; i < count; i++) {
            Location l = loc.clone().add((Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2);
            protectors.add(new MansaveBot(plugin, l, count == 1 ? name : name + (i + 1), key));
        }
        return count;
    }

    public int clear() {
        int n = protectors.size();
        for (MansaveBot b : protectors) {
            b.remove();
        }
        protectors.clear();
        return n;
    }

    public void tick() {
        if (protectors.isEmpty()) {
            return;
        }
        protectors.removeIf(b -> {
            if (!b.isValid()) {
                b.remove();
                return true;
            }
            return false;
        });
        for (MansaveBot b : new ArrayList<>(protectors)) {
            try {
                b.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Erro no Mansave", e);
            }
        }
    }

    public boolean isOrphan(Entity e) {
        if (!e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return false;
        }
        for (MansaveBot b : protectors) {
            if (b.entity().getUniqueId().equals(e.getUniqueId())) {
                return false;
            }
        }
        return true;
    }
}
