package dev.manhuntstar.host;

import dev.manhuntstar.bot.SkinData;
import java.util.concurrent.CompletableFuture;

/** Finds the skin for a bot's name (a player's skin, or a generated one). */
public interface SkinSource {

    /** The skin, or null when there is none; completes off the main thread. */
    CompletableFuture<SkinData> resolve(String name);
}
