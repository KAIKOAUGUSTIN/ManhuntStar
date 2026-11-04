package dev.manhuntstar.gui;

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
import dev.manhuntstar.config.Setting.Category;
import dev.manhuntstar.config.Settings;
import io.papermc.paper.event.player.AsyncChatEvent;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * {@code /manhunt menu}: menus em baú no lugar da tela do cliente do mod, em quatro seções.
 * Partida (começar, pausar, alvo forçado), Regras da caçada (roda, contagem, quem entra no meio,
 * lista de jogadores), Bots (criar, gerenciar, grupos, trazer, apagar) e Configurações (as outras
 * categorias de opções). Textos (nome do bot, skin, jogador) são digitados no chat; os textos do
 * menu vêm do arquivo de idioma ({@code menu.*}).
 */
public final class MenuManager implements Listener {

    private static final List<Category> CONFIG_CATEGORIES = List.of(Category.GENERAL, Category.BOTS, Category.COMBAT,
            Category.MOBILITY, Category.PATHFINDING, Category.TACTICS, Category.APPEARANCE);

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, SpawnDraft> drafts = new HashMap<>();
    /** Lido pelo evento de chat, que roda fora da thread principal. */
    private final Map<UUID, Consumer<String>> chatInput = new ConcurrentHashMap<>();

    private static final class SpawnDraft {
        String name = "Terminator";
        String skin = "";
        int amount = 1;
        BotDifficulty difficulty;
        int phase = 0;
        int aggression = 0;
    }

    /** Inventário de menu: guarda a ação de cada slot. */
    private static final class Menu implements InventoryHolder {
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public MenuManager(ManhuntStarPlugin plugin) {
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

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (Component c : lore) {
            l.add(c.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(l);
        s.setItemMeta(meta);
        return s;
    }

    /** Texto do arquivo de idioma, na cor padrão do menu quando o texto não traz cor. */
    private Component t(String key, NamedTextColor color, TagResolver... resolvers) {
        return msg().get(key, resolvers).colorIfAbsent(color);
    }

    /** Texto de várias linhas do arquivo de idioma, uma linha de lore por linha. */
    private List<Component> lines(String key, NamedTextColor color, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (Component line : msg().lines(key, resolvers)) {
            out.add(line.colorIfAbsent(color));
        }
        return out;
    }

    /** Texto que não vem do idioma (nome de jogador, de grupo, comando). */
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
        Menu m = new Menu();
        m.inventory = Bukkit.createInventory(m, rows * 9, msg().get(titleKey));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < rows * 9; i++) {
            m.inventory.setItem(i, filler);
        }
        return m;
    }

    private void set(Menu m, int slot, ItemStack it, Consumer<ClickType> action) {
        m.inventory.setItem(slot, it);
        if (action != null) {
            m.actions.put(slot, action);
        }
    }

