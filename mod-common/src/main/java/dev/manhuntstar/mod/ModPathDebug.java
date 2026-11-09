package dev.manhuntstar.mod;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.host.PathDebug;
import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The A* path in particles for whoever has the admin permission ({@code pathfinding.debug-particles} option):
 * green = path, red = blocks to dig, blue = blocks to place.
 */
public final class ModPathDebug implements PathDebug {

    private static final DustParticleOptions PATH = new DustParticleOptions(0x00FF00, 0.8f);
    private static final DustParticleOptions DONE = new DustParticleOptions(0x507850, 0.6f);
    private static final DustParticleOptions MINE = new DustParticleOptions(0xFF0000, 1.2f);
    private static final DustParticleOptions PLACE = new DustParticleOptions(0x00FFFF, 1.2f);

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

    @Override
    public void clearAll() {
        paths.clear();
    }

    @Override
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
        for (ServerPlayer p : ModServer.get().vanilla().getPlayerList().getPlayers()) {
            if (!ModEntities.of(p).hasPermission("manhuntstar.admin")) {
                continue;
            }
            ServerLevel level = p.level();
            UUID world = ModWorld.of(level).id();
            for (Snapshot s : paths.values()) {
                if (!s.world().equals(world)) {
                    continue;
                }
                for (int i = 0; i < s.path().size(); i++) {
                    Pos n = s.path().get(i);
                    if (p.distanceToSqr(n.x(), n.y(), n.z()) > 64 * 64) {
                        continue;
                    }
                    level.sendParticles(p, i < s.index() ? DONE : PATH, true, false, n.x() + 0.5, n.y() + 0.1, n.z() + 0.5, 1, 0, 0, 0, 0);
                }
                for (Pos m : s.mine()) {
                    level.sendParticles(p, MINE, true, false, m.x() + 0.5, m.y() + 0.5, m.z() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                }
                for (Pos m : s.place()) {
                    level.sendParticles(p, PLACE, true, false, m.x() + 0.5, m.y() + 0.5, m.z() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }
}
