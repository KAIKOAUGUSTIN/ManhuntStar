package dev.manhuntstar.paper;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.manhuntstar.command.CommandPlatform;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.util.Locations;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Paper's command source and argument types for core's commands. */
public final class PaperCommands implements CommandPlatform<CommandSourceStack> {

    @Override
    public Audience sender(CommandSourceStack source) {
        return source.getSender();
    }

    @Override
    public GamePlayer playerSender(CommandSourceStack source) {
        return source.getSender() instanceof Player p ? PaperEntities.of(p) : null;
    }

    @Override
    public GamePlayer playerExecutor(CommandSourceStack source) {
        return source.getExecutor() instanceof Player p ? PaperEntities.of(p) : null;
    }

    @Override
    public Place location(CommandSourceStack source) {
        return Locations.place(source.getLocation());
    }

    @Override
    public Place executorLocation(CommandSourceStack source) {
        return source.getExecutor() instanceof Entity ? Locations.place(source.getLocation()) : null;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission(permission);
    }

    @Override
    public ArgumentType<?> playerArgument() {
        return ArgumentTypes.player();
    }

    @Override
    public GamePlayer player(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return PaperEntities.of(ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst());
    }

    @Override
    public ArgumentType<?> positionArgument() {
        return ArgumentTypes.finePosition();
    }

    @Override
    public Place position(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        Location base = ctx.getSource().getLocation();
        return Locations.place(ctx.getArgument(name, FinePositionResolver.class).resolve(ctx.getSource()).toLocation(base.getWorld()));
    }

    @Override
    public ArgumentType<?> itemArgument() {
        return ArgumentTypes.itemStack();
    }

    @Override
    public GameItem item(CommandContext<CommandSourceStack> ctx, String name) {
        return PaperItems.of(ctx.getArgument(name, ItemStack.class));
    }
}
