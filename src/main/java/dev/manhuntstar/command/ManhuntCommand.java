package dev.manhuntstar.command;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.bot.Modes.GlowColor;
import dev.manhuntstar.bot.Modes.PlayerListMode;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.util.Vec3;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /manhunt}. Partida: start, pause, resume, target, ring, players. Bots: create, remove, list,
 * info, tp, skin, spawnpoint, equip, test. Grupos: group. Opções: settings, reload. E o menu.
 * Os verbos são os mesmos em tudo: create/remove para bots e grupos, add/remove para
 * listas e clear para apagar um valor. Posições aceitam {@code ~ ~ ~}; sem posição vale onde você está.
 */
public final class ManhuntCommand {

    private static final String[] DIFFICULTIES = {"easy", "medium", "hard", "terminator"};
    private static final String[] ABILITIES = {"tnt-craft", "bow-burst", "firework-crossbow"};

    private final ManhuntStarPlugin plugin;

    public ManhuntCommand(ManhuntStarPlugin plugin) {
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

    // ------------------------------------------------------------------ sugestões

    private SuggestionProvider<CommandSourceStack> botNames() {
        return (ctx, b) -> {
            for (Bot bot : manager().allBots()) {
                suggestName(b, bot.name());
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> groupNames() {
        return (ctx, b) -> {
            for (BotGroup g : manager().groups()) {
                suggestName(b, g.name());
            }
            return b.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> onlinePlayerNames() {
        return (ctx, b) -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestName(b, p.getName());
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> listedPlayerNames() {
        return (ctx, b) -> {
            for (String n : settings().playerList()) {
                suggestName(b, n);
            }
            return b.buildFuture();
        };
    }

    /** Nomes com %, acento ou espaço só passam entre aspas, então a sugestão já vem com elas. */
    private static void suggestName(SuggestionsBuilder b, String name) {
        String text = StringArgumentType.escapeIfRequired(name);
        if (text.toLowerCase(Locale.ROOT).startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
            b.suggest(text);
        }
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

    // ------------------------------------------------------------------ árvore

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("manhunt")
                .requires(src -> src.getSender().hasPermission("manhuntstar.admin"))
                .executes(this::help);
        root.then(Commands.literal("help").executes(this::help));
        root.then(Commands.literal("menu").executes(this::menu));

        // partida
        root.then(Commands.literal("start").executes(this::start));
        root.then(Commands.literal("pause").executes(this::pause));
        root.then(Commands.literal("resume").executes(this::resume));
        root.then(Commands.literal("target")
                .then(Commands.literal("clear").executes(this::targetClear))
                .then(Commands.argument("player", ArgumentTypes.player()).executes(this::target)));
        root.then(Commands.literal("ring").executes(this::ringInfo)
                .then(Commands.literal("point")
                        .executes(ctx -> ringPoint(ctx, here(ctx)))
                        .then(Commands.literal("clear").executes(this::ringPointClear))
                        .then(Commands.argument("pos", ArgumentTypes.finePosition()).executes(ctx -> ringPoint(ctx, pos(ctx))))));
        root.then(Commands.literal("players").executes(this::playersInfo)
                .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word()).suggests(onlinePlayerNames())
                        .executes(ctx -> playersEdit(ctx, true))))
                .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word()).suggests(listedPlayerNames())
                        .executes(ctx -> playersEdit(ctx, false))))
                .then(Commands.literal("mode").then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(words("off", "allowlist", "blocklist")).executes(this::playersMode))));

        // bots
        root.then(Commands.literal("create").then(
                createOptions(Commands.argument("name", StringArgumentType.string()), false, false, false).then(
                        createOptions(Commands.argument("amount", IntegerArgumentType.integer(1, 100)), false, false, false).then(
                                createOptions(Commands.argument("difficulty", StringArgumentType.word()).suggests(words(DIFFICULTIES)), true, true, true)))));
        root.then(Commands.literal("remove")
                .then(Commands.literal("all").executes(ctx -> {
                    msg().send(sender(ctx), "comando.confirmar-remover-todos");
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.literal("confirm").executes(this::removeAll)))
                .then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames()).executes(this::remove)));
        root.then(Commands.literal("list").executes(this::list));
        root.then(Commands.literal("info").then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames()).executes(this::info)));
        root.then(Commands.literal("tp")
                .then(Commands.literal("all").executes(ctx -> tpAll(ctx, null))
                        .then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> tpAll(ctx, player(ctx)))))
                .then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames()).executes(ctx -> tp(ctx, null))
                        .then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> tp(ctx, player(ctx))))));
        root.then(Commands.literal("skin").then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames())
                .then(Commands.argument("player", StringArgumentType.word()).suggests(onlinePlayerNames()).executes(this::skin))));
        root.then(Commands.literal("spawnpoint").then(Commands.argument("bot", StringArgumentType.string()).suggests(botNames())
                .executes(ctx -> botSpawnpoint(ctx, here(ctx), false))
                .then(Commands.literal("clear").executes(ctx -> botSpawnpoint(ctx, null, true)))
                .then(Commands.argument("pos", ArgumentTypes.finePosition()).executes(ctx -> botSpawnpoint(ctx, pos(ctx), false)))));
        root.then(Commands.literal("equip")
                .then(Commands.literal("item").then(Commands.argument("item", ArgumentTypes.itemStack()).executes(this::equipItem)))
                .then(Commands.literal("armor").then(Commands.argument("tier", StringArgumentType.word())
                        .suggests(words("leather", "chainmail", "iron", "gold", "diamond", "netherite")).executes(this::equipArmor))));
        root.then(Commands.literal("test").then(Commands.argument("ability", StringArgumentType.word()).suggests(words(ABILITIES)).executes(this::test)));

        // grupos
        root.then(Commands.literal("group").then(Commands.argument("group", StringArgumentType.string()).suggests(groupNames())
                .then(Commands.literal("role").then(Commands.argument("role", StringArgumentType.word()).suggests(words("hunter", "escape")).executes(this::groupRole)))
                .then(Commands.literal("enemy")
                        .then(Commands.literal("add").then(Commands.argument("other", StringArgumentType.string()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, true))))
                        .then(Commands.literal("remove").then(Commands.argument("other", StringArgumentType.string()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, false)))))
                .then(Commands.literal("spawnpoint")
                        .executes(ctx -> groupSpawnpoint(ctx, here(ctx), false))
                        .then(Commands.literal("clear").executes(ctx -> groupSpawnpoint(ctx, null, true)))
                        .then(Commands.argument("pos", ArgumentTypes.finePosition()).executes(ctx -> groupSpawnpoint(ctx, pos(ctx), false))))
                .then(Commands.literal("phases").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(this::groupPhases)))
                .then(Commands.literal("chaseplayers").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(this::groupChase)))
                .then(Commands.literal("remove").executes(this::groupRemove))));

        // opções
        root.then(settingsNode());
        root.then(Commands.literal("reload").executes(ctx -> {
            plugin.reloadAll();
            msg().send(sender(ctx), "comando.recarregado");
            return Command.SINGLE_SUCCESS;
        }));
        return root.build();
    }

    /**
     * Opções do create, sempre nesta ordem: {@code [skin <jogador>] [at <x y z>] [phase] [aggression]}.
     * Cada nível da árvore ganha o executor e os ramos que ainda cabem nele.
     */
    private <T extends ArgumentBuilder<CommandSourceStack, T>> T createOptions(T node, boolean skin, boolean at, boolean phase) {
        node.executes(this::create);
        if (skin) {
            node.then(Commands.literal("skin").then(createOptions(Commands.argument("skin", StringArgumentType.string()).suggests(onlinePlayerNames()), false, true, true)));
        }
        if (at) {
            node.then(Commands.literal("at").then(createOptions(Commands.argument("pos", ArgumentTypes.finePosition()), false, false, true)));
        }
        if (phase) {
            node.then(Commands.argument("phase", IntegerArgumentType.integer(1, 10)).executes(this::create)
                    .then(Commands.argument("aggression", IntegerArgumentType.integer(0, 10)).executes(this::create)));
        }
        return node;
    }

    // ------------------------------------------------------------------ argumentos

    /** Argumento opcional: null quando o ramo digitado não tem esse argumento. */
    private static <V> V opt(CommandContext<CommandSourceStack> ctx, String name, Class<V> type) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    private static Location pos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Location base = ctx.getSource().getLocation();
        return ctx.getArgument("pos", FinePositionResolver.class).resolve(ctx.getSource()).toLocation(base.getWorld());
    }

    /** Onde quem digitou está; null no console (sem posição própria). */
    private static Location here(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getExecutor() instanceof Entity ? ctx.getSource().getLocation() : null;
    }

    private int noLocation(CommandContext<CommandSourceStack> ctx) {
        msg().send(sender(ctx), "comando.sem-local");
        return 0;
    }

    // ------------------------------------------------------------------ geral

    private int help(CommandContext<CommandSourceStack> ctx) {
        for (String line : msg().raw("ajuda").split("\n")) {
            sender(ctx).sendMessage(MiniMessage.miniMessage().deserialize(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int menu(CommandContext<CommandSourceStack> ctx) {
        if (!(sender(ctx) instanceof Player p)) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        plugin.menus().openMain(p);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ partida

    private int start(CommandContext<CommandSourceStack> ctx) {
        if (manager().rings().releaseAll() == 0) {
            msg().send(sender(ctx), "contagem.nada");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int pause(CommandContext<CommandSourceStack> ctx) {
        if (!manager().pause().pause()) {
            msg().send(sender(ctx), "pausa.ja-pausada");
            return 0;
        }
        Bukkit.broadcast(msg().prefixed("pausa.pausada"));
        return Command.SINGLE_SUCCESS;
    }

    private int resume(CommandContext<CommandSourceStack> ctx) {
        if (!manager().pause().resume()) {
            msg().send(sender(ctx), "pausa.nao-pausada");
            return 0;
        }
        Bukkit.broadcast(msg().prefixed("pausa.continuada"));
        return Command.SINGLE_SUCCESS;
    }

    private int target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = player(ctx);
        manager().setForcedTarget(p.getUniqueId());
        for (Bot b : manager().aliveBots()) {
            b.setTargetPlayer(p.getUniqueId());
        }
        msg().send(sender(ctx), "comando.alvo-definido", Messages.p("jogador", p.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int targetClear(CommandContext<CommandSourceStack> ctx) {
        manager().setForcedTarget(null);
        msg().send(sender(ctx), "comando.alvo-limpo");
        return Command.SINGLE_SUCCESS;
    }

    private int ringInfo(CommandContext<CommandSourceStack> ctx) {
        Settings.RingPoint pt = settings().ringPoint();
        msg().send(sender(ctx), "roda.info",
                Messages.p("raio", settings().ringRadius.value() <= 0 ? "automático" : settings().ringRadius.display()),
                Messages.p("centro", settings().ringCenter.display()),
                Messages.p("ponto", pt == null ? "nenhum" : String.format(Locale.ROOT, "%.1f %.1f %.1f (%s)", pt.x(), pt.y(), pt.z(), pt.world())),
                Messages.p("rodas", manager().rings().count()));
        return Command.SINGLE_SUCCESS;
    }

    /** Marca o ponto da roda e passa o centro para manual. */
    private int ringPoint(CommandContext<CommandSourceStack> ctx, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return noLocation(ctx);
        }
        settings().setRingPoint(new Settings.RingPoint(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        settings().ringCenter.set(RingCenter.MANUAL);
        plugin.onSettingChanged(settings().ringCenter);
        msg().send(sender(ctx), "roda.ponto-marcado", Messages.p("mundo", loc.getWorld().getName()),
                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        return Command.SINGLE_SUCCESS;
    }

    /** Apaga o ponto da roda e volta o centro para automático. */
    private int ringPointClear(CommandContext<CommandSourceStack> ctx) {
        settings().setRingPoint(null);
        settings().ringCenter.set(RingCenter.AUTOMATICO);
        plugin.onSettingChanged(settings().ringCenter);
        msg().send(sender(ctx), "roda.ponto-limpo");
        return Command.SINGLE_SUCCESS;
    }

    private int playersInfo(CommandContext<CommandSourceStack> ctx) {
        List<String> names = settings().playerList();
        msg().send(sender(ctx), "lista.info", Messages.p("modo", settings().playerListMode.display()),
                Messages.p("nomes", names.isEmpty() ? msg().raw("lista.vazia") : String.join(", ", names)));
        return Command.SINGLE_SUCCESS;
    }

    private int playersEdit(CommandContext<CommandSourceStack> ctx, boolean add) {
        String name = StringArgumentType.getString(ctx, "player");
        boolean changed = add ? settings().addListedPlayer(name) : settings().removeListedPlayer(name);
        if (!changed) {
            msg().send(sender(ctx), add ? "lista.ja-esta" : "lista.nao-esta", Messages.p("jogador", name));
            return 0;
        }
        plugin.saveSettings();
        msg().send(sender(ctx), add ? "lista.adicionado" : "lista.removido", Messages.p("jogador", name));
        if (settings().playerListMode.get() == PlayerListMode.DESLIGADA) {
            msg().send(sender(ctx), "lista.aviso-desligada");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int playersMode(CommandContext<CommandSourceStack> ctx) {
        if (applySilently(ctx, settings().playerListMode, StringArgumentType.getString(ctx, "mode")) == 0) {
            return 0;
        }
        plugin.onSettingChanged(settings().playerListMode);
        String mode = settings().playerListMode.display();
        msg().send(sender(ctx), "lista.modo", Messages.p("modo", mode), Messages.p("explicacao", msg().raw("lista.explicacao." + mode)));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ bots

    /** Cria onde quem digitou está (ou no ponto da roda, com o centro em manual), ou no "at x y z". */
    private int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender s = sender(ctx);
        Integer amount = opt(ctx, "amount", Integer.class);
        String diffRaw = opt(ctx, "difficulty", String.class);
        String skin = opt(ctx, "skin", String.class);
        Integer phase = opt(ctx, "phase", Integer.class);
        Integer aggression = opt(ctx, "aggression", Integer.class);
        BotDifficulty difficulty = diffRaw == null ? settings().defaultDifficulty.get() : BotDifficulty.parse(diffRaw);
        if (difficulty == null) {
            msg().send(s, "comando.dificuldade-invalida");
            return 0;
        }
        Location loc;
        if (opt(ctx, "pos", FinePositionResolver.class) != null) {
            loc = pos(ctx);
        } else {
            if (manager().manualRingPointMissing()) {
                msg().send(s, "roda.sem-ponto");
            }
            loc = manager().ringCenterFor(ctx.getSource().getLocation());
        }
        if (loc == null || loc.getWorld() == null) {
            return noLocation(ctx);
        }
        String name = StringArgumentType.getString(ctx, "name");
        int count = amount == null ? 1 : amount;
        msg().send(s, "comando.criando", Messages.p("quantidade", count), Messages.p("dificuldade", difficulty.label()), Messages.p("nome", name));
        manager().createBots(loc, name, skin, count, difficulty, phase == null ? 0 : phase, aggression == null ? 0 : aggression).whenComplete((bots, err) -> {
            if (err != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao criar bots", err);
                msg().send(s, "comando.erro-criar");
            } else {
                msg().send(s, "comando.criados", Messages.p("quantidade", bots.size()), Messages.p("nome", name));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        for (Bot b : list) {
            manager().removeBot(b);
        }
        msg().send(sender(ctx), "comando.removidos", Messages.p("quantidade", list.size()), Messages.p("nome", name));
        return Command.SINGLE_SUCCESS;
    }

    private int removeAll(CommandContext<CommandSourceStack> ctx) {
        int n = manager().deleteAll();
        msg().send(sender(ctx), "comando.todos-removidos", Messages.p("quantidade", n));
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
                    Messages.p("estado", stateOf(b))));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** O que a IA está fazendo, ou "pausado" com a caçada pausada. */
    private String stateOf(Bot b) {
        if (!b.isAlive()) {
            return "-";
        }
        return manager().pause().isPaused() ? msg().raw("pausa.estado") : b.brain().state();
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Bot b = manager().bot(StringArgumentType.getString(ctx, "bot"));
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
                Messages.p("vivo", b.isAlive() ? "sim" : "não"), Messages.p("estado", b.isAlive() ? stateOf(b) : b.brain().state()),
                Messages.p("local", b.isAlive() ? String.format(Locale.ROOT, "%s %.1f %.1f %.1f", b.world().getName(), b.pos().x(), b.pos().y(), b.pos().z()) : "-"),
                Messages.p("velocidade", String.format(Locale.ROOT, "%.2f", b.velocity().horizontalLength() * 20)),
                Messages.p("chao", b.onGround() ? "sim" : "não")));
        return Command.SINGLE_SUCCESS;
    }

    /** Leva os bots com esse nome até o jogador (ou até você). */
    private int tp(CommandContext<CommandSourceStack> ctx, Player to) {
        Player dest = to != null ? to : ctx.getSource().getExecutor() instanceof Player p ? p : null;
        if (dest == null) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "bot");
        int n = manager().teleportBotsToPlayer(name, dest);
        if (n == 0) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        msg().send(sender(ctx), n == 1 ? "comando.tp-ok" : "comando.tp-todos", Messages.p("bot", name), Messages.p("quantidade", n),
                Messages.p("jogador", dest.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int tpAll(CommandContext<CommandSourceStack> ctx, Player to) {
        Player dest = to != null ? to : ctx.getSource().getExecutor() instanceof Player p ? p : null;
        if (dest == null) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        int n = manager().teleportAllTo(dest);
        msg().send(sender(ctx), "comando.tp-todos", Messages.p("quantidade", n), Messages.p("jogador", dest.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int skin(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        String player = StringArgumentType.getString(ctx, "player");
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

    /** Ponto de renascimento dos bots com esse nome (onde você está, numa posição, ou clear). */
    private int botSpawnpoint(CommandContext<CommandSourceStack> ctx, Location loc, boolean clear) {
        String name = StringArgumentType.getString(ctx, "bot");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "comando.bot-nao-encontrado");
            return 0;
        }
        if (!clear && (loc == null || loc.getWorld() == null)) {
            return noLocation(ctx);
        }
        for (Bot b : list) {
            manager().setCustomSpawnpoint(b, clear ? null : loc);
        }
        if (clear) {
            msg().send(sender(ctx), "comando.spawnpoint-bot-limpo", Messages.p("bot", name));
        } else {
            msg().send(sender(ctx), "comando.spawnpoint-bot", Messages.p("bot", name),
                    Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int equipItem(CommandContext<CommandSourceStack> ctx) {
        ItemStack stack = ctx.getArgument("item", ItemStack.class);
        for (Bot b : manager().aliveBots()) {
            b.setItem(EquipmentSlot.HAND, stack.clone());
            b.inventory().add(stack.clone());
        }
        msg().send(sender(ctx), "comando.item-dado", Messages.p("item", stack.getType().getKey().getKey()));
        return Command.SINGLE_SUCCESS;
    }

    private int equipArmor(CommandContext<CommandSourceStack> ctx) {
        String tier = StringArgumentType.getString(ctx, "tier").toLowerCase(Locale.ROOT);
        String prefix = switch (tier) {
            case "leather" -> "LEATHER";
            case "chainmail" -> "CHAINMAIL";
            case "iron" -> "IRON";
            case "gold" -> "GOLDEN";
            case "diamond" -> "DIAMOND";
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

    private int test(CommandContext<CommandSourceStack> ctx) {
        if (!(sender(ctx) instanceof Player p)) {
            msg().send(sender(ctx), "comando.apenas-jogador");
            return 0;
        }
        String cap = StringArgumentType.getString(ctx, "ability");
        String err = manager().tactics().debugExecute(p, cap);
        if (err == null) {
            msg().send(p, "capacidade.iniciada", Messages.p("capacidade", cap));
            return Command.SINGLE_SUCCESS;
        }
        msg().send(p, "capacidade." + err, Messages.p("capacidade", cap));
        return 0;
    }

    // ------------------------------------------------------------------ grupos

    private BotGroup groupArg(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "group");
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
        String raw = StringArgumentType.getString(ctx, "role").toLowerCase(Locale.ROOT);
        BotRole role;
        if (raw.equals("hunter")) {
            role = BotRole.HUNTER;
        } else if (raw.equals("escape")) {
            role = BotRole.ESCAPE;
        } else {
            msg().send(sender(ctx), "grupo.papel-invalido", Messages.p("papel", raw));
            return 0;
        }
        g.setRole(role);
        plugin.glow().applyAll(manager().aliveBots());
        manager().markDirty();
        msg().send(sender(ctx), "grupo.papel", Messages.p("grupo", g.name()), Messages.p("papel", role == BotRole.ESCAPE ? "fugitivo" : "caçador"));
        return Command.SINGLE_SUCCESS;
    }

    private int groupEnemy(CommandContext<CommandSourceStack> ctx, boolean add) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        String enemy = StringArgumentType.getString(ctx, "other");
        if (add) {
            g.addEnemy(enemy);
        } else {
            g.removeEnemy(enemy);
        }
        manager().markDirty();
        msg().send(sender(ctx), add ? "grupo.inimigo-add" : "grupo.inimigo-remove", Messages.p("grupo", g.name()), Messages.p("inimigo", enemy));
        return Command.SINGLE_SUCCESS;
    }

    private int groupSpawnpoint(CommandContext<CommandSourceStack> ctx, Location loc, boolean clear) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        if (clear) {
            g.setSpawnpoint(null, null);
            msg().send(sender(ctx), "grupo.spawnpoint-limpo", Messages.p("grupo", g.name()));
        } else if (loc == null || loc.getWorld() == null) {
            return noLocation(ctx);
        } else {
            g.setSpawnpoint(loc.getWorld().getName(), Vec3.of(loc));
            msg().send(sender(ctx), "grupo.spawnpoint", Messages.p("grupo", g.name()),
                    Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
        }
        manager().markDirty();
        return Command.SINGLE_SUCCESS;
    }

    private int groupPhases(CommandContext<CommandSourceStack> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        boolean on = BoolArgumentType.getBool(ctx, "enabled");
        int n = manager().setPhaseProgressionForGroup(g.name(), on);
        msg().send(sender(ctx), "grupo.progressao", Messages.p("grupo", g.name()), Messages.p("valor", on ? "ligada" : "desligada"), Messages.p("quantidade", n));
        return Command.SINGLE_SUCCESS;
    }

    private int groupChase(CommandContext<CommandSourceStack> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        g.setTrackPlayers(BoolArgumentType.getBool(ctx, "enabled"));
        manager().markDirty();
        msg().send(sender(ctx), "grupo.rastrear", Messages.p("grupo", g.name()), Messages.p("valor", g.trackPlayers() ? "sim" : "não"));
        return Command.SINGLE_SUCCESS;
    }

    private int groupRemove(CommandContext<CommandSourceStack> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        int n = manager().deleteGroup(g.name());
        msg().send(sender(ctx), "grupo.apagado", Messages.p("grupo", g.name()), Messages.p("quantidade", n));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ opções

    private LiteralArgumentBuilder<CommandSourceStack> settingsNode() {
        SuggestionProvider<CommandSourceStack> keys = (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (Setting<?> s : settings().all()) {
                if (s.key().startsWith(rem)) {
                    b.suggest(s.key());
                }
            }
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> values = (ctx, b) -> {
            Setting<?> s = settings().byKey(StringArgumentType.getString(ctx, "key"));
            if (s != null) {
                String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                for (String v : s.suggestions()) {
                    if (v.toLowerCase(Locale.ROOT).startsWith(rem)) {
                        b.suggest(v);
                    }
                }
                if (s == settings().ringRadius && "auto".startsWith(rem)) {
                    b.suggest("auto");
                }
                if ((s == settings().glowHunters || s == settings().glowEscapers) && rem.contains(" ")) {
                    for (GlowColor c : GlowColor.values()) {
                        b.suggest(rem.substring(0, rem.indexOf(' ') + 1) + c.configName());
                    }
                }
            }
            return b.buildFuture();
        };
        return Commands.literal("settings")
                .executes(this::listSettings)
                .then(Commands.argument("key", StringArgumentType.word()).suggests(keys)
                        .executes(this::showSetting)
                        .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(values)
                                .executes(this::changeSetting)));
    }

    private int listSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = sender(ctx);
        msg().send(s, "opcoes.titulo");
        for (Setting<?> st : settings().all()) {
            s.sendMessage(msg().get("opcoes.linha", Messages.p("chave", st.key()), Messages.p("valor", st.display()), Messages.p("caminho", st.path())));
        }
        s.sendMessage(msg().get("opcoes.linha", Messages.p("chave", "pathnodes (efetivo)"),
                Messages.p("valor", plugin.pathService().effectiveNodeCap()), Messages.p("caminho", "pathfinding.limite-nos")));
        return Command.SINGLE_SUCCESS;
    }

    private Setting<?> settingArg(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        Setting<?> s = settings().byKey(key);
        if (s == null) {
            msg().send(sender(ctx), "opcoes.desconhecida", Messages.p("chave", key));
        }
        return s;
    }

    private int showSetting(CommandContext<CommandSourceStack> ctx) {
        Setting<?> s = settingArg(ctx);
        if (s == null) {
            return 0;
        }
        msg().send(sender(ctx), "opcoes.valor-atual", Messages.p("chave", s.key()), Messages.p("valor", s.display()));
        return Command.SINGLE_SUCCESS;
    }

    private int changeSetting(CommandContext<CommandSourceStack> ctx) {
        Setting<?> s = settingArg(ctx);
        if (s == null) {
            return 0;
        }
        String v = StringArgumentType.getString(ctx, "value").trim();
        if (s == settings().ringRadius && v.equalsIgnoreCase("auto")) {
            v = "0";
        }
        if (s == settings().glowHunters || s == settings().glowEscapers) {
            // "glowhunters true red": liga e troca a cor de uma vez
            String[] parts = v.split("\\s+", 2);
            v = parts[0];
            if (parts.length > 1) {
                Setting<?> color = s == settings().glowHunters ? settings().glowHuntersColor : settings().glowEscapersColor;
                if (applySilently(ctx, color, parts[1]) == 0) {
                    return 0;
                }
            }
        }
        if (applySilently(ctx, s, v) == 0) {
            return 0;
        }
        plugin.onSettingChanged(s);
        msg().send(sender(ctx), "opcoes.alterada", Messages.p("chave", s.key()), Messages.p("valor", s.display()));
        return Command.SINGLE_SUCCESS;
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
}
