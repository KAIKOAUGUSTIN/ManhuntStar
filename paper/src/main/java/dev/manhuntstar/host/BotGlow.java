package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;

/** The glow (outline through walls) on the bots, by group role. */
public interface BotGlow {

    /** Puts the bot's body in its glow team (after it spawns or changes group). */
    void apply(Bot bot);

    void remove(Bot bot);
}
