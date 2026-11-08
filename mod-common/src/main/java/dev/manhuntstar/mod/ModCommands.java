package dev.manhuntstar.mod;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.manhuntstar.command.CommandPlatform;
import dev.manhuntstar.mod.mixin.CommandSourceStackAccessor;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Place;
import net.kyori.adventure.audience.Audience;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Minecraft's command source and argument types for core's commands (what Paper's command API gives the plugin). */
public final class ModCommands implements CommandPlatform<CommandSourceStack> {

    private final CommandBuildContext context;

    /** {@code context} gives the item argument the game's registries (the loaders hand it out with the command event). */
    public ModCommands(CommandBuildContext context) {
        this.context = context;
    }

    @Override
    public Audience sender(CommandSourceStack source) {
        return ModServer.get().audiences().audience(source);
    }

    /** The player whose own command source typed it (each player has one; {@code execute as} keeps it). */
    @Override
    public GamePlayer playerSender(CommandSourceStack source) {
        CommandSource typed = ((CommandSourceStackAccessor) source).manhuntstar$source();
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            if (p.commandSource() == typed) {
                return ModEntities.of(p);
            }
        }
        return null;
    }

    @Override
    public GamePlayer playerExecutor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer p ? ModEntities.of(p) : null;
    }

    private static Place place(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        Vec2 rot = source.getRotation();
        return new Place(ModWorld.of(source.getLevel()), pos.x, pos.y, pos.z, rot.y, rot.x);
    }

    @Override
    public Place location(CommandSourceStack source) {
        return place(source);
    }

    @Override
    public Place executorLocation(CommandSourceStack source) {
        return source.getEntity() != null ? place(source) : null;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, String permission) {
        return ModPermissions.has(source, permission);
    }

    @Override
    public ArgumentType<?> playerArgument() {
        return EntityArgument.player();
    }

    @Override
    public GamePlayer player(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ModEntities.of(EntityArgument.getPlayer(ctx, name));
    }

    @Override
    public ArgumentType<?> positionArgument() {
        return Vec3Argument.vec3(false);
    }

    @Override
    public Place position(CommandContext<CommandSourceStack> ctx, String name) {
        Vec3 p = Vec3Argument.getVec3(ctx, name);
        return new Place(ModWorld.of(ctx.getSource().getLevel()), p.x, p.y, p.z);
    }

    @Override
    public ArgumentType<?> itemArgument() {
        return ItemArgument.item(context);
    }

    @Override
    public GameItem item(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return ModItem.of(ItemArgument.getItem(ctx, name).createItemStack(1));
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
