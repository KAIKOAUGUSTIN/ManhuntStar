package dev.manhuntstar.command;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;

/** {@code /mansave spawn [nome] [quantidade]} e {@code /mansave clear} (no mod o comando existia mas não era registrado). */
public final class MansaveCommand {

    private final ManhuntStarPlugin plugin;

    public MansaveCommand(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mansave")
                .requires(src -> src.getSender().hasPermission("manhuntstar.admin"))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, "Mansave", 1))
                        .then(Commands.argument("nome", StringArgumentType.word())
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "nome"), 1))
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "nome"), IntegerArgumentType.getInteger(ctx, "quantidade"))))))
                .then(Commands.literal("clear").executes(ctx -> {
                    int n = plugin.mansave().clear();
                    plugin.messages().send(ctx.getSource().getSender(), "mansave.removidos", Messages.p("quantidade", n));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    private int spawn(CommandContext<CommandSourceStack> ctx, String name, int count) {
        Location loc = ctx.getSource().getLocation();
        if (loc.getWorld() == null) {
            plugin.messages().send(ctx.getSource().getSender(), "comando.sem-local");
            return 0;
        }
        int n = plugin.mansave().spawn(loc, name, count);
        plugin.messages().send(ctx.getSource().getSender(), "mansave.criados", Messages.p("quantidade", n), Messages.p("nome", name));
        return Command.SINGLE_SUCCESS;
    }
}
