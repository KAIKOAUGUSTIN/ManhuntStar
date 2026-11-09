package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.GamePlayer;

/** The bots in the player list (TAB), as if they were players. */
public interface BotTabList {

    void add(Bot bot);

    void remove(Bot bot);

    /** Sends the bot again (after its skin changes). */
    void refresh(Bot bot);

    /** Sends every bot to a player who just joined. */
    void sendAll(GamePlayer player);

    /** Puts the bots in or takes them out of the list after the "tab-list" option changes. */
    void resync();

    /** Called every tick (the bots' ping changes now and then). */
    void tick(int now);

    /** On shutdown: takes every bot out of the list. */
    void clear();
}
