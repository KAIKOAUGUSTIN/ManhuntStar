package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import java.util.Collection;

/** The glow (outline through walls) on the bots, by group role. */
public interface BotGlow {

    /** Puts the bot's body in its glow team (after it spawns or changes group). */
    void apply(Bot bot);

    void remove(Bot bot);

    /** {@link #apply} for each bot (after the glow options change). */
    void applyAll(Collection<Bot> bots);
}
