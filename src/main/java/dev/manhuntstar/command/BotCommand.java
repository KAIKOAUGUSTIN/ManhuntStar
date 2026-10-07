package dev.manhuntstar.command;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.bot.Modes.GlowColor;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.util.Vec3;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /bot}: mesma árvore do mod (create, spawn, remove, list, give, armor, target, settings,
 * gameplay, group, gamerule, tp, tpall, setspawnpoint, execute, deleteall) mais menu, start, skin,
 * info e reload.
 */
public final class BotCommand {

    private final ManhuntStarPlugin plugin;

    public BotCommand(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private BotManager manager() {
        return plugin.botManager();
    }

    private Settings settings() {
        return plugin.settings();
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private SuggestionProvider<CommandSourceStack> botNames() {
        return (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (Bot bot : manager().allBots()) {
                if (bot.name().toLowerCase(Locale.ROOT).startsWith(rem)) {
                    b.suggest(bot.name());
                }
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> groupNames() {
        return (ctx, b) -> {
            for (BotGroup g : manager().groups()) {
                if (g.name().toLowerCase(Locale.ROOT).startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
                    b.suggest(g.name());
                }
            }
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> words(String... options) {
        return (ctx, b) -> {
            for (String o : options) {
                if (o.startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
                    b.suggest(o);
                }
            }
            return b.buildFuture();
        };
    }

    private static final String[] DIFFICULTIES = {"difficulty.easy", "difficulty.medium", "difficulty.hard", "difficulty.terminator", "facil", "medio", "dificil", "terminator"};

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot")
                .requires(src -> src.getSender().hasPermission("manhuntstar.admin"))
                .executes(ctx -> help(ctx));

        root.then(Commands.literal("create")
                .then(Commands.argument("nome", StringArgumentType.string())
                        .executes(ctx -> create(ctx, 1, null, null, 0, 0))
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "quantidade"), null, null, 0, 0))
                                .then(Commands.argument("dificuldade", StringArgumentType.word()).suggests(words(DIFFICULTIES))
                                        .executes(ctx -> create(ctx, amount(ctx), diff(ctx), null, 0, 0))
                                        .then(Commands.literal("skin").then(Commands.argument("skin", StringArgumentType.string())
                                                .executes(ctx -> create(ctx, amount(ctx), diff(ctx), StringArgumentType.getString(ctx, "skin"), 0, 0))
                                                .then(Commands.argument("fase", IntegerArgumentType.integer(1, 10))
                                                        .executes(ctx -> create(ctx, amount(ctx), diff(ctx), StringArgumentType.getString(ctx, "skin"), IntegerArgumentType.getInteger(ctx, "fase"), 0))
                                                        .then(Commands.argument("agressividade", IntegerArgumentType.integer(0, 10))
                                                                .executes(ctx -> create(ctx, amount(ctx), diff(ctx), StringArgumentType.getString(ctx, "skin"),
                                                                        IntegerArgumentType.getInteger(ctx, "fase"), IntegerArgumentType.getInteger(ctx, "agressividade")))))))
                                        .then(Commands.argument("fase", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> create(ctx, amount(ctx), diff(ctx), null, IntegerArgumentType.getInteger(ctx, "fase"), 0))
                                                .then(Commands.argument("agressividade", IntegerArgumentType.integer(0, 10))
                                                        .executes(ctx -> create(ctx, amount(ctx), diff(ctx), null, IntegerArgumentType.getInteger(ctx, "fase"),
                                                                IntegerArgumentType.getInteger(ctx, "agressividade")))))))));

        root.then(Commands.literal("spawn")
                .then(Commands.argument("nome", StringArgumentType.string())
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 100))
                                .then(Commands.argument("dificuldade", StringArgumentType.word()).suggests(words(DIFFICULTIES))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> spawnAt(ctx, 0, 0))
                                                                .then(Commands.argument("fase", IntegerArgumentType.integer(1, 10))
                                                                        .executes(ctx -> spawnAt(ctx, IntegerArgumentType.getInteger(ctx, "fase"), 0))
                                                                        .then(Commands.argument("agressividade", IntegerArgumentType.integer(0, 10))
                                                                                .executes(ctx -> spawnAt(ctx, IntegerArgumentType.getInteger(ctx, "fase"),
                                                                                        IntegerArgumentType.getInteger(ctx, "agressividade"))))))))))));

        root.then(Commands.literal("remove")
                .then(Commands.argument("nome", StringArgumentType.string()).suggests(botNames()).executes(this::remove)));
        root.then(Commands.literal("list").executes(this::list));
        root.then(Commands.literal("info").then(Commands.argument("nome", StringArgumentType.string()).suggests(botNames()).executes(this::info)));
        root.then(Commands.literal("give").then(Commands.argument("item", ArgumentTypes.itemStack()).executes(this::give)));
        root.then(Commands.literal("armor").then(Commands.argument("tier", StringArgumentType.word())
                .suggests(words("leather", "chainmail", "iron", "gold", "diamond", "netherite")).executes(this::armor)));
        root.then(Commands.literal("target").then(Commands.argument("jogador", ArgumentTypes.player()).executes(this::target)));
        root.then(Commands.literal("cleartarget").executes(ctx -> {
            manager().setForcedTarget(null);
            msg().send(sender(ctx), "comando.alvo-limpo");
            return Command.SINGLE_SUCCESS;
        }));

        root.then(settingsNode("settings"));
        root.then(settingsNode("gameplay"));

        root.then(Commands.literal("group")
                .then(Commands.literal("role").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                        .then(Commands.argument("papel", StringArgumentType.word()).suggests(words("hunter", "escape", "cacador", "fugitivo")).executes(this::groupRole))))
                .then(Commands.literal("delete").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames()).executes(this::groupDelete)))
                .then(Commands.literal("phaseprogression").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                        .then(Commands.argument("ligado", StringArgumentType.word()).suggests(words("true", "false")).executes(this::groupPhase))))
                .then(Commands.literal("trackplayers").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                        .then(Commands.argument("ligado", StringArgumentType.word()).suggests(words("true", "false")).executes(this::groupTrack))))
                .then(Commands.literal("spawnpoint")
                        .then(Commands.literal("set").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                                .executes(ctx -> groupSpawn(ctx, ctx.getSource().getLocation()))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg()).executes(ctx -> groupSpawn(ctx,
                                                new Location(ctx.getSource().getLocation().getWorld(), DoubleArgumentType.getDouble(ctx, "x"),
                                                        DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z")))))))))
                        .then(Commands.literal("clear").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames()).executes(ctx -> groupSpawn(ctx, null)))))
                .then(Commands.literal("enemy")
                        .then(Commands.literal("add").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                                .then(Commands.argument("inimigo", StringArgumentType.word()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, true)))))
                        .then(Commands.literal("remove").then(Commands.argument("grupo", StringArgumentType.word()).suggests(groupNames())
                                .then(Commands.argument("inimigo", StringArgumentType.word()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, false)))))));

        root.then(Commands.literal("gamerule")
                .then(Commands.literal("min_sec_range_nether_entering").then(Commands.argument("valor", IntegerArgumentType.integer(0, 600))
                        .executes(ctx -> applySetting(ctx, settings().minNetherDelay, String.valueOf(IntegerArgumentType.getInteger(ctx, "valor"))))))
                .then(Commands.literal("max_sec_range_nether_entering").then(Commands.argument("valor", IntegerArgumentType.integer(0, 600))
                        .executes(ctx -> applySetting(ctx, settings().maxNetherDelay, String.valueOf(IntegerArgumentType.getInteger(ctx, "valor")))))));

        root.then(Commands.literal("tp").then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames())
                .then(Commands.argument("jogador", ArgumentTypes.player()).executes(this::tp))));
        root.then(Commands.literal("tpall").executes(this::tpAll));
        root.then(Commands.literal("setspawnpoint")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("y", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg()).then(Commands.argument("bot", StringArgumentType.word()).suggests(botNames())
                                .executes(this::setSpawnpoint))))));
        root.then(Commands.literal("execute").then(Commands.argument("capacidade", StringArgumentType.word())
                .suggests(words("tnt-craft", "bow-burst", "firework-crossbow")).executes(this::execute)));
        root.then(Commands.literal("deleteall")
                .executes(ctx -> {
                    msg().send(sender(ctx), "comando.confirmar-deleteall");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("confirm").executes(ctx -> {
                    int n = manager().deleteAll();
                    msg().send(sender(ctx), "comando.todos-removidos", Messages.p("quantidade", n));
                    return Command.SINGLE_SUCCESS;
                })));
        root.then(Commands.literal("start").executes(ctx -> {
            if (manager().countdown().finishNow()) {
                return Command.SINGLE_SUCCESS;
            }
            int released = 0;
            for (Bot b : manager().aliveBots()) {
                if (b.idle() != null && b.idle().isActive()) {
                    b.idle().release();
                    released++;
                }
            }
            msg().send(sender(ctx), released > 0 ? "contagem.liberados" : "contagem.nada", Messages.p("quantidade", released));
            return Command.SINGLE_SUCCESS;
        }));
        root.then(Commands.literal("menu").executes(ctx -> {
            if (!(sender(ctx) instanceof Player p)) {
                msg().send(sender(ctx), "comando.apenas-jogador");
                return 0;
            }
            plugin.menus().openMain(p);
            return Command.SINGLE_SUCCESS;
        }));
        root.then(Commands.literal("skin").then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames())
                .then(Commands.argument("jogador", StringArgumentType.word()).executes(this::skin))));
        root.then(Commands.literal("reload").executes(ctx -> {
            plugin.reloadAll();
            msg().send(sender(ctx), "comando.recarregado");
            return Command.SINGLE_SUCCESS;
        }));
        return root.build();
    }

    private static int amount(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "quantidade");
    }

    private static String diff(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "dificuldade");
    }

    // ------------------------------------------------------------------ criação

    private int help(CommandContext<CommandSourceStack> ctx) {
        for (String line : msg().raw("ajuda").split("\n")) {
            sender(ctx).sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx, int amount, String diffRaw, String skin, int phase, int aggression) {
        return spawn(ctx, ctx.getSource().getLocation(), amount, diffRaw, skin, phase, aggression);
    }

    private int spawnAt(CommandContext<CommandSourceStack> ctx, int phase, int aggression) {
        Location base = ctx.getSource().getLocation();
        Location loc = new Location(base.getWorld(), DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"));
        return spawn(ctx, loc, amount(ctx), diff(ctx), null, phase, aggression);
    }

    private int spawn(CommandContext<CommandSourceStack> ctx, Location loc, int amount, String diffRaw, String skin, int phase, int aggression) {
        CommandSender s = sender(ctx);
        BotDifficulty difficulty = diffRaw == null ? settings().defaultDifficulty.get() : BotDifficulty.parse(diffRaw);
        if (difficulty == null) {
            msg().send(s, "comando.dificuldade-invalida");
            return 0;
        }
        if (loc == null || loc.getWorld() == null) {
            msg().send(s, "comando.sem-local");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nome");
        msg().send(s, "comando.criando", Messages.p("quantidade", amount), Messages.p("dificuldade", difficulty.label()), Messages.p("nome", name));
        manager().createBots(loc, name, skin, amount, difficulty, phase, aggression).whenComplete((bots, err) -> {
            if (err != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao criar bots", err);
                msg().send(s, "comando.erro-criar");
            } else {
                msg().send(s, "comando.criados", Messages.p("quantidade", bots.size()), Messages.p("nome", name));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ gerenciamento

    private int remove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nome");
        List<Bot> list = manager().botsNamed(name);
        for (Bot b : list) {
            manager().removeBot(b);
        }
        msg().send(sender(ctx), "comando.removidos", Messages.p("quantidade", list.size()), Messages.p("nome", name));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = sender(ctx);
        msg().send(s, "comando.lista-titulo", Messages.p("quantidade", manager().allBots().size()), Messages.p("vivos", manager().aliveCount()));
        for (Bot b : manager().allBots()) {
            String where = b.isAlive() ? String.format(Locale.ROOT, "%s %.0f %.0f %.0f", b.world().getName(), b.pos().x(), b.pos().y(), b.pos().z())
                    : msg().raw("comando.lista-morto");
            Player t = b.target();
            s.sendMessage(msg().get("comando.lista-linha", Messages.p("nome", b.name()), Messages.p("visivel", b.visualName()),
                    Messages.p("fase", b.progression().phase()), Messages.p("dificuldade", b.difficulty().label()),
                    Messages.p("vida", String.format(Locale.ROOT, "%.0f", b.health())), Messages.p("grupo", b.groupName() == null ? "-" : b.groupName()),
                    Messages.p("alvo", t == null ? "-" : t.getName()), Messages.p("local", where), Messages.p("abates", b.kills()),
                    Messages.p("estado", b.isAlive() ? b.brain().state() : "-")));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Bot b = manager().bot(StringArgumentType.getString(ctx, "nome"));
        if (b == null) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        Player t = b.target();
        sender(ctx).sendMessage(msg().get("comando.info", Messages.p("nome", b.name()), Messages.p("visivel", b.visualName()),
                Messages.p("fase", b.progression().phase()), Messages.p("dificuldade", b.difficulty().label()),
                Messages.p("vida", String.format(Locale.ROOT, "%.1f/%.0f", b.health(), b.maxHealth())),
                Messages.p("fome", b.hunger().foodLevel()), Messages.p("grupo", b.groupName() == null ? "-" : b.groupName()),
                Messages.p("alvo", t == null ? "-" : t.getName()), Messages.p("abates", b.kills()), Messages.p("agressividade", b.aggression()),
                Messages.p("vivo", b.isAlive() ? "sim" : "não"), Messages.p("estado", b.brain().state()),
                Messages.p("local", b.isAlive() ? String.format(Locale.ROOT, "%s %.1f %.1f %.1f", b.world().getName(), b.pos().x(), b.pos().y(), b.pos().z()) : "-"),
                Messages.p("velocidade", String.format(Locale.ROOT, "%.2f", b.velocity().horizontalLength() * 20)),
                Messages.p("chao", b.onGround() ? "sim" : "não")));
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx) {
        ItemStack stack = ctx.getArgument("item", ItemStack.class);
        for (Bot b : manager().aliveBots()) {
            b.setItem(EquipmentSlot.HAND, stack.clone());
            b.inventory().add(stack.clone());
        }
        msg().send(sender(ctx), "comando.item-dado", Messages.p("item", stack.getType().getKey().getKey()));
        return Command.SINGLE_SUCCESS;
    }

    private int armor(CommandContext<CommandSourceStack> ctx) {
        String tier = StringArgumentType.getString(ctx, "tier").toLowerCase(Locale.ROOT);
        String prefix = switch (tier) {
            case "leather", "couro" -> "LEATHER";
            case "chainmail", "malha" -> "CHAINMAIL";
            case "iron", "ferro" -> "IRON";
            case "gold", "ouro" -> "GOLDEN";
            case "diamond", "diamante" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> null;
        };
        if (prefix == null) {
            msg().send(sender(ctx), "comando.tier-invalido", Messages.p("tier", tier));
            return 0;
        }
        for (Bot b : manager().aliveBots()) {
            b.setItem(EquipmentSlot.HEAD, new ItemStack(Material.valueOf(prefix + "_HELMET")));
            b.setItem(EquipmentSlot.CHEST, new ItemStack(Material.valueOf(prefix + "_CHESTPLATE")));
            b.setItem(EquipmentSlot.LEGS, new ItemStack(Material.valueOf(prefix + "_LEGGINGS")));
            b.setItem(EquipmentSlot.FEET, new ItemStack(Material.valueOf(prefix + "_BOOTS")));
        }
        msg().send(sender(ctx), "comando.armadura", Messages.p("tier", tier));
        return Command.SINGLE_SUCCESS;
    }

    private int target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        manager().setForcedTarget(p.getUniqueId());
        for (Bot b : manager().aliveBots()) {
            b.setTargetPlayer(p.getUniqueId());
        }
        msg().send(sender(ctx), "comando.alvo-definido", Messages.p("jogador", p.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "bot");
        Player p = ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
        boolean ok = manager().teleportBotToPlayer(name, p);
        msg().send(sender(ctx), ok ? "comando.tp-ok" : "comando.bot-nao-encontrado", Messages.p("bot", name), Messages.p("jogador", p.getName()));
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private int tpAll(CommandContext<CommandSourceStack> ctx) {
        Entity ex = ctx.getSource().getExecutor();
        if (!(ex instanceof Player p)) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        int n = manager().teleportAllTo(p);
        msg().send(sender(ctx), "comando.tpall", Messages.p("quantidade", n));
        return Command.SINGLE_SUCCESS;
    }

    private int setSpawnpoint(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        Location loc = new Location(ctx.getSource().getLocation().getWorld(), DoubleArgumentType.getDouble(ctx, "x"),
                DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"));
        for (Bot b : list) {
            manager().setCustomSpawnpoint(b, loc);
        }
        msg().send(sender(ctx), "comando.spawnpoint-bot", Messages.p("bot", name),
                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        return Command.SINGLE_SUCCESS;
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
        if (!(sender(ctx) instanceof Player p)) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        String cap = StringArgumentType.getString(ctx, "capacidade");
        String err = manager().tactics().debugExecute(p, cap);
        if (err == null) {
            msg().send(p, "capacidade.iniciada", Messages.p("capacidade", cap));
            return Command.SINGLE_SUCCESS;
        }
        msg().send(p, "capacidade." + err, Messages.p("capacidade", cap));
        return 0;
    }

    private int skin(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        String player = StringArgumentType.getString(ctx, "jogador");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        CommandSender s = sender(ctx);
        plugin.skins().fetchPlayerSkin(player).thenAccept(skin -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (skin == null) {
                msg().send(s, "comando.skin-falhou", Messages.p("jogador", player));
                return;
            }
            for (Bot b : list) {
                b.setSkin(skin);
                plugin.tabList().refresh(b);
            }
            manager().markDirty();
            msg().send(s, "comando.skin-ok", Messages.p("bot", name), Messages.p("jogador", player));
        }));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ grupos

    private BotGroup groupArg(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "grupo");
        BotGroup g = manager().group(name);
        if (g == null) {
            msg().send(sender(ctx), "grupo.nao-existe", Messages.p("grupo", name));
        }
        return g;
    }

    private int groupRole(CommandContext<CommandSourceStack> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "papel").toLowerCase(Locale.ROOT);
        BotRole role = raw.startsWith("esc") || raw.startsWith("fug") ? BotRole.ESCAPE : BotRole.HUNTER;
        g.setRole(role);
        plugin.glow().applyAll(manager().aliveBots());
        manager().markDirty();
        msg().send(sender(ctx), "grupo.papel", Messages.p("grupo", g.name()), Messages.p("papel", role == BotRole.ESCAPE ? "fugitivo" : "caçador"));
        return Command.SINGLE_SUCCESS;
    }

    private int groupDelete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "grupo");
        int n = manager().deleteGroup(name);
        msg().send(sender(ctx), "grupo.apagado", Messages.p("grupo", name), Messages.p("quantidade", n));
        return Command.SINGLE_SUCCESS;
    }

    private int groupPhase(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "grupo");
        boolean on = parseBool(StringArgumentType.getString(ctx, "ligado"));
        int n = manager().setPhaseProgressionForGroup(name, on);
        msg().send(sender(ctx), "grupo.progressao", Messages.p("grupo", name), Messages.p("valor", on ? "ligada" : "desligada"), Messages.p("quantidade", n));
        return Command.SINGLE_SUCCESS;
    }

    private int groupTrack(CommandContext<CommandSourceStack> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        g.setTrackPlayers(parseBool(StringArgumentType.getString(ctx, "ligado")));
        manager().markDirty();
        msg().send(sender(ctx), "grupo.rastrear", Messages.p("grupo", g.name()), Messages.p("valor", g.trackPlayers() ? "sim" : "não"));
        return Command.SINGLE_SUCCESS;
    }

    private int groupSpawn(CommandContext<CommandSourceStack> ctx, Location loc) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        if (loc == null) {
            g.setSpawnpoint(null, null);
            msg().send(sender(ctx), "grupo.spawnpoint-limpo", Messages.p("grupo", g.name()));
        } else {
            g.setSpawnpoint(loc.getWorld().getName(), Vec3.of(loc));
            msg().send(sender(ctx), "grupo.spawnpoint", Messages.p("grupo", g.name()),
                    Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        }
        manager().markDirty();
        return Command.SINGLE_SUCCESS;
    }

    private int groupEnemy(CommandContext<CommandSourceStack> ctx, boolean add) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        String enemy = StringArgumentType.getString(ctx, "inimigo");
        if (add) {
            g.addEnemy(enemy);
        } else {
            g.removeEnemy(enemy);
        }
        manager().markDirty();
        msg().send(sender(ctx), add ? "grupo.inimigo-add" : "grupo.inimigo-remove", Messages.p("grupo", g.name()), Messages.p("inimigo", enemy));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean parseBool(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("on") || s.equals("sim") || s.equals("1") || s.equals("ligado");
    }

    // ------------------------------------------------------------------ opções

    private LiteralArgumentBuilder<CommandSourceStack> settingsNode(String literal) {
        SuggestionProvider<CommandSourceStack> keys = (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            List<String> all = new ArrayList<>();
            for (Setting<?> s : settings().all()) {
                all.add(s.key());
            }
            all.add("astar");
            all.add("clearpersistent");
            all.add("list");
            for (String k : all) {
                if (k.startsWith(rem)) {
                    b.suggest(k);
                }
            }
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> values = (ctx, b) -> {
            String key = StringArgumentType.getString(ctx, "chave");
            if (key.equalsIgnoreCase("astar")) {
                for (String k : new String[]{"nodes", "dynamic", "fallback", "async"}) {
                    b.suggest(k);
                }
                return b.buildFuture();
            }
            Setting<?> s = settings().byKey(key);
            if (s != null) {
                String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                for (String v : s.suggestions()) {
                    if (v.toLowerCase(Locale.ROOT).startsWith(rem)) {
                        b.suggest(v);
                    }
                }
                if (s == settings().glowHunters && rem.contains(" ")) {
                    for (GlowColor c : GlowColor.values()) {
                        b.suggest(rem.substring(0, rem.indexOf(' ') + 1) + c.configName());
                    }
                }
            }
            return b.buildFuture();
        };
        return Commands.literal(literal)
                .executes(this::listSettings)
                .then(Commands.argument("chave", StringArgumentType.word()).suggests(keys)
                        .executes(ctx -> settingNoValue(ctx, StringArgumentType.getString(ctx, "chave")))
                        .then(Commands.argument("valor", StringArgumentType.greedyString()).suggests(values)
                                .executes(ctx -> settingWithValue(ctx, StringArgumentType.getString(ctx, "chave"), StringArgumentType.getString(ctx, "valor")))));
    }

    private int listSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = sender(ctx);
        msg().send(s, "opcoes.titulo");
        for (Setting<?> st : settings().all()) {
            s.sendMessage(msg().get("opcoes.linha", Messages.p("chave", st.key()), Messages.p("valor", st.display()), Messages.p("caminho", st.path())));
        }
        s.sendMessage(msg().get("opcoes.linha", Messages.p("chave", "nodes (efetivo)"),
                Messages.p("valor", plugin.pathService().effectiveNodeCap()), Messages.p("caminho", "pathfinding.limite-nos")));
        return Command.SINGLE_SUCCESS;
    }

    private int settingNoValue(CommandContext<CommandSourceStack> ctx, String key) {
        if (key.equalsIgnoreCase("clearpersistent")) {
            manager().clearPersistentSnapshots();
            msg().send(sender(ctx), "opcoes.persistencia-limpa");
            return Command.SINGLE_SUCCESS;
        }
        if (key.equalsIgnoreCase("list")) {
            return listSettings(ctx);
        }
        Setting<?> s = settings().byKey(key);
        if (s == null) {
            msg().send(sender(ctx), "opcoes.desconhecida", Messages.p("chave", key));
            return 0;
        }
        msg().send(sender(ctx), "opcoes.valor-atual", Messages.p("chave", s.key()), Messages.p("valor", s.display()));
        return Command.SINGLE_SUCCESS;
    }

    private int settingWithValue(CommandContext<CommandSourceStack> ctx, String key, String value) {
        String k = key;
        String v = value.trim();
        if (k.equalsIgnoreCase("astar")) {
            String[] parts = v.split("\\s+", 2);
            k = parts[0];
            v = parts.length > 1 ? parts[1] : "";
            if (v.isEmpty()) {
                return settingNoValue(ctx, k);
            }
        }
        Setting<?> s = settings().byKey(k);
        if (s == null) {
            msg().send(sender(ctx), "opcoes.desconhecida", Messages.p("chave", k));
            return 0;
        }
        if (s == settings().glowHunters || s == settings().glowEscapers) {
            String[] parts = v.split("\\s+", 2);
            v = parts[0];
            if (parts.length > 1) {
                Setting<?> color = s == settings().glowHunters ? settings().glowHuntersColor : settings().glowEscapersColor;
                if (applySilently(ctx, color, parts[1]) == 0) {
                    return 0;
                }
            }
        }
        return applySetting(ctx, s, v);
    }

    private int applySilently(CommandContext<CommandSourceStack> ctx, Setting<?> s, String raw) {
        try {
            s.setFromText(raw);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException e) {
            msg().send(sender(ctx), "opcoes.invalido", Messages.p("chave", s.key()), Messages.p("erro", e.getMessage()));
            return 0;
        }
    }

    private int applySetting(CommandContext<CommandSourceStack> ctx, Setting<?> s, String raw) {
        if (applySilently(ctx, s, raw) == 0) {
            return 0;
        }
        plugin.onSettingChanged(s);
        msg().send(sender(ctx), "opcoes.alterada", Messages.p("chave", s.key()), Messages.p("valor", s.display()));
        return Command.SINGLE_SUCCESS;
    }
}
