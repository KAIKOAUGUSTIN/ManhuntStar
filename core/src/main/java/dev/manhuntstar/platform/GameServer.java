package dev.manhuntstar.platform;

import java.util.UUID;

/** The server, as the AI sees it. */
public interface GameServer {

    /** The server's tick counter. */
    int currentTick();

    /** A loaded world, or null. */
    GameWorld world(UUID id);
}
