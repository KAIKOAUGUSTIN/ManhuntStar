package dev.manhuntstar.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.manhuntstar.ManhuntHost;
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
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.Place;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

/**
 * {@code /manhunt}. Match: start, pause, resume, target, ring, players. Bots: create, remove, list,
 * info, tp, skin, spawnpoint, equip, test. Groups: group. Options: settings, reload. And the menu.
 * The verbs are the same everywhere: create/remove for bots and groups, add/remove for
 * lists and clear to erase a value. Positions accept {@code ~ ~ ~}; with no position, where you are is used.
 */
public final class ManhuntCommand<S> {

    private static final String[] DIFFICULTIES = {"easy", "medium", "hard", "terminator"};
    private static final String[] ABILITIES = {"tnt-craft", "bow-burst", "firework-crossbow"};

    private final ManhuntHost host;
    private final CommandPlatform<S> platform;

    public ManhuntCommand(ManhuntHost host, CommandPlatform<S> platform) {
        this.host = host;
        this.platform = platform;
    }

    private Messages msg() {
        return host.messages();
    }

    private BotManager manager() {
        return host.botManager();
    }

    private Settings settings() {
        return host.settings();
    }

    private Audience sender(CommandContext<S> ctx) {
        return platform.sender(ctx.getSource());
    }

    private LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private <T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    // ------------------------------------------------------------------ suggestions