    /** Botão "Voltar" no meio da última linha. */
    private void back(Menu m, Runnable to) {
        set(m, m.inventory.getSize() - 5, item(Material.OAK_DOOR, t("menu.back", NamedTextColor.GRAY), List.of()), c -> to.run());
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    private String forcedTargetName() {
        UUID forced = manager().forcedTarget();
        Player fp = forced == null ? null : Bukkit.getPlayer(forced);
        return fp == null ? msg().raw("common.none") : fp.getName();
    }

    // ------------------------------------------------------------------ menu principal

    public void openMain(Player p) {
        Menu m = create(3, "menu.title.main");
        set(m, 4, item(Material.COMPASS, plain("Manhunt*", NamedTextColor.DARK_RED), status()), null);
        set(m, 10, item(Material.CLOCK, t("menu.main.match", NamedTextColor.GREEN),
                lines("menu.main.match-lore", NamedTextColor.GRAY)), c -> openMatch(p));
        set(m, 12, item(Material.WRITABLE_BOOK, t("menu.main.rules", NamedTextColor.GOLD),
                lines("menu.main.rules-lore", NamedTextColor.GRAY)), c -> openRules(p));
        set(m, 14, item(Material.ZOMBIE_HEAD, t("menu.main.bots", NamedTextColor.AQUA),
                lines("menu.main.bots-lore", NamedTextColor.GRAY)), c -> openBotsSection(p));
        set(m, 16, item(Material.COMPARATOR, t("menu.main.config", NamedTextColor.YELLOW),
                lines("menu.main.config-lore", NamedTextColor.GRAY)), c -> openConfig(p));
        set(m, 22, item(Material.BARRIER, t("menu.close", NamedTextColor.GRAY), List.of()), c -> p.closeInventory());
        p.openInventory(m.inventory);
    }

    /** Resumo no topo do menu principal: bots, estado da caçada, alvo forçado e lista. */
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

    // ------------------------------------------------------------------ partida

    private void openMatch(Player p) {
        BotManager bm = manager();
        Menu m = create(3, "menu.title.match");
        int rings = bm.rings().count();
        set(m, 11, item(Material.LIME_DYE, t("menu.match.start", NamedTextColor.GREEN),
                List.of(rings == 0 ? t("menu.match.start-none", NamedTextColor.GRAY) : t("menu.match.start-rings", NamedTextColor.GRAY, Messages.p("rings", rings)),
                        plain("/manhunt start", NamedTextColor.DARK_GRAY))), c -> {
                    p.closeInventory();
                    if (bm.rings().releaseAll() == 0) {
                        msg().send(p, "countdown.nothing");
                    }
                });
        boolean paused = bm.pause().isPaused();
        set(m, 13, item(paused ? Material.MAGMA_CREAM : Material.BLUE_ICE,
                t(paused ? "menu.match.resume" : "menu.match.pause", paused ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                join(lines(paused ? "menu.match.resume-lore" : "menu.match.pause-lore", NamedTextColor.GRAY),
                        List.of(plain(paused ? "/manhunt resume" : "/manhunt pause", NamedTextColor.DARK_GRAY)))), c -> {
                    if (paused) {
                        if (bm.pause().resume()) {
                            Bukkit.broadcast(msg().prefixed("pause.resumed"));
                        }
                    } else if (bm.pause().pause()) {
                        Bukkit.broadcast(msg().prefixed("pause.paused"));
                    }
                    openMatch(p);
                });
        set(m, 15, item(Material.TARGET, t("menu.match.target", NamedTextColor.RED, Messages.p("target", forcedTargetName())),
                join(lines("menu.match.target-lore", NamedTextColor.GRAY), List.of(plain("/manhunt target", NamedTextColor.DARK_GRAY)))),
                c -> openTargetPicker(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    private void openTargetPicker(Player p) {
        Menu m = create(6, "menu.title.target");
        int i = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (i >= 45) {
                break;
            }
            set(m, i++, head(target.getName(), target, NamedTextColor.WHITE, lines("menu.target.pick", NamedTextColor.GRAY)), c -> {
                manager().setForcedTarget(target.getUniqueId());
                for (Bot b : manager().aliveBots()) {
                    b.setTargetPlayer(target.getUniqueId());
                }
                msg().send(p, "command.target-set", Messages.p("player", target.getName()));
                openMatch(p);
            });
        }
        set(m, 45, item(Material.MILK_BUCKET, t("menu.target.clear", NamedTextColor.GREEN),
                lines("menu.target.clear-lore", NamedTextColor.GRAY)), c -> {
                    manager().setForcedTarget(null);
                    msg().send(p, "command.target-cleared");
                    openMatch(p);
                });
        back(m, () -> openMatch(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ regras da caçada

    private void openRules(Player p) {
        List<Setting<?>> list = settings().inCategory(Category.MANHUNT);
        Menu m = create(4, "menu.title.rules");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openRules(p));
        }
        Settings.RingPoint pt = settings().ringPoint();
        set(m, 21, item(Material.LODESTONE, t("menu.rules.ring-point", NamedTextColor.GOLD), join(
                List.of(pt == null ? t("menu.rules.ring-point-none", NamedTextColor.WHITE)
                        : plain(String.format(Locale.ROOT, "%.0f %.0f %.0f (%s)", pt.x(), pt.y(), pt.z(), pt.world()), NamedTextColor.WHITE)),
                lines("menu.rules.ring-point-lore", NamedTextColor.GRAY),
                List.of(plain("/manhunt ring point", NamedTextColor.DARK_GRAY)))), c -> {
                    if (c.isRightClick()) {
                        settings().setRingPoint(null);
                        settings().ringCenter.set(RingCenter.AUTO);
                        msg().send(p, "ring.point-cleared");
                    } else {
                        Location l = p.getLocation();
                        settings().setRingPoint(new Settings.RingPoint(l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
                        settings().ringCenter.set(RingCenter.MANUAL);
                        msg().send(p, "ring.point-set", Messages.p("world", l.getWorld().getName()),
                                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", l.getX(), l.getY(), l.getZ())));
                    }
                    plugin.onSettingChanged(settings().ringCenter);
                    openRules(p);
                });
        List<String> names = settings().playerList();
        set(m, 23, item(Material.NAME_TAG, t("menu.rules.player-list", NamedTextColor.GOLD), List.of(
                t("menu.rules.player-list-mode", NamedTextColor.WHITE, Messages.p("mode", settings().playerListMode.display())),
                names.isEmpty() ? t("menu.rules.player-list-empty", NamedTextColor.GRAY)
                        : t("menu.rules.player-list-count", NamedTextColor.GRAY, Messages.p("amount", names.size())),
                t("menu.rules.click-to-edit", NamedTextColor.GRAY),
                plain("/manhunt players", NamedTextColor.DARK_GRAY))), c -> openPlayerList(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    /** Jogadores online e os da lista: clicar põe ou tira da lista. */
    private void openPlayerList(Player p) {
        Menu m = create(6, "menu.title.player-list");
        Map<String, Player> people = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : settings().playerList()) {
            people.put(n, null);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            people.put(online.getName(), online);
        }
        int i = 0;
        for (Map.Entry<String, Player> e : people.entrySet()) {
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
                            plugin.saveSettings();
                        }
                        openPlayerList(p);
                    });
        }
        PlayerListMode mode = settings().playerListMode.get();
        set(m, 47, item(Material.PAPER, t("menu.player-list.mode", NamedTextColor.YELLOW, Messages.p("mode", mode.configName())), List.of(
                t("player-list.explanation." + mode.configName(), NamedTextColor.GRAY),
                t("menu.player-list.click-to-change", NamedTextColor.DARK_GRAY))), c -> {
                    settings().playerListMode.setFromText(settings().playerListMode.next().configName());
                    plugin.onSettingChanged(settings().playerListMode);
                    openPlayerList(p);
                });
        set(m, 51, item(Material.WRITABLE_BOOK, t("menu.player-list.add-by-name", NamedTextColor.WHITE),
                lines("menu.player-list.add-by-name-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-player", text -> {
                    if (settings().addListedPlayer(text)) {
                        plugin.saveSettings();
                    }
                    openPlayerList(p);
                }));
        back(m, () -> openRules(p));
        p.openInventory(m.inventory);
    }

    /** Cabeça de jogador; com o jogador online, já com a skin dele. */
    private static ItemStack head(String name, Player online, NamedTextColor color, List<Component> lore) {
        ItemStack head = item(Material.PLAYER_HEAD, plain(name, color), lore);
        if (online != null) {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            head.setItemMeta(meta);
        }
        return head;
    }

    // ------------------------------------------------------------------ bots

    private void openBotsSection(Player p) {
        BotManager bm = manager();
        Menu m = create(4, "menu.title.bots-section");
        set(m, 11, item(Material.SPAWNER, t("menu.bots-section.create", NamedTextColor.GREEN),
                lines("menu.bots-section.create-lore", NamedTextColor.GRAY)), c -> openSpawner(p));
        set(m, 13, item(Material.PLAYER_HEAD, t("menu.bots-section.manage", NamedTextColor.AQUA),
                lines("menu.bots-section.manage-lore", NamedTextColor.GRAY, Messages.p("alive", bm.aliveCount()), Messages.p("total", bm.allBots().size()))),
                c -> openBots(p, 0));
        set(m, 15, item(Material.WHITE_BANNER, t("menu.bots-section.groups", NamedTextColor.GOLD),
                lines("menu.bots-section.groups-lore", NamedTextColor.GRAY)), c -> openGroups(p));
        set(m, 20, item(Material.ENDER_PEARL, t("menu.bots-section.bring", NamedTextColor.LIGHT_PURPLE),
                List.of(plain("/manhunt tp all", NamedTextColor.DARK_GRAY))), c -> {
                    int n = bm.teleportAllTo(p);
                    msg().send(p, "command.tp-all", Messages.p("amount", n), Messages.p("player", p.getName()));
                });
        set(m, 24, item(Material.LAVA_BUCKET, t("menu.bots-section.delete", NamedTextColor.RED),
                join(lines("menu.bots-section.delete-lore", NamedTextColor.GRAY), List.of(plain("/manhunt remove all", NamedTextColor.DARK_GRAY)))),
                c -> openConfirmDelete(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    private void openConfirmDelete(Player p) {
        Menu m = create(3, "menu.title.confirm");
        set(m, 11, item(Material.RED_CONCRETE, t("menu.confirm.yes", NamedTextColor.RED), List.of()), c -> {
            int n = manager().deleteAll();
            msg().send(p, "command.all-removed", Messages.p("amount", n));
            p.closeInventory();
        });
        set(m, 15, item(Material.GREEN_CONCRETE, t("menu.confirm.no", NamedTextColor.GREEN), List.of()), c -> openBotsSection(p));
        p.openInventory(m.inventory);
    }

    private void openSpawner(Player p) {
        SpawnDraft d = drafts.computeIfAbsent(p.getUniqueId(), k -> new SpawnDraft());
        if (d.difficulty == null) {
            d.difficulty = settings().defaultDifficulty.get();
        }
        Menu m = create(3, "menu.title.create");
        set(m, 10, item(Material.NAME_TAG, t("menu.create.name", NamedTextColor.WHITE, Messages.p("name", d.name)),
                lines("menu.create.name-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-name", text -> {
                    d.name = text;
                    openSpawner(p);
                }));
        set(m, 11, item(Material.PLAYER_HEAD, t("menu.create.skin", NamedTextColor.WHITE,
                        Messages.p("skin", d.skin.isEmpty() ? msg().raw("menu.create.skin-same") : d.skin)),
                lines("menu.create.skin-lore", NamedTextColor.GRAY)), c -> askChat(p, "menu.type-skin", text -> {
                    d.skin = text.equals("-") ? "" : text;
                    openSpawner(p);
                }));
        set(m, 12, item(Material.SLIME_BALL, t("menu.create.amount", NamedTextColor.WHITE, Messages.p("amount", d.amount)),
                lines("menu.create.amount-lore", NamedTextColor.GRAY)), c -> {
                    int step = c.isShiftClick() ? 5 : 1;
                    d.amount = Math.max(1, Math.min(100, d.amount + (c.isLeftClick() ? step : -step)));
                    openSpawner(p);
                });
        set(m, 13, item(Material.NETHERITE_SWORD, t("menu.create.difficulty", NamedTextColor.WHITE, Messages.p("difficulty", msg().difficulty(d.difficulty))),
                lines("menu.create.difficulty-lore", NamedTextColor.GRAY)), c -> {
                    BotDifficulty[] all = BotDifficulty.values();
                    d.difficulty = all[(d.difficulty.ordinal() + 1) % all.length];
                    openSpawner(p);
                });
        set(m, 14, item(Material.EXPERIENCE_BOTTLE, t("menu.create.phase", NamedTextColor.WHITE,
                        Messages.p("phase", d.phase == 0 ? msg().raw("menu.create.phase-default") : String.valueOf(d.phase))),
                lines("menu.create.phase-lore", NamedTextColor.GRAY)), c -> {
                    d.phase = Math.max(0, Math.min(10, d.phase + (c.isLeftClick() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 15, item(Material.BLAZE_POWDER, t("menu.create.aggression", NamedTextColor.WHITE, Messages.p("aggression", d.aggression)),
                lines("menu.create.aggression-lore", NamedTextColor.GRAY)), c -> {
                    d.aggression = Math.max(0, Math.min(10, d.aggression + (c.isLeftClick() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 16, item(Material.LIME_CONCRETE, t("menu.create.spawn", NamedTextColor.GREEN),
                lines("menu.create.spawn-lore", NamedTextColor.GRAY, Messages.p("mode", settings().spawnMode.display()))), c -> {
                    p.closeInventory();
                    if (manager().manualRingPointMissing()) {
                        msg().send(p, "ring.no-point");
                    }
                    msg().send(p, "command.creating", Messages.p("amount", d.amount), Messages.p("difficulty", msg().difficulty(d.difficulty)),
                            Messages.p("name", d.name));
                    manager().createBots(manager().ringCenterFor(p.getLocation()), d.name, d.skin.isEmpty() ? null : d.skin, d.amount, d.difficulty, d.phase, d.aggression)
                            .whenComplete((bots, err) -> {
                                if (err != null) {
                                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create bots", err);
                                    msg().send(p, "command.create-error");
                                } else {
                                    msg().send(p, "command.created", Messages.p("amount", bots.size()), Messages.p("name", d.name));
                                }
                            });
                });
        back(m, () -> openBotsSection(p));
        p.openInventory(m.inventory);
    }

    private void openBots(Player p, int page) {
        List<Bot> bots = new ArrayList<>(manager().allBots());
        Menu m = create(6, "menu.title.bots");
        int per = 45;
        int start = page * per;
        for (int i = 0; i < per && start + i < bots.size(); i++) {
            Bot b = bots.get(start + i);
            Player target = b.target();
            ItemStack head = item(Material.PLAYER_HEAD, plain(b.visualName() + " (" + b.name() + ")", b.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(
                    t("menu.bots.phase", NamedTextColor.GRAY, Messages.p("phase", b.progression().phase()), Messages.p("difficulty", msg().difficulty(b.difficulty()))),
                    t("menu.bots.health", NamedTextColor.GRAY, Messages.p("health", String.format(Locale.ROOT, "%.0f", b.health())), Messages.p("kills", b.kills())),
                    t("menu.bots.target", NamedTextColor.GRAY, Messages.p("target", target == null ? "-" : target.getName())),
                    t("menu.bots.help", NamedTextColor.DARK_GRAY)));
            set(m, i, head, c -> {
                if (c == ClickType.SHIFT_RIGHT) {
                    manager().removeBot(b);
                    openBots(p, page);
                } else if (c.isRightClick()) {
                    if (b.isAlive()) {
                        manager().teleportBot(b, p.getLocation());
                    }
                } else if (b.isAlive()) {
                    p.teleport(b.location());
                    p.closeInventory();
                }
            });
        }
        if (page > 0) {
            set(m, 45, item(Material.ARROW, t("menu.bots.previous", NamedTextColor.GRAY), List.of()), c -> openBots(p, page - 1));
        }
        back(m, () -> openBotsSection(p));
        if (start + per < bots.size()) {
            set(m, 53, item(Material.ARROW, t("menu.bots.next", NamedTextColor.GRAY), List.of()), c -> openBots(p, page + 1));
        }
        p.openInventory(m.inventory);
    }

    private void openGroups(Player p) {
        Menu m = create(6, "menu.title.groups");
        int i = 0;
        for (BotGroup g : manager().groups()) {
            if (i >= 45) {
                break;
            }
            boolean escape = g.role() == BotRole.ESCAPE;
            set(m, i++, item(escape ? Material.RABBIT_FOOT : Material.IRON_SWORD, plain(g.name(), NamedTextColor.GOLD), join(List.of(
                    t("menu.groups.role", NamedTextColor.GRAY, Messages.p("role", msg().raw(escape ? "role.escape" : "role.hunter"))),
                    t("menu.groups.chase", NamedTextColor.GRAY, Messages.p("value", msg().yesNo(g.trackPlayers()))),
                    t("menu.groups.enemies", NamedTextColor.GRAY, Messages.p("enemies", g.enemies().isEmpty() ? "-" : String.join(", ", g.enemies())))),
                    lines("menu.groups.help", NamedTextColor.DARK_GRAY))), c -> {
                        if (c == ClickType.SHIFT_RIGHT) {
                            manager().deleteGroup(g.name());
                        } else if (c.isRightClick()) {
                            g.setTrackPlayers(!g.trackPlayers());
                        } else {
                            g.setRole(escape ? BotRole.HUNTER : BotRole.ESCAPE);
                            plugin.glow().applyAll(manager().aliveBots());
                        }
                        manager().markDirty();
                        openGroups(p);
                    });
        }
        back(m, () -> openBotsSection(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ configurações

    private void openConfig(Player p) {
        Menu m = create(3, "menu.title.config");
        Material[] icons = {Material.BOOK, Material.ARMOR_STAND, Material.IRON_SWORD, Material.FEATHER, Material.MAP, Material.TNT, Material.GLOWSTONE_DUST};
        int slot = 10;
        for (int i = 0; i < CONFIG_CATEGORIES.size(); i++) {
            Category cat = CONFIG_CATEGORIES.get(i);
            set(m, slot++, item(icons[i], t("category." + cat.name().toLowerCase(Locale.ROOT), NamedTextColor.YELLOW),
                    lines("menu.config.category-lore", NamedTextColor.GRAY, Messages.p("amount", settings().inCategory(cat).size()))), c -> openSettings(p, cat));
        }
        setGlowButton(p, m, 4);
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    /** Atalho para o brilho dos caçadores: esquerdo liga/desliga, direito troca a cor. */
    private void setGlowButton(Player p, Menu m, int slot) {
        Setting.Bool glow = settings().glowHunters;
        Setting.Enumerated<GlowColor> color = settings().glowHuntersColor;
        boolean on = glow.on();
        set(m, slot, item(on ? Material.GLOW_INK_SAC : Material.INK_SAC,
                t("menu.config.glow", on ? NamedTextColor.GREEN : NamedTextColor.GRAY, Messages.p("name", msg().optionName(glow)), Messages.p("value", msg().onOff(on))),
                join(List.of(t("menu.config.glow-color", NamedTextColor.GRAY, Messages.c("color", plain(color.display(), color.get().color())))),
                        lines("menu.config.glow-help", NamedTextColor.DARK_GRAY))), c -> {
                    Setting<?> changed;
                    if (c.isRightClick()) {
                        color.setFromText(color.next().configName());
                        changed = color;
                    } else {
                        glow.set(!on);
                        changed = glow;
                    }
                    plugin.onSettingChanged(changed);
                    openConfig(p);
                });
    }

    private void openSettings(Player p, Category cat) {
        List<Setting<?>> list = settings().inCategory(cat);
        Menu m = create(Math.max(2, (list.size() + 8) / 9 + 1), "menu.title.options");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openSettings(p, cat));
        }
        back(m, () -> openConfig(p));
        p.openInventory(m.inventory);
    }

    /** Uma opção como item: nome, descrição do arquivo de idioma, valor e comando; o clique muda o valor. */
    private void setSetting(Menu m, int slot, Setting<?> s, Runnable reopen) {
        Material icon;
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(msg().raw("options." + s.key() + ".description"), 40)) {
            lore.add(plain(line, NamedTextColor.GRAY));
        }
        String value = s instanceof Setting.Bool b ? msg().onOff(b.on()) : s.display();
        lore.add(t("menu.option.value", NamedTextColor.WHITE, Messages.p("value", value)));
        lore.add(plain("/manhunt settings " + s.key(), NamedTextColor.DARK_GRAY));
        if (s instanceof Setting.Bool b) {
            icon = b.on() ? Material.LIME_DYE : Material.GRAY_DYE;
            lore.add(t("menu.option.toggle", NamedTextColor.GRAY));
        } else if (s instanceof Setting.Enumerated<?> || s instanceof Setting.Text) {
            icon = Material.PAPER;
            lore.add(t("menu.option.next", NamedTextColor.GRAY));
        } else {
            icon = Material.REPEATER;
            lore.add(t("menu.option.adjust", NamedTextColor.GRAY));
        }
        set(m, slot, item(icon, plain(msg().optionName(s), NamedTextColor.YELLOW), lore), c -> {
            adjust(s, c);
            plugin.onSettingChanged(s);
            reopen.run();
        });
    }

    /**
     * Quebra a descrição em linhas de até {@code width} letras. Palavra maior que a linha (texto sem
     * espaços, como em chinês) é cortada no meio.
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

    private static void adjust(Setting<?> s, ClickType c) {
        int sign = c.isRightClick() ? -1 : 1;
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
            int step = Math.max(1, range >= 1000 ? (c.isShiftClick() ? 500 : 50) : range >= 100 ? (c.isShiftClick() ? 50 : 5) : (c.isShiftClick() ? 10 : 1));
            in.set(in.value() + sign * step);
        } else if (s instanceof Setting.Dbl d) {
            double step = d.step() * (c.isShiftClick() ? 10 : 1);
            d.set(Math.round((d.value() + sign * step) * 1000.0) / 1000.0);
        }
    }

    // ------------------------------------------------------------------ eventos

    private void askChat(Player p, String key, Consumer<String> then) {
        p.closeInventory();
        chatInput.put(p.getUniqueId(), then);
        msg().send(p, key);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> handler = chatInput.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!text.isEmpty() && !text.equalsIgnoreCase("cancel") && !text.equalsIgnoreCase(msg().raw("menu.cancel-word"))) {
                handler.accept(text);
            } else {
                msg().send(event.getPlayer(), "menu.cancelled");
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Consumer<ClickType> action = menu.actions.get(event.getRawSlot());
        if (action != null) {
            click(p);
            action.accept(event.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatInput.remove(event.getPlayer().getUniqueId());
        drafts.remove(event.getPlayer().getUniqueId());
    }
}
