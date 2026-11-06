package dev.manhuntstar.debug;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.host.PathDebug;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The A* path in particles for whoever has the admin permission ({@code pathfinding.debug-particles} option):
 * green = path, red = blocks to dig, blue = blocks to place. In the mod this was a client-side drawing
 * that only worked in single player.
 */
public final class DebugRenderer implements PathDebug {

    private static final Particle.DustOptions PATH = new Particle.DustOptions(Color.LIME, 0.8f);
    private static final Particle.DustOptions DONE = new Particle.DustOptions(Color.fromRGB(80, 120, 80), 0.6f);
    private static final Particle.DustOptions MINE = new Particle.DustOptions(Color.RED, 1.2f);
    private static final Particle.DustOptions PLACE = new Particle.DustOptions(Color.AQUA, 1.2f);

    private record Snapshot(UUID world, List<Pos> path, int index, Set<Pos> mine, Set<Pos> place) {
    }

    private final Map<UUID, Snapshot> paths = new HashMap<>();

    @Override
    public void update(Bot bot, List<Pos> path, int index, Set<Pos> mine, Set<Pos> place) {
        if (bot.world() == null) {
            return;
        }
        paths.put(bot.id(), new Snapshot(bot.world().id(), List.copyOf(path), index, Set.copyOf(mine), Set.copyOf(place)));
    }

    @Override
    public void clear(UUID botId) {
        paths.remove(botId);
    }

    public void clearAll() {
        paths.clear();
    }

    public void tick(int now, boolean enabled) {
        if (!enabled) {
            if (!paths.isEmpty()) {
                paths.clear();
            }
            return;
        }
        if (now % 5 != 0 || paths.isEmpty()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("manhuntstar.admin")) {
                continue;
            }
            World w = p.getWorld();
            for (Snapshot s : paths.values()) {
                if (!s.world().equals(w.getUID())) {
                    continue;
                }
                for (int i = 0; i < s.path().size(); i++) {
                    Pos n = s.path().get(i);
                    if (p.getLocation().distanceSquared(Locations.location(n, w)) > 64 * 64) {
                        continue;
                    }
                    p.spawnParticle(Particle.DUST, n.x() + 0.5, n.y() + 0.1, n.z() + 0.5, 1, 0, 0, 0, 0, i < s.index() ? DONE : PATH);
                }
                for (Pos m : s.mine()) {
                    p.spawnParticle(Particle.DUST, m.x() + 0.5, m.y() + 0.5, m.z() + 0.5, 2, 0.2, 0.2, 0.2, 0, MINE);
                }
                for (Pos m : s.place()) {
                    p.spawnParticle(Particle.DUST, m.x() + 0.5, m.y() + 0.5, m.z() + 0.5, 2, 0.2, 0.2, 0.2, 0, PLACE);
                }
            }
        }
    }
}
