package dev.manhuntstar.gui;

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
import dev.manhuntstar.config.Setting.Category;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.host.Menus;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.Sounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * {@code /manhunt menu}: chest menus in place of the mod client's screen, in four sections.
 * Match (start, pause, forced target), Hunt rules (ring, countdown, late joiners, player list),
 * Bots (create, manage, groups, bring, delete) and Settings (the other option categories). Text
 * (bot name, skin, player) is typed in chat; the menu text comes from the language file
 * ({@code menu.*}).
 */
public final class MenuManager implements Menus {

    private static final List<Category> CONFIG_CATEGORIES = List.of(Category.GENERAL, Category.BOTS, Category.COMBAT,
            Category.MOBILITY, Category.PATHFINDING, Category.TACTICS, Category.APPEARANCE);

    private final ManhuntHost host;
    private final MenuScreens screens;
    private final Map<UUID, SpawnDraft> drafts = new HashMap<>();
    /** Read by the chat event, which runs off the main thread. */
    private final Map<UUID, Consumer<String>> chatInput = new ConcurrentHashMap<>();

    private static final class SpawnDraft {
        String name = "Terminator";
        String skin = "";
        int amount = 1;
        BotDifficulty difficulty;
        int phase = 0;
        int aggression = 0;
    }

    public MenuManager(ManhuntHost host, MenuScreens screens) {
        this.host = host;
        this.screens = screens;
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

    private static MenuIcon item(ItemType type, Component name, List<Component> lore) {
        return icon(type, name, lore, null);
    }

    /** The name and lines without the italics the game gives to named items. */
    private static MenuIcon icon(ItemType type, Component name, List<Component> lore, GamePlayer head) {
        List<Component> l = new ArrayList<>();
        for (Component c : lore) {
            l.add(c.decoration(TextDecoration.ITALIC, false));
        }
        return new MenuIcon(type, name.decoration(TextDecoration.ITALIC, false), l, head);
    }

    /** Text from the language file, in the menu's default color when the text has no color. */
    private Component t(String key, NamedTextColor color, TagResolver... resolvers) {
        return msg().get(key, resolvers).colorIfAbsent(color);
    }

    /** Multi-line text from the language file, one lore line per line. */
    private List<Component> lines(String key, NamedTextColor color, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (Component line : msg().lines(key, resolvers)) {
            out.add(line.colorIfAbsent(color));
        }
        return out;
    }

    /** Text that does not come from the language file (player name, group name, command). */
    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    @SafeVarargs
    private static List<Component> join(List<Component>... parts) {
        List<Component> out = new ArrayList<>();
        for (List<Component> part : parts) {
            out.addAll(part);
        }
        return out;
    }

    private Menu create(int rows, String titleKey) {
        return new Menu(msg().get(titleKey), rows, item(MenuItems.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of()));
    }

    private void set(Menu m, int slot, MenuIcon it, Consumer<MenuClick> action) {
        m.set(slot, it, action);
    }

    /** "Back" button in the middle of the last row. */
    private void back(Menu m, Runnable to) {
        set(m, m.size() - 5, item(MenuItems.OAK_DOOR, t("menu.back", NamedTextColor.GRAY), List.of()), c -> to.run());
    }

    private void click(GamePlayer p) {
        p.playSound(p.position(), Sounds.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1f);
    }

    private String forcedTargetName() {
        UUID forced = manager().forcedTarget();
        GamePlayer fp = forced == null ? null : host.server().player(forced);
        return fp == null ? msg().raw("common.none") : fp.name();
    }

    // ------------------------------------------------------------------ main menu

    @Override
    public void openMain(GamePlayer p) {
        Menu m = create(3, "menu.title.main");
        set(m, 4, item(MenuItems.COMPASS, plain("Manhunt*", NamedTextColor.DARK_RED), status()), null);
        set(m, 10, item(MenuItems.CLOCK, t("menu.main.match", NamedTextColor.GREEN),
                lines("menu.main.match-lore", NamedTextColor.GRAY)), c -> openMatch(p));
        set(m, 12, item(MenuItems.WRITABLE_BOOK, t("menu.main.rules", NamedTextColor.GOLD),
                lines("menu.main.rules-lore", NamedTextColor.GRAY)), c -> openRules(p));
        set(m, 14, item(MenuItems.ZOMBIE_HEAD, t("menu.main.bots", NamedTextColor.AQUA),
                lines("menu.main.bots-lore", NamedTextColor.GRAY)), c -> openBotsSection(p));
        set(m, 16, item(MenuItems.COMPARATOR, t("menu.main.config", NamedTextColor.YELLOW),
                lines("menu.main.config-lore", NamedTextColor.GRAY)), c -> openConfig(p));
        set(m, 22, item(MenuItems.BARRIER, t("menu.close", NamedTextColor.GRAY), List.of()), c -> screens.close(p));
        screens.open(p, m);
    }

    /** Summary at the top of the main menu: bots, hunt state, forced target and list. */
    private List<Component> status() {
        BotManager bm = manager();
        List<Component> lore = new ArrayList<>();
        lore.add(t("menu.main.status-bots", NamedTextColor.GRAY, Messages.p("alive", bm.aliveCount()), Messages.p("total", bm.allBots().size())));
        lore.add(t("menu.main.status-hunt", NamedTextColor.GRAY, Messages.c("state", huntState())));
        lore.add(t("menu.main.status-target", NamedTextColor.GRAY, Messages.p("target", forcedTargetName())));
        lore.add(t("menu.main.status-list", NamedTextColor.GRAY, Messages.p("mode", settings().playerListMode.display())));
        return lore;
    }

    private Component huntState() {
        BotManager bm = manager();
        if (bm.pause().isPaused()) {
            return t("menu.main.hunt-paused", NamedTextColor.YELLOW);
        }
        if (bm.rings().isActive()) {
            return t("menu.main.hunt-rings", NamedTextColor.GOLD, Messages.p("rings", bm.rings().count()));
        }
        if (bm.policy().isActive()) {
            return t("menu.main.hunt-running", NamedTextColor.RED);
        }
        return t(bm.allBots().isEmpty() ? "menu.main.hunt-no-bots" : "menu.main.hunt-stopped", NamedTextColor.WHITE);
    }

    // ------------------------------------------------------------------ match

    private void openMatch(GamePlayer p) {
        BotManager bm = manager();
        Menu m = create(3, "menu.title.match");
        int rings = bm.rings().count();
        set(m, 11, item(MenuItems.LIME_DYE, t("menu.match.start", NamedTextColor.GREEN),
                List.of(rings == 0 ? t("menu.match.start-none", NamedTextColor.GRAY) : t("menu.match.start-rings", NamedTextColor.GRAY, Messages.p("rings", rings)),
                        plain("/manhunt start", NamedTextColor.DARK_GRAY))), c -> {
                    screens.close(p);
                    if (bm.rings().releaseAll() == 0) {
                        msg().send(p, "countdown.nothing");
                    }
                });
        boolean paused = bm.pause().isPaused();
        set(m, 13, item(paused ? MenuItems.MAGMA_CREAM : MenuItems.BLUE_ICE,
                t(paused ? "menu.match.resume" : "menu.match.pause", paused ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                join(lines(paused ? "menu.match.resume-lore" : "menu.match.pause-lore", NamedTextColor.GRAY),
                        List.of(plain(paused ? "/manhunt resume" : "/manhunt pause", NamedTextColor.DARK_GRAY)))), c -> {
                    if (paused) {
                        if (bm.pause().resume()) {
                            host.server().broadcast(msg().prefixed("pause.resumed"));
                        }
                    } else if (bm.pause().pause()) {
                        host.server().broadcast(msg().prefixed("pause.paused"));
                    }
                    openMatch(p);
                });
        set(m, 15, item(MenuItems.TARGET, t("menu.match.target", NamedTextColor.RED, Messages.p("target", forcedTargetName())),
                join(lines("menu.match.target-lore", NamedTextColor.GRAY), List.of(plain("/manhunt target", NamedTextColor.DARK_GRAY)))),
                c -> openTargetPicker(p));
        back(m, () -> openMain(p));
        screens.open(p, m);
    }

    private void openTargetPicker(GamePlayer p) {
        Menu m = create(6, "menu.title.target");
        int i = 0;
        for (GamePlayer target : host.server().onlinePlayers()) {
            if (i >= 45) {
                break;
            }
            set(m, i++, head(target.name(), target, NamedTextColor.WHITE, lines("menu.target.pick", NamedTextColor.GRAY)), c -> {
                manager().setForcedTarget(target.uuid());
                for (Bot b : manager().aliveBots()) {
                    b.setTargetPlayer(target.uuid());
                }
                msg().send(p, "command.target-set", Messages.p("player", target.name()));
                openMatch(p);
            });
        }
        set(m, 45, item(MenuItems.MILK_BUCKET, t("menu.target.clear", NamedTextColor.GREEN),
                lines("menu.target.clear-lore", NamedTextColor.GRAY)), c -> {
                    manager().setForcedTarget(null);
                    msg().send(p, "command.target-cleared");
                    openMatch(p);
                });
        back(m, () -> openMatch(p));
        screens.open(p, m);
    }

    // ------------------------------------------------------------------ hunt rules

    private void openRules(GamePlayer p) {
        List<Setting<?>> list = settings().inCategory(Category.MANHUNT);
        Menu m = create(4, "menu.title.rules");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openRules(p));
        }
        Settings.RingPoint pt = settings().ringPoint();
        set(m, 21, item(MenuItems.LODESTONE, t("menu.rules.ring-point", NamedTextColor.GOLD), join(
                List.of(pt == null ? t("menu.rules.ring-point-none", NamedTextColor.WHITE)
                        : plain(String.format(Locale.ROOT, "%.0f %.0f %.0f (%s)", pt.x(), pt.y(), pt.z(), pt.world()), NamedTextColor.WHITE)),
                lines("menu.rules.ring-point-lore", NamedTextColor.GRAY),
                List.of(plain("/manhunt ring point", NamedTextColor.DARK_GRAY)))), c -> {
                    if (c.right()) {
                        settings().setRingPoint(null);
                        settings().ringCenter.set(RingCenter.AUTO);
                        msg().send(p, "ring.point-cleared");
                    } else {
                        Place l = p.place();
                        settings().setRingPoint(new Settings.RingPoint(l.world().name(), l.x(), l.y(), l.z()));
                        settings().ringCenter.set(RingCenter.MANUAL);
                        msg().send(p, "ring.point-set", Messages.p("world", l.world().name()),
                                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", l.x(), l.y(), l.z())));
                    }
                    host.onSettingChanged(settings().ringCenter);
                    openRules(p);
                });
        List<String> names = settings().playerList();
        set(m, 23, item(MenuItems.NAME_TAG, t("menu.rules.player-list", NamedTextColor.GOLD), List.of(
                t("menu.rules.player-list-mode", NamedTextColor.WHITE, Messages.p("mode", settings().playerListMode.display())),
                names.isEmpty() ? t("menu.rules.player-list-empty", NamedTextColor.GRAY)
                        : t("menu.rules.player-list-count", NamedTextColor.GRAY, Messages.p("amount", names.size())),
                t("menu.rules.click-to-edit", NamedTextColor.GRAY),
                plain("/manhunt players", NamedTextColor.DARK_GRAY))), c -> openPlayerList(p));
        back(m, () -> openMain(p));
        screens.open(p, m);
    }

    /** Online players and the ones on the list: clicking adds them to or removes them from the list. */
    private void openPlayerList(GamePlayer p) {
        Menu m = create(6, "menu.title.player-list");
        Map<String, GamePlayer> people = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : settings().playerList()) {
            people.put(n, null);
        }
        for (GamePlayer online : host.server().onlinePlayers()) {
            people.put(online.name(), online);
        }
        int i = 0;
        for (Map.Entry<String, GamePlayer> e : people.entrySet()) {
            if (i >= 45) {
                break;
            }
            String name = e.getKey();
            boolean listed = settings().isListed(name);
            set(m, i++, head(name, e.getValue(), listed ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                    t(listed ? "menu.player-list.listed" : "menu.player-list.not-listed", listed ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                    t(listed ? "menu.player-list.click-remove" : "menu.player-list.click-add", NamedTextColor.DARK_GRAY))), c -> {
                        boolean changed = listed ? settings().removeListedPlayer(name) : settings().addListedPlayer(name);
                        if (changed) {
                            host.saveSettings();
                        }
                        openPlayerList(p);
                    });
        }
        PlayerListMode mode = settings().playerListMode.get();
        set(m, 47, item(MenuItems.PAPER, t("menu.player-list.mode", NamedTextColor.YELLOW, Messages.p("mode", mode.configName())), List.of(
                t("player-list.explanation." + mode.configName(), NamedTextColor.GRAY),
                t("menu.player-list.click-to-change", NamedTextColor.DARK_GRAY))), c -> {
                    settings().playerListMode.setFromText(settings().playerListMode.next().configName());
                    host.onSettingChanged(settings().playerListMode);
                    openPlayerList(p);
                });
        set(m, 51, item(MenuItems.WRITABLE_BOOK, t("menu.player-list.add-by-name", NamedTextColor.WHITE),
                lines("menu.player-list.add-by-name-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-player", text -> {
                    if (settings().addListedPlayer(text)) {
                        host.saveSettings();
                    }
                    openPlayerList(p);
                }));
        back(m, () -> openRules(p));
        screens.open(p, m);
    }

    /** Player head; with the player online, already wearing their skin. */
    private static MenuIcon head(String name, GamePlayer online, NamedTextColor color, List<Component> lore) {
        return icon(MenuItems.PLAYER_HEAD, plain(name, color), lore, online);
    }

    // ------------------------------------------------------------------ bots

    private void openBotsSection(GamePlayer p) {
        BotManager bm = manager();
        Menu m = create(4, "menu.title.bots-section");
        set(m, 11, item(MenuItems.SPAWNER, t("menu.bots-section.create", NamedTextColor.GREEN),
                lines("menu.bots-section.create-lore", NamedTextColor.GRAY)), c -> openSpawner(p));
        set(m, 13, item(MenuItems.PLAYER_HEAD, t("menu.bots-section.manage", NamedTextColor.AQUA),
                lines("menu.bots-section.manage-lore", NamedTextColor.GRAY, Messages.p("alive", bm.aliveCount()), Messages.p("total", bm.allBots().size()))),
                c -> openBots(p, 0));
        set(m, 15, item(MenuItems.WHITE_BANNER, t("menu.bots-section.groups", NamedTextColor.GOLD),
                lines("menu.bots-section.groups-lore", NamedTextColor.GRAY)), c -> openGroups(p));
        set(m, 20, item(MenuItems.ENDER_PEARL, t("menu.bots-section.bring", NamedTextColor.LIGHT_PURPLE),
                List.of(plain("/manhunt tp all", NamedTextColor.DARK_GRAY))), c -> {
                    int n = bm.teleportAllTo(p);
                    msg().send(p, "command.tp-all", Messages.p("amount", n), Messages.p("player", p.name()));
                });
        set(m, 24, item(MenuItems.LAVA_BUCKET, t("menu.bots-section.delete", NamedTextColor.RED),
                join(lines("menu.bots-section.delete-lore", NamedTextColor.GRAY), List.of(plain("/manhunt remove all", NamedTextColor.DARK_GRAY)))),
                c -> openConfirmDelete(p));
        back(m, () -> openMain(p));
        screens.open(p, m);
    }

    private void openConfirmDelete(GamePlayer p) {
        Menu m = create(3, "menu.title.confirm");
        set(m, 11, item(MenuItems.RED_CONCRETE, t("menu.confirm.yes", NamedTextColor.RED), List.of()), c -> {
            int n = manager().deleteAll();
            msg().send(p, "command.all-removed", Messages.p("amount", n));
            screens.close(p);
        });
        set(m, 15, item(MenuItems.GREEN_CONCRETE, t("menu.confirm.no", NamedTextColor.GREEN), List.of()), c -> openBotsSection(p));
        screens.open(p, m);
    }

    private void openSpawner(GamePlayer p) {
        SpawnDraft d = drafts.computeIfAbsent(p.uuid(), k -> new SpawnDraft());
        if (d.difficulty == null) {
            d.difficulty = settings().defaultDifficulty.get();
        }
        Menu m = create(3, "menu.title.create");
        set(m, 10, item(MenuItems.NAME_TAG, t("menu.create.name", NamedTextColor.WHITE, Messages.p("name", d.name)),
                lines("menu.create.name-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-name", text -> {
                    d.name = text;
                    openSpawner(p);
                }));
        set(m, 11, item(MenuItems.PLAYER_HEAD, t("menu.create.skin", NamedTextColor.WHITE,
                        Messages.p("skin", d.skin.isEmpty() ? msg().raw("menu.create.skin-same") : d.skin)),
                lines("menu.create.skin-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-skin", text -> {
                    d.skin = text.equals("-") ? "" : text;
                    openSpawner(p);
                }));
        set(m, 12, item(MenuItems.SLIME_BALL, t("menu.create.amount", NamedTextColor.WHITE, Messages.p("amount", d.amount)),
                lines("menu.create.amount-lore", NamedTextColor.GRAY)), c -> {
                    int step = c.shift() ? 5 : 1;
                    d.amount = Math.max(1, Math.min(100, d.amount + (c.left() ? step : -step)));
                    openSpawner(p);
                });
        set(m, 13, item(MenuItems.NETHERITE_SWORD, t("menu.create.difficulty", NamedTextColor.WHITE, Messages.p("difficulty", msg().difficulty(d.difficulty))),
                lines("menu.create.difficulty-lore", NamedTextColor.GRAY)), c -> {
                    BotDifficulty[] all = BotDifficulty.values();
                    d.difficulty = all[(d.difficulty.ordinal() + 1) % all.length];
                    openSpawner(p);
                });
        set(m, 14, item(MenuItems.EXPERIENCE_BOTTLE, t("menu.create.phase", NamedTextColor.WHITE,
                        Messages.p("phase", d.phase == 0 ? msg().raw("menu.create.phase-default") : String.valueOf(d.phase))),
                lines("menu.create.phase-lore", NamedTextColor.GRAY)), c -> {
                    d.phase = Math.max(0, Math.min(10, d.phase + (c.left() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 15, item(MenuItems.BLAZE_POWDER, t("menu.create.aggression", NamedTextColor.WHITE, Messages.p("aggression", d.aggression)),
                lines("menu.create.aggression-lore", NamedTextColor.GRAY)), c -> {
                    d.aggression = Math.max(0, Math.min(10, d.aggression + (c.left() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 16, item(MenuItems.LIME_CONCRETE, t("menu.create.spawn", NamedTextColor.GREEN),
                lines("menu.create.spawn-lore", NamedTextColor.GRAY, Messages.p("mode", settings().spawnMode.display()))), c -> {
                    screens.close(p);
                    if (manager().manualRingPointMissing()) {
                        msg().send(p, "ring.no-point");
                    }
                    msg().send(p, "command.creating", Messages.p("amount", d.amount), Messages.p("difficulty", msg().difficulty(d.difficulty)),
                            Messages.p("name", d.name));
                    manager().createBots(manager().ringCenterFor(p.place()), d.name, d.skin.isEmpty() ? null : d.skin, d.amount, d.difficulty, d.phase, d.aggression)
                            .whenComplete((bots, err) -> {
                                if (err != null) {
                                    host.logger().log(java.util.logging.Level.SEVERE, "Failed to create bots", err);
                                    msg().send(p, "command.create-error");
                                } else {
                                    msg().send(p, "command.created", Messages.p("amount", bots.size()), Messages.p("name", d.name));
                                }
                            });
                });
        back(m, () -> openBotsSection(p));
        screens.open(p, m);
    }

    private void openBots(GamePlayer p, int page) {
        List<Bot> bots = new ArrayList<>(manager().allBots());
        Menu m = create(6, "menu.title.bots");
        int per = 45;
        int start = page * per;
        for (int i = 0; i < per && start + i < bots.size(); i++) {
            Bot b = bots.get(start + i);
            GamePlayer target = b.target();
            MenuIcon head = item(MenuItems.PLAYER_HEAD, plain(b.visualName() + " (" + b.name() + ")", b.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(
                    t("menu.bots.phase", NamedTextColor.GRAY, Messages.p("phase", b.progression().phase()), Messages.p("difficulty", msg().difficulty(b.difficulty()))),
                    t("menu.bots.health", NamedTextColor.GRAY, Messages.p("health", String.format(Locale.ROOT, "%.0f", b.health())), Messages.p("kills", b.kills())),
                    t("menu.bots.target", NamedTextColor.GRAY, Messages.p("target", target == null ? "-" : target.name())),
                    t("menu.bots.help", NamedTextColor.DARK_GRAY)));
            set(m, i, head, c -> {
                if (c.right() && c.shift()) {
                    manager().removeBot(b);
                    openBots(p, page);
                } else if (c.right()) {
                    if (b.isAlive()) {
                        manager().teleportBot(b, p.place());
                    }
                } else if (b.isAlive()) {
                    p.teleport(b.location());
                    screens.close(p);
                }
            });
        }
        if (page > 0) {
            set(m, 45, item(MenuItems.ARROW, t("menu.bots.previous", NamedTextColor.GRAY), List.of()), c -> openBots(p, page - 1));
        }
        back(m, () -> openBotsSection(p));
        if (start + per < bots.size()) {
            set(m, 53, item(MenuItems.ARROW, t("menu.bots.next", NamedTextColor.GRAY), List.of()), c -> openBots(p, page + 1));
        }
        screens.open(p, m);
    }

    private void openGroups(GamePlayer p) {
        Menu m = create(6, "menu.title.groups");
        int i = 0;
        for (BotGroup g : manager().groups()) {
            if (i >= 45) {
                break;
            }
            boolean escape = g.role() == BotRole.ESCAPE;
            set(m, i++, item(escape ? MenuItems.RABBIT_FOOT : MenuItems.IRON_SWORD, plain(g.name(), NamedTextColor.GOLD), join(List.of(
                    t("menu.groups.role", NamedTextColor.GRAY, Messages.p("role", msg().raw(escape ? "role.escape" : "role.hunter"))),
                    t("menu.groups.chase", NamedTextColor.GRAY, Messages.p("value", msg().yesNo(g.trackPlayers()))),
                    t("menu.groups.enemies", NamedTextColor.GRAY, Messages.p("enemies", g.enemies().isEmpty() ? "-" : String.join(", ", g.enemies())))),
                    lines("menu.groups.help", NamedTextColor.DARK_GRAY))), c -> {
                        if (c.right() && c.shift()) {
                            manager().deleteGroup(g.name());
                        } else if (c.right()) {
                            g.setTrackPlayers(!g.trackPlayers());
                        } else {
                            g.setRole(escape ? BotRole.HUNTER : BotRole.ESCAPE);
                            host.glow().applyAll(manager().aliveBots());
                        }
                        manager().markDirty();
                        openGroups(p);
                    });
        }
        back(m, () -> openBotsSection(p));
        screens.open(p, m);
    }

    // ------------------------------------------------------------------ settings

    private void openConfig(GamePlayer p) {
        Menu m = create(3, "menu.title.config");
        ItemType[] icons = {MenuItems.BOOK, MenuItems.ARMOR_STAND, MenuItems.IRON_SWORD, MenuItems.FEATHER, MenuItems.MAP, MenuItems.TNT, MenuItems.GLOWSTONE_DUST};
        int slot = 10;
        for (int i = 0; i < CONFIG_CATEGORIES.size(); i++) {
            Category cat = CONFIG_CATEGORIES.get(i);
            set(m, slot++, item(icons[i], t("category." + cat.name().toLowerCase(Locale.ROOT), NamedTextColor.YELLOW),
                    lines("menu.config.category-lore", NamedTextColor.GRAY, Messages.p("amount", settings().inCategory(cat).size()))), c -> openSettings(p, cat));
        }
        setGlowButton(p, m, 4);
        back(m, () -> openMain(p));
        screens.open(p, m);
    }

    /** Shortcut for the hunters' glow: left click toggles it, right click changes the color. */
    private void setGlowButton(GamePlayer p, Menu m, int slot) {
        Setting.Bool glow = settings().glowHunters;
        Setting.Enumerated<GlowColor> color = settings().glowHuntersColor;
        boolean on = glow.on();
        set(m, slot, item(on ? MenuItems.GLOW_INK_SAC : MenuItems.INK_SAC,
                t("menu.config.glow", on ? NamedTextColor.GREEN : NamedTextColor.GRAY, Messages.p("name", msg().optionName(glow)), Messages.p("value", msg().onOff(on))),
                join(List.of(t("menu.config.glow-color", NamedTextColor.GRAY, Messages.c("color", plain(color.display(), color.get().color())))),
                        lines("menu.config.glow-help", NamedTextColor.DARK_GRAY))), c -> {
                    Setting<?> changed;
                    if (c.right()) {
                        color.setFromText(color.next().configName());
                        changed = color;
                    } else {
                        glow.set(!on);
                        changed = glow;
                    }
                    host.onSettingChanged(changed);
                    openConfig(p);
                });
    }

    private void openSettings(GamePlayer p, Category cat) {
        List<Setting<?>> list = settings().inCategory(cat);
        Menu m = create(Math.max(2, (list.size() + 8) / 9 + 1), "menu.title.options");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openSettings(p, cat));
        }
        back(m, () -> openConfig(p));
        screens.open(p, m);
    }

    /** An option as an item: name, description from the language file, value and command; clicking changes the value. */
    private void setSetting(Menu m, int slot, Setting<?> s, Runnable reopen) {
        ItemType icon;
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(msg().raw("options." + s.key() + ".description"), 40)) {
            lore.add(plain(line, NamedTextColor.GRAY));
        }
        String value = s instanceof Setting.Bool b ? msg().onOff(b.on()) : s.display();
        lore.add(t("menu.option.value", NamedTextColor.WHITE, Messages.p("value", value)));
        lore.add(plain("/manhunt settings " + s.key(), NamedTextColor.DARK_GRAY));
        if (s instanceof Setting.Bool b) {
            icon = b.on() ? MenuItems.LIME_DYE : MenuItems.GRAY_DYE;
            lore.add(t("menu.option.toggle", NamedTextColor.GRAY));
        } else if (s instanceof Setting.Enumerated<?> || s instanceof Setting.Text) {
            icon = MenuItems.PAPER;
            lore.add(t("menu.option.next", NamedTextColor.GRAY));
        } else {
            icon = MenuItems.REPEATER;
            lore.add(t("menu.option.adjust", NamedTextColor.GRAY));
        }
        set(m, slot, item(icon, plain(msg().optionName(s), NamedTextColor.YELLOW), lore), c -> {
            adjust(s, c);
            host.onSettingChanged(s);
            reopen.run();
        });
    }

    /**
     * Wraps the description into lines of up to {@code width} letters. A word longer than the line
     * (text without spaces, as in Chinese) is cut in the middle.
     */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.codePointCount(0, word.length()) > width) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                int cut = word.offsetByCodePoints(0, width);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() > 0 && line.codePointCount(0, line.length()) + 1 + word.codePointCount(0, word.length()) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void adjust(Setting<?> s, MenuClick c) {
        int sign = c.right() ? -1 : 1;
        if (s instanceof Setting.Bool b) {
            b.set(!b.on());
        } else if (s instanceof Setting.Enumerated<?> e) {
            e.setFromText(e.next().configName());
        } else if (s instanceof Setting.Text text) {
            List<String> options = text.suggestions();
            if (!options.isEmpty()) {
                int at = options.indexOf(text.get());
                text.set(options.get(Math.floorMod(at + sign, options.size())));
            }
        } else if (s instanceof Setting.Int in) {
            int range = in.max() - in.min();
            int step = Math.max(1, range >= 1000 ? (c.shift() ? 500 : 50) : range >= 100 ? (c.shift() ? 50 : 5) : (c.shift() ? 10 : 1));
            in.set(in.value() + sign * step);
        } else if (s instanceof Setting.Dbl d) {
            double step = d.step() * (c.shift() ? 10 : 1);
            d.set(Math.round((d.value() + sign * step) * 1000.0) / 1000.0);
        }
    }

    // ------------------------------------------------------------------ events

    private void askChat(GamePlayer p, String key, Consumer<String> then) {
        screens.close(p);
        chatInput.put(p.uuid(), then);
        msg().send(p, key);
    }

    /**
     * What a player typed in chat (on any thread, already as plain text without the spaces around it).
     * True when a menu was waiting for it: the message then doesn't go to the chat.
     */
    public boolean chatTyped(GamePlayer player, String text) {
        Consumer<String> handler = chatInput.remove(player.uuid());
        if (handler == null) {
            return false;
        }
        host.server().runNextTick(() -> {
            if (!text.isEmpty() && !text.equalsIgnoreCase("cancel") && !text.equalsIgnoreCase(msg().raw("menu.cancel-word"))) {
                handler.accept(text);
            } else {
                msg().send(player, "menu.cancelled");
            }
        });
        return true;
    }

    /** A player clicked a slot of an open menu. */
    public void clicked(GamePlayer p, Menu menu, int slot, MenuClick click) {
        Consumer<MenuClick> action = menu.action(slot);
        if (action != null) {
            click(p);
            action.accept(click);
        }
    }

    /** The player left: forgets what they were typing and their bot draft. */
    public void forget(UUID player) {
        chatInput.remove(player);
        drafts.remove(player);
    }
}
