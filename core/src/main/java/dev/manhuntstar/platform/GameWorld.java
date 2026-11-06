package dev.manhuntstar.platform;

import java.util.UUID;

/** A world (dimension) of the server, as the AI sees it. */
public interface GameWorld {

    UUID id();

    String name();

    /** Lowest block Y. */
    int minHeight();

    /** One above the highest block Y. */
    int maxHeight();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** A copy of a loaded chunk (server thread only). */
    BlockSnapshot chunkSnapshot(int chunkX, int chunkZ);
}
