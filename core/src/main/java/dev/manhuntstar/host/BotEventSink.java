package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;
import java.util.OptionalDouble;

/** Tells other plugins or mods what happened to the bots (the public API's events). */
public interface BotEventSink {

    void botDied(Bot bot, DamageInfo source);

    void botKilledByPlayer(Bot bot, GamePlayer player);

    /** A player is about to damage the bot: the damage after the listeners, or empty when one cancelled it. */
    OptionalDouble botDamagedByPlayer(Bot bot, GamePlayer player, double damage);

    /** The bot is about to take fall damage: false when a listener cancelled it. */
    boolean botFallDamage(Bot bot, double damage);
}
