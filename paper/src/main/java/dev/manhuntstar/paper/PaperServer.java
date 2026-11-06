package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import java.util.UUID;
import org.bukkit.Bukkit;

/** The Bukkit server as a core {@link GameServer}. */
public final class PaperServer implements GameServer {

    @Override
    public int currentTick() {
        return Bukkit.getCurrentTick();
    }

    @Override
    public GameWorld world(UUID id) {
        return PaperWorld.of(Bukkit.getWorld(id));
    }
}
