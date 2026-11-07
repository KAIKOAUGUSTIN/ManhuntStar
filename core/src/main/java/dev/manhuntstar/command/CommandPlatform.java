package dev.manhuntstar.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Place;
import net.kyori.adventure.audience.Audience;

/**
 * What the commands need from the platform's command source {@code S} (who typed it, from where) and the
 * argument types that only the platform has (player selector, position with {@code ~ ~ ~}, item).
 */
public interface CommandPlatform<S> {

    /** Who gets the replies: the one who typed the command. */
    Audience sender(S source);

    /** The one who typed it, if a player; null for the console and command blocks. */
    GamePlayer playerSender(S source);

    /** The player running it (who {@code execute as} picked, or the sender); null if not a player. */
    GamePlayer playerExecutor(S source);

    /** Where it runs: the executor's place, or the world spawn on the console. */
    Place location(S source);

    /** Where the entity running it is; null when no entity runs it (the console). */
    Place executorLocation(S source);

    boolean hasPermission(S source, String permission);

    /** One online player, by name or selector. */
    ArgumentType<?> playerArgument();

    GamePlayer player(CommandContext<S> ctx, String name) throws CommandSyntaxException;

    /** A position ({@code x y z}, with {@code ~} relative to where it runs). */
    ArgumentType<?> positionArgument();

    /** The position, in the world where it runs, facing south and level. */
    Place position(CommandContext<S> ctx, String name) throws CommandSyntaxException;

    /** An item, with components ({@code diamond_sword[enchantments={sharpness:5}]}). */
    ArgumentType<?> itemArgument();

    GameItem item(CommandContext<S> ctx, String name);
}
