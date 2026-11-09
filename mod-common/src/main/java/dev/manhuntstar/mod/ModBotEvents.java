package dev.manhuntstar.mod;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.host.BotEventSink;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;
import java.util.OptionalDouble;

/** The mods have no event API for other mods (the plugin's are Bukkit events), so nothing changes the bots' events. */
public final class ModBotEvents implements BotEventSink {

    @Override
    public void botDied(Bot bot, DamageInfo source) {
    }

    @Override
    public void botKilledByPlayer(Bot bot, GamePlayer player) {
    }

    @Override
    public OptionalDouble botDamagedByPlayer(Bot bot, GamePlayer player, double damage) {
        return OptionalDouble.of(damage);
    }

    @Override
    public boolean botFallDamage(Bot bot, double damage) {
        return true;
    }
}