    private SuggestionProvider<S> botNames() {
        return (ctx, b) -> {
            for (Bot bot : manager().allBots()) {
                suggestName(b, bot.name());
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<S> groupNames() {
        return (ctx, b) -> {
            for (BotGroup g : manager().groups()) {
                suggestName(b, g.name());
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<S> onlinePlayerNames() {
        return (ctx, b) -> {
            for (GamePlayer p : host.server().onlinePlayers()) {
                suggestName(b, p.name());
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<S> listedPlayerNames() {
        return (ctx, b) -> {
            for (String n : settings().playerList()) {
                suggestName(b, n);
            }
            return b.buildFuture();
        };
    }

    /** Names with %, accents or spaces only work in quotes, so the suggestion already comes with them. */
    private static void suggestName(SuggestionsBuilder b, String name) {
        String text = StringArgumentType.escapeIfRequired(name);
        if (text.toLowerCase(Locale.ROOT).startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
            b.suggest(text);
        }
    }

    private SuggestionProvider<S> words(String... options) {
        return (ctx, b) -> {
            for (String o : options) {
                if (o.startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) {
                    b.suggest(o);
                }
            }
            return b.buildFuture();
        };
    }

    // ------------------------------------------------------------------ tree

    public LiteralCommandNode<S> build() {
        LiteralArgumentBuilder<S> root = literal("manhunt")
                .requires(src -> platform.hasPermission(src, "manhuntstar.admin"))
                .executes(this::help);
        root.then(literal("help").executes(this::help));
        root.then(literal("menu").executes(this::menu));

        // match
        root.then(literal("start").executes(this::start));
        root.then(literal("pause").executes(this::pause));
        root.then(literal("resume").executes(this::resume));
        root.then(literal("target")
                .then(literal("clear").executes(this::targetClear))
                .then(argument("player", platform.playerArgument()).executes(this::target)));
        root.then(literal("ring").executes(this::ringInfo)
                .then(literal("point")
                        .executes(ctx -> ringPoint(ctx, here(ctx)))
                        .then(literal("clear").executes(this::ringPointClear))
                        .then(argument("pos", platform.positionArgument()).executes(ctx -> ringPoint(ctx, pos(ctx))))));
        root.then(literal("players").executes(this::playersInfo)
                .then(literal("add").then(argument("player", StringArgumentType.word()).suggests(onlinePlayerNames())
                        .executes(ctx -> playersEdit(ctx, true))))
                .then(literal("remove").then(argument("player", StringArgumentType.word()).suggests(listedPlayerNames())
                        .executes(ctx -> playersEdit(ctx, false))))
                .then(literal("mode").then(argument("mode", StringArgumentType.word())
                        .suggests(words("off", "allowlist", "blocklist")).executes(this::playersMode))));

        // bots
        root.then(literal("create").then(
                createOptions(argument("name", StringArgumentType.string()), true, true, false).then(
                        createOptions(argument("amount", IntegerArgumentType.integer(1, 100)), true, true, false).then(
                                createOptions(argument("difficulty", StringArgumentType.word()).suggests(words(DIFFICULTIES)), true, true, true)))));
        root.then(literal("remove")
                .then(literal("all").executes(ctx -> {
                    msg().send(sender(ctx), "command.confirm-remove-all");
                    return Command.SINGLE_SUCCESS;
                }).then(literal("confirm").executes(this::removeAll)))
                .then(argument("bot", StringArgumentType.string()).suggests(botNames()).executes(this::remove)));
        root.then(literal("list").executes(this::list));
        root.then(literal("info").then(argument("bot", StringArgumentType.string()).suggests(botNames()).executes(this::info)));
        root.then(literal("tp")
                .then(literal("all").executes(ctx -> tpAll(ctx, null))
                        .then(argument("player", platform.playerArgument()).executes(ctx -> tpAll(ctx, player(ctx)))))
                .then(argument("bot", StringArgumentType.string()).suggests(botNames()).executes(ctx -> tp(ctx, null))
                        .then(argument("player", platform.playerArgument()).executes(ctx -> tp(ctx, player(ctx))))));
        root.then(literal("skin").then(argument("bot", StringArgumentType.string()).suggests(botNames())
                .then(argument("player", StringArgumentType.word()).suggests(onlinePlayerNames()).executes(this::skin))));
        root.then(literal("spawnpoint").then(argument("bot", StringArgumentType.string()).suggests(botNames())
                .executes(ctx -> botSpawnpoint(ctx, here(ctx), false))
                .then(literal("clear").executes(ctx -> botSpawnpoint(ctx, null, true)))
                .then(argument("pos", platform.positionArgument()).executes(ctx -> botSpawnpoint(ctx, pos(ctx), false)))));
        root.then(literal("equip")
                .then(literal("item").then(argument("item", platform.itemArgument()).executes(this::equipItem)))
                .then(literal("armor").then(argument("tier", StringArgumentType.word())
                        .suggests(words("leather", "chainmail", "iron", "gold", "diamond", "netherite")).executes(this::equipArmor))));
        root.then(literal("test").then(argument("ability", StringArgumentType.word()).suggests(words(ABILITIES)).executes(this::test)));

        // groups
        root.then(literal("group").then(argument("group", StringArgumentType.string()).suggests(groupNames())
                .then(literal("role").then(argument("role", StringArgumentType.word()).suggests(words("hunter", "escape")).executes(this::groupRole)))
                .then(literal("enemy")
                        .then(literal("add").then(argument("other", StringArgumentType.string()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, true))))
                        .then(literal("remove").then(argument("other", StringArgumentType.string()).suggests(groupNames()).executes(ctx -> groupEnemy(ctx, false)))))
                .then(literal("spawnpoint")
                        .executes(ctx -> groupSpawnpoint(ctx, here(ctx), false))
                        .then(literal("clear").executes(ctx -> groupSpawnpoint(ctx, null, true)))
                        .then(argument("pos", platform.positionArgument()).executes(ctx -> groupSpawnpoint(ctx, pos(ctx), false))))
                .then(literal("phases").then(argument("enabled", BoolArgumentType.bool()).executes(this::groupPhases)))
                .then(literal("chaseplayers").then(argument("enabled", BoolArgumentType.bool()).executes(this::groupChase)))
                .then(literal("remove").executes(this::groupRemove))));

        // options
        root.then(settingsNode());
        root.then(literal("reload").executes(ctx -> {
            host.reloadAll();
            msg().send(sender(ctx), "command.reloaded", Messages.p("language", msg().language()));
            return Command.SINGLE_SUCCESS;
        }));
        return root.build();
    }

    /**
     * create's options, always in this order: {@code [skin <player>] [at <x y z>] [phase] [aggression]}.
     * Each level of the tree gets the executor and the branches that still fit in it.
     */
    private <T extends ArgumentBuilder<S, T>> T createOptions(T node, boolean skin, boolean at, boolean phase) {
        node.executes(this::create);
        if (skin) {
            node.then(literal("skin").then(createOptions(argument("skin", StringArgumentType.string()).suggests(onlinePlayerNames()), false, true, true)));
        }
        if (at) {
            node.then(literal("at").then(createOptions(argument("pos", platform.positionArgument()), false, false, true)));
        }
        if (phase) {
            node.then(argument("phase", IntegerArgumentType.integer(1, 10)).executes(this::create)
                    .then(argument("aggression", IntegerArgumentType.integer(0, 10)).executes(this::create)));
        }
        return node;
    }

    // ------------------------------------------------------------------ arguments

    /** Optional argument: null when the typed branch doesn't have this argument. */
    private static <V> V opt(CommandContext<?> ctx, String name, Class<V> type) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private GamePlayer player(CommandContext<S> ctx) throws CommandSyntaxException {
        return platform.player(ctx, "player");
    }

    private Place pos(CommandContext<S> ctx) throws CommandSyntaxException {
        return platform.position(ctx, "pos");
    }

    /** Where whoever typed it is; null on the console (no position of its own). */
    private Place here(CommandContext<S> ctx) {
        return platform.executorLocation(ctx.getSource());
    }

    private int noLocation(CommandContext<S> ctx) {
        msg().send(sender(ctx), "command.no-location");
        return 0;
    }

    // ------------------------------------------------------------------ general

    private int help(CommandContext<S> ctx) {
        for (Component line : msg().lines("help")) {
            sender(ctx).sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int menu(CommandContext<S> ctx) {
        GamePlayer p = platform.playerSender(ctx.getSource());
        if (p == null) {
            msg().send(sender(ctx), "command.players-only");
            return 0;
        }
        host.menus().openMain(p);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ match

    private int start(CommandContext<S> ctx) {
        if (manager().rings().releaseAll() == 0) {
            msg().send(sender(ctx), "countdown.nothing");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int pause(CommandContext<S> ctx) {
        if (!manager().pause().pause()) {
            msg().send(sender(ctx), "pause.already-paused");
            return 0;
        }
        host.server().broadcast(msg().prefixed("pause.paused"));
        return Command.SINGLE_SUCCESS;
    }

    private int resume(CommandContext<S> ctx) {
        if (!manager().pause().resume()) {
            msg().send(sender(ctx), "pause.not-paused");
            return 0;
        }
        host.server().broadcast(msg().prefixed("pause.resumed"));
        return Command.SINGLE_SUCCESS;
    }

    private int target(CommandContext<S> ctx) throws CommandSyntaxException {
        GamePlayer p = player(ctx);
        manager().setForcedTarget(p.uuid());
        for (Bot b : manager().aliveBots()) {
            b.setTargetPlayer(p.uuid());
        }
        msg().send(sender(ctx), "command.target-set", Messages.p("player", p.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int targetClear(CommandContext<S> ctx) {
        manager().setForcedTarget(null);
        msg().send(sender(ctx), "command.target-cleared");
        return Command.SINGLE_SUCCESS;
    }

    private int ringInfo(CommandContext<S> ctx) {
        Settings.RingPoint pt = settings().ringPoint();
        msg().send(sender(ctx), "ring.info",
                Messages.p("radius", settings().ringRadius.value() <= 0 ? msg().raw("common.automatic") : settings().ringRadius.display()),
                Messages.p("center", settings().ringCenter.display()),
                Messages.p("point", pt == null ? msg().raw("common.none") : String.format(Locale.ROOT, "%.1f %.1f %.1f (%s)", pt.x(), pt.y(), pt.z(), pt.world())),
                Messages.p("rings", manager().rings().count()));
        return Command.SINGLE_SUCCESS;
    }

    /** Sets the ring point and switches the center to manual. */
    private int ringPoint(CommandContext<S> ctx, Place loc) {
        if (loc == null || loc.world() == null) {
            return noLocation(ctx);
        }
        settings().setRingPoint(new Settings.RingPoint(loc.world().name(), loc.x(), loc.y(), loc.z()));
        settings().ringCenter.set(RingCenter.MANUAL);
        host.onSettingChanged(settings().ringCenter);
        msg().send(sender(ctx), "ring.point-set", Messages.p("world", loc.world().name()),
                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.x(), loc.y(), loc.z())));
        return Command.SINGLE_SUCCESS;
    }

    /** Clears the ring point and switches the center back to automatic. */
    private int ringPointClear(CommandContext<S> ctx) {
        settings().setRingPoint(null);
        settings().ringCenter.set(RingCenter.AUTO);
        host.onSettingChanged(settings().ringCenter);
        msg().send(sender(ctx), "ring.point-cleared");
        return Command.SINGLE_SUCCESS;
    }

    private int playersInfo(CommandContext<S> ctx) {
        List<String> names = settings().playerList();
        msg().send(sender(ctx), "player-list.info", Messages.p("mode", settings().playerListMode.display()),
                Messages.p("names", names.isEmpty() ? msg().raw("player-list.empty") : String.join(", ", names)));
        return Command.SINGLE_SUCCESS;
    }

    private int playersEdit(CommandContext<S> ctx, boolean add) {
        String name = StringArgumentType.getString(ctx, "player");
        boolean changed = add ? settings().addListedPlayer(name) : settings().removeListedPlayer(name);
        if (!changed) {
            msg().send(sender(ctx), add ? "player-list.already-listed" : "player-list.not-listed", Messages.p("player", name));
            return 0;
        }
        host.saveSettings();
        msg().send(sender(ctx), add ? "player-list.added" : "player-list.removed", Messages.p("player", name));
        if (settings().playerListMode.get() == PlayerListMode.OFF) {
            msg().send(sender(ctx), "player-list.off-warning");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int playersMode(CommandContext<S> ctx) {
        if (applySilently(ctx, settings().playerListMode, StringArgumentType.getString(ctx, "mode")) == 0) {
            return 0;
        }
        host.onSettingChanged(settings().playerListMode);
        String mode = settings().playerListMode.display();
        msg().send(sender(ctx), "player-list.mode", Messages.p("mode", mode), Messages.p("explanation", msg().raw("player-list.explanation." + mode)));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ bots

    /** Creates where whoever typed it is (or at the ring point, with the center on manual), or at "at x y z". */
    private int create(CommandContext<S> ctx) throws CommandSyntaxException {
        Audience s = sender(ctx);
        Integer amount = opt(ctx, "amount", Integer.class);
        String diffRaw = opt(ctx, "difficulty", String.class);
        String skin = opt(ctx, "skin", String.class);
        Integer phase = opt(ctx, "phase", Integer.class);
        Integer aggression = opt(ctx, "aggression", Integer.class);
        BotDifficulty difficulty = diffRaw == null ? settings().defaultDifficulty.get() : BotDifficulty.parse(diffRaw);
        if (difficulty == null) {
            msg().send(s, "command.invalid-difficulty");
            return 0;
        }
        Place loc;
        if (opt(ctx, "pos", Object.class) != null) {
            loc = pos(ctx);
        } else {
            if (manager().manualRingPointMissing()) {
                msg().send(s, "ring.no-point");
            }
            loc = manager().ringCenterFor(platform.location(ctx.getSource()));
        }
        if (loc == null || loc.world() == null) {
            return noLocation(ctx);
        }
        String name = StringArgumentType.getString(ctx, "name");
        int count = amount == null ? 1 : amount;
        msg().send(s, "command.creating", Messages.p("amount", count), Messages.p("difficulty", msg().difficulty(difficulty)), Messages.p("name", name));
        manager().createBots(loc, name, skin, count, difficulty, phase == null ? 0 : phase, aggression == null ? 0 : aggression).whenComplete((bots, err) -> {
            if (err != null) {
                host.logger().log(java.util.logging.Level.SEVERE, "Failed to create bots", err);
                msg().send(s, "command.create-error");
            } else {
                msg().send(s, "command.created", Messages.p("amount", bots.size()), Messages.p("name", name));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<S> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "command.bot-not-found");
            return 0;
        }
        for (Bot b : list) {
            manager().removeBot(b);
        }
        msg().send(sender(ctx), "command.removed", Messages.p("amount", list.size()), Messages.p("name", name));
        return Command.SINGLE_SUCCESS;
    }

    private int removeAll(CommandContext<S> ctx) {
        int n = manager().deleteAll();
        msg().send(sender(ctx), "command.all-removed", Messages.p("amount", n));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<S> ctx) {
        Audience s = sender(ctx);
        msg().send(s, "command.list-title", Messages.p("amount", manager().allBots().size()), Messages.p("alive", manager().aliveCount()));
        for (Bot b : manager().allBots()) {
            String where = b.isAlive() ? String.format(Locale.ROOT, "%s %.0f %.0f %.0f", b.world().name(), b.pos().x(), b.pos().y(), b.pos().z())
                    : msg().raw("command.list-dead");
            GamePlayer t = b.target();
            s.sendMessage(msg().get("command.list-line", Messages.p("name", b.name()), Messages.p("display-name", b.visualName()),
                    Messages.p("phase", b.progression().phase()), Messages.p("difficulty", msg().difficulty(b.difficulty())),
                    Messages.p("health", String.format(Locale.ROOT, "%.0f", b.health())), Messages.p("group", b.groupName() == null ? "-" : b.groupName()),
                    Messages.p("target", t == null ? "-" : t.name()), Messages.p("location", where), Messages.p("kills", b.kills()),
                    Messages.c("state", stateOf(b))));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** What the AI is doing, or "paused" with the hunt paused. */
    private Component stateOf(Bot b) {
        if (!b.isAlive()) {
            return Component.text("-");
        }
        return manager().pause().isPaused() ? msg().get("pause.state") : brainState(b);
    }

    private Component brainState(Bot b) {
        return msg().get("state." + b.brain().state(), Messages.p("target", b.brain().stateTarget()), Messages.p("detail", b.brain().stateDetail()));
    }

    private int info(CommandContext<S> ctx) {
        Bot b = manager().bot(StringArgumentType.getString(ctx, "bot"));
        if (b == null) {
            msg().send(sender(ctx), "command.bot-not-found");
            return 0;
        }
        GamePlayer t = b.target();
        sender(ctx).sendMessage(msg().get("command.info", Messages.p("name", b.name()), Messages.p("display-name", b.visualName()),
                Messages.p("phase", b.progression().phase()), Messages.p("difficulty", msg().difficulty(b.difficulty())),
                Messages.p("health", String.format(Locale.ROOT, "%.1f/%.0f", b.health(), b.maxHealth())),
                Messages.p("hunger", b.hunger().foodLevel()), Messages.p("group", b.groupName() == null ? "-" : b.groupName()),
                Messages.p("target", t == null ? "-" : t.name()), Messages.p("kills", b.kills()), Messages.p("aggression", b.aggression()),
                Messages.p("alive", msg().yesNo(b.isAlive())), Messages.c("state", b.isAlive() ? stateOf(b) : brainState(b)),
                Messages.p("location", b.isAlive() ? String.format(Locale.ROOT, "%s %.1f %.1f %.1f", b.world().name(), b.pos().x(), b.pos().y(), b.pos().z()) : "-"),
                Messages.p("speed", String.format(Locale.ROOT, "%.2f", b.velocity().horizontalLength() * 20)),
                Messages.p("on-ground", msg().yesNo(b.onGround()))));
        return Command.SINGLE_SUCCESS;
    }

    /** Brings the bots with this name to the player (or to you). */
    private int tp(CommandContext<S> ctx, GamePlayer to) {
        GamePlayer dest = to != null ? to : platform.playerExecutor(ctx.getSource());
        if (dest == null) {
            msg().send(sender(ctx), "command.players-only");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "bot");
        int n = manager().teleportBotsToPlayer(name, dest);
        if (n == 0) {
            msg().send(sender(ctx), "command.bot-not-found");
            return 0;
        }
        msg().send(sender(ctx), n == 1 ? "command.tp-one" : "command.tp-all", Messages.p("bot", name), Messages.p("amount", n),
                Messages.p("player", dest.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int tpAll(CommandContext<S> ctx, GamePlayer to) {
        GamePlayer dest = to != null ? to : platform.playerExecutor(ctx.getSource());
        if (dest == null) {
            msg().send(sender(ctx), "command.players-only");
            return 0;
        }
        int n = manager().teleportAllTo(dest);
        msg().send(sender(ctx), "command.tp-all", Messages.p("amount", n), Messages.p("player", dest.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int skin(CommandContext<S> ctx) {
        String name = StringArgumentType.getString(ctx, "bot");
        String player = StringArgumentType.getString(ctx, "player");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "command.bot-not-found");
            return 0;
        }
        Audience s = sender(ctx);
        host.skins().fetchPlayerSkin(player).thenAccept(skin -> host.server().runNextTick(() -> {
            if (skin == null) {
                msg().send(s, "command.skin-failed", Messages.p("player", player));
                return;
            }
            for (Bot b : list) {
                b.setSkin(skin);
                host.tabList().refresh(b);
            }
            manager().markDirty();
            msg().send(s, "command.skin-ok", Messages.p("bot", name), Messages.p("player", player));
        }));
        return Command.SINGLE_SUCCESS;
    }

    /** Respawn point of the bots with this name (where you are, at a position, or clear). */
    private int botSpawnpoint(CommandContext<S> ctx, Place loc, boolean clear) {
        String name = StringArgumentType.getString(ctx, "bot");
        List<Bot> list = manager().botsNamed(name);
        if (list.isEmpty()) {
            msg().send(sender(ctx), "command.bot-not-found");
            return 0;
        }
        if (!clear && (loc == null || loc.world() == null)) {
            return noLocation(ctx);
        }
        for (Bot b : list) {
            manager().setCustomSpawnpoint(b, clear ? null : loc);
        }
        if (clear) {
            msg().send(sender(ctx), "command.spawnpoint-bot-cleared", Messages.p("bot", name));
        } else {
            msg().send(sender(ctx), "command.spawnpoint-bot", Messages.p("bot", name),
                    Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.x(), loc.y(), loc.z())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int equipItem(CommandContext<S> ctx) {
        GameItem stack = platform.item(ctx, "item");
        for (Bot b : manager().aliveBots()) {
            b.setItem(EquipSlot.HAND, stack.copy());
            b.inventory().add(stack.copy());
        }
        msg().send(sender(ctx), "command.item-given", Messages.p("item", stack.type().name().toLowerCase(Locale.ROOT)));
        return Command.SINGLE_SUCCESS;
    }

    private int equipArmor(CommandContext<S> ctx) {
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
            msg().send(sender(ctx), "command.invalid-tier", Messages.p("tier", tier));
            return 0;
        }
        for (Bot b : manager().aliveBots()) {
            b.setItem(EquipSlot.HEAD, GameItem.of(ItemType.of(prefix + "_HELMET")));
            b.setItem(EquipSlot.CHEST, GameItem.of(ItemType.of(prefix + "_CHESTPLATE")));
            b.setItem(EquipSlot.LEGS, GameItem.of(ItemType.of(prefix + "_LEGGINGS")));
            b.setItem(EquipSlot.FEET, GameItem.of(ItemType.of(prefix + "_BOOTS")));
        }
        msg().send(sender(ctx), "command.armor", Messages.p("tier", tier));
        return Command.SINGLE_SUCCESS;
    }

    private int test(CommandContext<S> ctx) {
        GamePlayer p = platform.playerSender(ctx.getSource());
        if (p == null) {
            msg().send(sender(ctx), "command.players-only");
            return 0;
        }
        String cap = StringArgumentType.getString(ctx, "ability");
        String err = manager().tactics().debugExecute(p, cap);
        if (err == null) {
            msg().send(sender(ctx), "ability.started", Messages.p("ability", cap));
            return Command.SINGLE_SUCCESS;
        }
        msg().send(sender(ctx), "ability." + err, Messages.p("ability", cap));
        return 0;
    }

    // ------------------------------------------------------------------ groups

    private BotGroup groupArg(CommandContext<S> ctx) {
        String name = StringArgumentType.getString(ctx, "group");
        BotGroup g = manager().group(name);
        if (g == null) {
            msg().send(sender(ctx), "group.not-found", Messages.p("group", name));
        }
        return g;
    }

    private int groupRole(CommandContext<S> ctx) {
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
            msg().send(sender(ctx), "group.invalid-role", Messages.p("role", raw));
            return 0;
        }
        g.setRole(role);
        host.glow().applyAll(manager().aliveBots());
        manager().markDirty();
        msg().send(sender(ctx), "group.role", Messages.p("group", g.name()), Messages.p("role", msg().raw(role == BotRole.ESCAPE ? "role.escape" : "role.hunter")));
        return Command.SINGLE_SUCCESS;
    }

    private int groupEnemy(CommandContext<S> ctx, boolean add) {
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
        msg().send(sender(ctx), add ? "group.enemy-added" : "group.enemy-removed", Messages.p("group", g.name()), Messages.p("enemy", enemy));
        return Command.SINGLE_SUCCESS;
    }

    private int groupSpawnpoint(CommandContext<S> ctx, Place loc, boolean clear) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        if (clear) {
            g.setSpawnpoint(null, null);
            msg().send(sender(ctx), "group.spawnpoint-cleared", Messages.p("group", g.name()));
        } else if (loc == null || loc.world() == null) {
            return noLocation(ctx);
        } else {
            g.setSpawnpoint(loc.world().name(), loc.pos());
            msg().send(sender(ctx), "group.spawnpoint", Messages.p("group", g.name()),
                    Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", loc.x(), loc.y(), loc.z())));
        }
        manager().markDirty();
        return Command.SINGLE_SUCCESS;
    }

    private int groupPhases(CommandContext<S> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        boolean on = BoolArgumentType.getBool(ctx, "enabled");
        int n = manager().setPhaseProgressionForGroup(g.name(), on);
        msg().send(sender(ctx), "group.phases", Messages.p("group", g.name()), Messages.p("value", msg().onOff(on)), Messages.p("amount", n));
        return Command.SINGLE_SUCCESS;
    }

    private int groupChase(CommandContext<S> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        g.setTrackPlayers(BoolArgumentType.getBool(ctx, "enabled"));
        manager().markDirty();
        msg().send(sender(ctx), "group.chase", Messages.p("group", g.name()), Messages.p("value", msg().yesNo(g.trackPlayers())));
        return Command.SINGLE_SUCCESS;
    }

    private int groupRemove(CommandContext<S> ctx) {
        BotGroup g = groupArg(ctx);
        if (g == null) {
            return 0;
        }
        int n = manager().deleteGroup(g.name());
        msg().send(sender(ctx), "group.deleted", Messages.p("group", g.name()), Messages.p("amount", n));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ options

    private LiteralArgumentBuilder<S> settingsNode() {
        SuggestionProvider<S> keys = (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (Setting<?> s : settings().all()) {
                if (s.key().startsWith(rem)) {
                    b.suggest(s.key());
                }
            }
            return b.buildFuture();
        };
        SuggestionProvider<S> values = (ctx, b) -> {
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
        return literal("settings")
                .executes(this::listSettings)
                .then(argument("key", StringArgumentType.word()).suggests(keys)
                        .executes(this::showSetting)
                        .then(argument("value", StringArgumentType.greedyString()).suggests(values)
                                .executes(this::changeSetting)));
    }

    private int listSettings(CommandContext<S> ctx) {
        Audience s = sender(ctx);
        msg().send(s, "settings.title");
        for (Setting<?> st : settings().all()) {
            s.sendMessage(msg().get("settings.line", Messages.p("key", st.key()), Messages.p("value", st.display()), Messages.p("path", st.path())));
        }
        s.sendMessage(msg().get("settings.line", Messages.p("key", msg().raw("settings.effective-nodes")),
                Messages.p("value", host.pathService().effectiveNodeCap()), Messages.p("path", settings().nodeCap.path())));
        return Command.SINGLE_SUCCESS;
    }

    private Setting<?> settingArg(CommandContext<S> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        Setting<?> s = settings().byKey(key);
        if (s == null) {
            msg().send(sender(ctx), "settings.unknown", Messages.p("key", key));
        }
        return s;
    }

    private int showSetting(CommandContext<S> ctx) {
        Setting<?> s = settingArg(ctx);
        if (s == null) {
            return 0;
        }
        msg().send(sender(ctx), "settings.current", Messages.p("key", s.key()), Messages.p("value", s.display()));
        return Command.SINGLE_SUCCESS;
    }

    private int changeSetting(CommandContext<S> ctx) {
        Setting<?> s = settingArg(ctx);
        if (s == null) {
            return 0;
        }
        String v = StringArgumentType.getString(ctx, "value").trim();
        if (s == settings().ringRadius && v.equalsIgnoreCase("auto")) {
            v = "0";
        }
        if (s == settings().glowHunters || s == settings().glowEscapers) {
            // "glowhunters true red": turns it on and changes the color at once
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
        host.onSettingChanged(s);
        msg().send(sender(ctx), "settings.changed", Messages.p("key", s.key()), Messages.p("value", s.display()));
        return Command.SINGLE_SUCCESS;
    }

    private int applySilently(CommandContext<S> ctx, Setting<?> s, String raw) {
        try {
            s.setFromText(raw);
            return Command.SINGLE_SUCCESS;
        } catch (Setting.InvalidValue e) {
            msg().send(sender(ctx), "settings.invalid", Messages.p("key", s.key()), Messages.c("error", msg().invalidValue(e)));
            return 0;
        }
    }
}
