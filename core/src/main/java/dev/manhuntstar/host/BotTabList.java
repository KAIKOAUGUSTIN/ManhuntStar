package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.GamePlayer;

/** The bots in the player list (TAB), as if they were players. */
public interface BotTabList {

    void add(Bot bot);

    void remove(Bot bot);

    /** Sends every bot to a player who just joined. */
    void sendAll(GamePlayer player);
}
