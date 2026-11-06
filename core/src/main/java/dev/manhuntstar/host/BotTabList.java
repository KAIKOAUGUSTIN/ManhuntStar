package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;

/** The bots in the player list (TAB), as if they were players. */
public interface BotTabList {

    void add(Bot bot);

    void remove(Bot bot);
}
