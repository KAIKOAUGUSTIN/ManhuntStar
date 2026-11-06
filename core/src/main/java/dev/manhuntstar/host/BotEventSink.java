package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;

/** Tells other plugins or mods what happened to the bots (the public API's events). */
public interface BotEventSink {

    void botDied(Bot bot, DamageInfo source);

    void botKilledByPlayer(Bot bot, GamePlayer player);
}
