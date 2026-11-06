package dev.manhuntstar.platform;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/** The server, as the AI sees it. */
public interface GameServer {

    /** The server's tick counter. */
    int currentTick();

    /** A loaded world, or null. */
    GameWorld world(UUID id);

    /** A loaded world by name, or null. */
    GameWorld world(String name);

    /** The main world (the first one the server loads). */
    GameWorld defaultWorld();

    List<GameWorld> worlds();

    Collection<? extends GamePlayer> onlinePlayers();

    /** An online player, or null. */
    GamePlayer player(UUID id);

    /** An online player by exact name (any case), or null. */
    GamePlayer player(String name);

    /** A loaded entity in any world, or null. */
    GameEntity entity(UUID id);

    /** A new stack of items (see {@link GameItem#of}). */
    GameItem newItem(ItemType type, int amount);

    /** A new empty stack (see {@link GameItem#empty}). */
    GameItem emptyItem();

    /** A firework rocket item that flies {@code flight} (1 to 3) and explodes with this shape and colors (0xRRGGBB). */
    GameItem newFireworkRocket(int flight, FireworkShape shape, int... colors);

    /** A stack written by {@link GameItem#serialize()}. */
    GameItem deserializeItem(byte[] data);

    /** Sends a message to every player and to the console. */
    void broadcast(Component message);

    /** Runs a task on the main thread in the next tick. */
    void runNextTick(Runnable task);

    /** Runs a task on the main thread after a number of ticks. */
    void runLater(Runnable task, long delayTicks);
}
