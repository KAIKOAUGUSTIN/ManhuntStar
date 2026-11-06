package dev.manhuntstar.paper;

import dev.manhuntstar.api.BotEvents;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.host.BotEventSink;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;

/** The bots' events as Bukkit events ({@link BotEvents}), for other plugins. */
public final class PaperBotEvents implements BotEventSink {

    @Override
    public void botDied(Bot bot, DamageInfo source) {
        BotEvents.fireDeath(bot, source);
    }

    @Override
    public void botKilledByPlayer(Bot bot, GamePlayer player) {
        BotEvents.fireKilledByPlayer(bot, player);
    }
}
