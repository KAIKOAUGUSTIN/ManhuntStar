package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.DamageInfo;
import net.kyori.adventure.text.Component;

/** The death message of a bot, in the game's format. */
public interface DeathMessageSource {

    Component build(Bot bot, DamageInfo source);
}
