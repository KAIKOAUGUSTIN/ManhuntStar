package dev.manhuntstar.paper;

import dev.manhuntstar.api.BotEvents;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.host.BotEventSink;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;
import java.util.OptionalDouble;
import org.bukkit.Bukkit;

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

    @Override
    public OptionalDouble botDamagedByPlayer(Bot bot, GamePlayer player, double damage) {
        BotEvents.BotDamageByPlayerEvent api = new BotEvents.BotDamageByPlayerEvent(bot, PaperEntities.bukkit(player), damage);
        Bukkit.getPluginManager().callEvent(api);
        return api.isCancelled() ? OptionalDouble.empty() : OptionalDouble.of(api.getDamage());
    }

    @Override
    public boolean botFallDamage(Bot bot, double damage) {
        BotEvents.BotFallDamageEvent api = new BotEvents.BotFallDamageEvent(bot, damage);
        Bukkit.getPluginManager().callEvent(api);
        return !api.isCancelled();
    }
}
