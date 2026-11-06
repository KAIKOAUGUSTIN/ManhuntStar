package dev.manhuntstar.platform;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** The server, as the AI sees it. */
public interface GameServer {

    /** The server's tick counter. */
    int currentTick();

    /** A loaded world, or null. */
    GameWorld world(UUID id);

    /** A loaded world by name, or null. */
    GameWorld world(String name);

    List<GameWorld> worlds();

    Collection<? extends GamePlayer> onlinePlayers();

    /** An online player, or null. */
    GamePlayer player(UUID id);

    /** An online player by exact name (any case), or null. */
    GamePlayer player(String name);

    /** A loaded entity in any world, or null. */
    GameEntity entity(UUID id);
}
