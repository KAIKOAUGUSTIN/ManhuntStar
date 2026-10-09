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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
 * categorias de opções). Textos (nome do bot, skin, jogador) são digitados no chat.
 */
public final class MenuManager implements Listener {

    private static final List<Category> CONFIG_CATEGORIES = List.of(Category.GERAL, Category.BOTS, Category.COMBATE,
            Category.MOBILIDADE, Category.PATHFINDING, Category.TATICAS, Category.APARENCIA);

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, SpawnDraft> drafts = new HashMap<>();
    /** Lido pelo evento de chat, que roda fora da thread principal. */
    private final Map<UUID, Consumer<String>> chatInput = new ConcurrentHashMap<>();
    private Map<String, List<String>> descriptions;

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

    private static Component t(String text, NamedTextColor color) {
        return Component.text(text, color);
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
        set(m, m.inventory.getSize() - 5, item(Material.OAK_DOOR, t("Voltar", NamedTextColor.GRAY), List.of()), c -> to.run());
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    // ------------------------------------------------------------------ menu principal

    public void openMain(Player p) {
        Menu m = create(3, "menu.titulo-principal");
        set(m, 4, item(Material.COMPASS, t("Manhunt*", NamedTextColor.DARK_RED), status()), null);
        set(m, 10, item(Material.CLOCK, t("Partida", NamedTextColor.GREEN),
                List.of(t("Começar, pausar e alvo forçado", NamedTextColor.GRAY))), c -> openMatch(p));
        set(m, 12, item(Material.WRITABLE_BOOK, t("Regras da caçada", NamedTextColor.GOLD),
                List.of(t("Roda, contagem, quem entra no meio", NamedTextColor.GRAY), t("e lista de jogadores", NamedTextColor.GRAY))), c -> openRules(p));
        set(m, 14, item(Material.ZOMBIE_HEAD, t("Bots", NamedTextColor.AQUA),
                List.of(t("Criar, gerenciar, grupos, trazer e apagar", NamedTextColor.GRAY))), c -> openBotsSection(p));
        set(m, 16, item(Material.COMPARATOR, t("Configurações", NamedTextColor.YELLOW),
                List.of(t("Combate, mobilidade, caminhos, aparência...", NamedTextColor.GRAY))), c -> openConfig(p));
        set(m, 22, item(Material.BARRIER, t("Fechar", NamedTextColor.GRAY), List.of()), c -> p.closeInventory());
        p.openInventory(m.inventory);
    }

    /** Resumo no topo do menu principal: bots, estado da caçada, alvo forçado e lista. */
    private List<Component> status() {
        BotManager bm = manager();
        List<Component> lore = new ArrayList<>();
        lore.add(t("Bots: " + bm.aliveCount() + " vivos de " + bm.allBots().size(), NamedTextColor.GRAY));
        lore.add(t("Caçada: ", NamedTextColor.GRAY).append(huntState()));
        UUID forced = bm.forcedTarget();
        Player fp = forced == null ? null : Bukkit.getPlayer(forced);
        lore.add(t("Alvo forçado: " + (fp == null ? "nenhum" : fp.getName()), NamedTextColor.GRAY));
        lore.add(t("Lista de jogadores: " + settings().playerListMode.display(), NamedTextColor.GRAY));
        return lore;
    }

    private Component huntState() {
        BotManager bm = manager();
        if (bm.pause().isPaused()) {
            return t("pausada", NamedTextColor.YELLOW);
        }
        if (bm.rings().isActive()) {
            return t(bm.rings().count() + " roda(s) esperando", NamedTextColor.GOLD);
        }
        if (bm.policy().isActive()) {
            return t("em andamento", NamedTextColor.RED);
        }
        return t(bm.allBots().isEmpty() ? "sem bots" : "parada", NamedTextColor.WHITE);
    }

    // ------------------------------------------------------------------ partida

    private void openMatch(Player p) {
        BotManager bm = manager();
        Menu m = create(3, "menu.titulo-partida");
        int rings = bm.rings().count();
        set(m, 11, item(Material.LIME_DYE, t("Começar a caçada agora", NamedTextColor.GREEN),
                List.of(t(rings == 0 ? "Nenhuma roda esperando" : rings + " roda(s) esperando: solta todas", NamedTextColor.GRAY),
                        t("/manhunt start", NamedTextColor.DARK_GRAY))), c -> {
                    p.closeInventory();
                    if (bm.rings().releaseAll() == 0) {
                        msg().send(p, "contagem.nada");
                    }
                });
        boolean paused = bm.pause().isPaused();
        set(m, 13, item(paused ? Material.MAGMA_CREAM : Material.BLUE_ICE,
                t(paused ? "Continuar a caçada" : "Pausar a caçada", paused ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                List.of(t(paused ? "Os bots voltam a se mover" : "Congela os bots onde estão, invencíveis,", NamedTextColor.GRAY),
                        t(paused ? "e os relógios voltam a contar" : "e para contagem, proteção e fases", NamedTextColor.GRAY),
                        t(paused ? "/manhunt resume" : "/manhunt pause", NamedTextColor.DARK_GRAY))), c -> {
                    if (paused) {
                        if (bm.pause().resume()) {
                            Bukkit.broadcast(msg().prefixed("pausa.continuada"));
                        }
                    } else if (bm.pause().pause()) {
                        Bukkit.broadcast(msg().prefixed("pausa.pausada"));
                    }
                    openMatch(p);
                });
        UUID forced = bm.forcedTarget();
        Player fp = forced == null ? null : Bukkit.getPlayer(forced);
        set(m, 15, item(Material.TARGET, t("Alvo forçado: " + (fp == null ? "nenhum" : fp.getName()), NamedTextColor.RED),
                List.of(t("Todos os bots atrás de um jogador", NamedTextColor.GRAY), t("/manhunt target", NamedTextColor.DARK_GRAY))),
                c -> openTargetPicker(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    private void openTargetPicker(Player p) {
        Menu m = create(6, "menu.titulo-alvo");
        int i = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (i >= 45) {
                break;
            }
            set(m, i++, head(target.getName(), target, NamedTextColor.WHITE, List.of(t("Clique: todos os bots atrás dele", NamedTextColor.GRAY))), c -> {
                manager().setForcedTarget(target.getUniqueId());
                for (Bot b : manager().aliveBots()) {
                    b.setTargetPlayer(target.getUniqueId());
                }
                msg().send(p, "comando.alvo-definido", Messages.p("jogador", target.getName()));
                openMatch(p);
            });
        }
        set(m, 45, item(Material.MILK_BUCKET, t("Sem alvo forçado", NamedTextColor.GREEN),
                List.of(t("Os bots voltam a escolher sozinhos", NamedTextColor.GRAY))), c -> {
                    manager().setForcedTarget(null);
                    msg().send(p, "comando.alvo-limpo");
                    openMatch(p);
                });
        back(m, () -> openMatch(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ regras da caçada

    private void openRules(Player p) {
        List<Setting<?>> list = settings().inCategory(Category.MANHUNT);
        Menu m = create(4, "menu.titulo-regras");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openRules(p));
        }
        Settings.RingPoint pt = settings().ringPoint();
        set(m, 21, item(Material.LODESTONE, t("Ponto da roda", NamedTextColor.GOLD), List.of(
                t(pt == null ? "Nenhum ponto marcado" : String.format(Locale.ROOT, "%.0f %.0f %.0f (%s)", pt.x(), pt.y(), pt.z(), pt.world()), NamedTextColor.WHITE),
                t("Esquerdo: marcar onde você está (centro manual)", NamedTextColor.GRAY),
                t("Direito: apagar (centro automático)", NamedTextColor.GRAY),
                t("/manhunt ring point", NamedTextColor.DARK_GRAY))), c -> {
                    if (c.isRightClick()) {
                        settings().setRingPoint(null);
                        settings().ringCenter.set(RingCenter.AUTOMATICO);
                        msg().send(p, "roda.ponto-limpo");
                    } else {
                        Location l = p.getLocation();
                        settings().setRingPoint(new Settings.RingPoint(l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
                        settings().ringCenter.set(RingCenter.MANUAL);
                        msg().send(p, "roda.ponto-marcado", Messages.p("mundo", l.getWorld().getName()),
                                Messages.p("pos", String.format(Locale.ROOT, "%.1f %.1f %.1f", l.getX(), l.getY(), l.getZ())));
                    }
                    plugin.onSettingChanged(settings().ringCenter);
                    openRules(p);
                });
        List<String> names = settings().playerList();
        set(m, 23, item(Material.NAME_TAG, t("Lista de jogadores", NamedTextColor.GOLD), List.of(
                t("Modo: " + settings().playerListMode.display(), NamedTextColor.WHITE),
                t(names.isEmpty() ? "Ninguém na lista" : names.size() + " jogador(es) na lista", NamedTextColor.GRAY),
                t("Clique para editar", NamedTextColor.GRAY),
                t("/manhunt players", NamedTextColor.DARK_GRAY))), c -> openPlayerList(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    /** Jogadores online e os da lista: clicar põe ou tira da lista. */
    private void openPlayerList(Player p) {
        Menu m = create(6, "menu.titulo-lista");
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
                    t(listed ? "Na lista" : "Fora da lista", listed ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                    t(listed ? "Clique para tirar" : "Clique para pôr", NamedTextColor.DARK_GRAY))), c -> {
                        boolean changed = listed ? settings().removeListedPlayer(name) : settings().addListedPlayer(name);
                        if (changed) {
                            plugin.saveSettings();
                        }
                        openPlayerList(p);
                    });
        }
        PlayerListMode mode = settings().playerListMode.get();
        set(m, 47, item(Material.PAPER, t("Modo: " + mode.configName(), NamedTextColor.YELLOW), List.of(
                t(msg().raw("lista.explicacao." + mode.configName()), NamedTextColor.GRAY),
                t("Clique para trocar", NamedTextColor.DARK_GRAY))), c -> {
                    settings().playerListMode.setFromText(settings().playerListMode.next().configName());
                    plugin.onSettingChanged(settings().playerListMode);
                    openPlayerList(p);
                });
        set(m, 51, item(Material.WRITABLE_BOOK, t("Pôr um jogador pelo nome", NamedTextColor.WHITE),
                List.of(t("Para quem não está online", NamedTextColor.GRAY))), c -> askChat(p, "menu.digite-jogador", text -> {
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
        ItemStack head = item(Material.PLAYER_HEAD, t(name, color), lore);
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
        Menu m = create(4, "menu.titulo-secao-bots");
        set(m, 11, item(Material.SPAWNER, t("Criar bots", NamedTextColor.GREEN),
                List.of(t("Nome, quantidade, dificuldade e fase", NamedTextColor.GRAY))), c -> openSpawner(p));
        set(m, 13, item(Material.PLAYER_HEAD, t("Gerenciar bots", NamedTextColor.AQUA),
                List.of(t(bm.aliveCount() + " vivos de " + bm.allBots().size(), NamedTextColor.GRAY))), c -> openBots(p, 0));
        set(m, 15, item(Material.WHITE_BANNER, t("Grupos", NamedTextColor.GOLD),
                List.of(t("Papel, perseguir jogadores e apagar", NamedTextColor.GRAY))), c -> openGroups(p));
        set(m, 20, item(Material.ENDER_PEARL, t("Trazer todos os bots até mim", NamedTextColor.LIGHT_PURPLE),
                List.of(t("/manhunt tp all", NamedTextColor.DARK_GRAY))), c -> {
                    int n = bm.teleportAllTo(p);
                    msg().send(p, "comando.tp-todos", Messages.p("quantidade", n), Messages.p("jogador", p.getName()));
                });
        set(m, 24, item(Material.LAVA_BUCKET, t("Apagar todos os bots", NamedTextColor.RED),
                List.of(t("Pede confirmação", NamedTextColor.GRAY), t("/manhunt remove all", NamedTextColor.DARK_GRAY))), c -> openConfirmDelete(p));
        back(m, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    private void openConfirmDelete(Player p) {
        Menu m = create(3, "menu.titulo-confirmar");
        set(m, 11, item(Material.RED_CONCRETE, t("Sim, apagar todos", NamedTextColor.RED), List.of()), c -> {
            int n = manager().deleteAll();
            msg().send(p, "comando.todos-removidos", Messages.p("quantidade", n));
            p.closeInventory();
        });
        set(m, 15, item(Material.GREEN_CONCRETE, t("Cancelar", NamedTextColor.GREEN), List.of()), c -> openBotsSection(p));
        p.openInventory(m.inventory);
    }

    private void openSpawner(Player p) {
        SpawnDraft d = drafts.computeIfAbsent(p.getUniqueId(), k -> new SpawnDraft());
        if (d.difficulty == null) {
            d.difficulty = settings().defaultDifficulty.get();
        }
        Menu m = create(3, "menu.titulo-criar");
        set(m, 10, item(Material.NAME_TAG, t("Nome: " + d.name, NamedTextColor.WHITE), List.of(t("Clique e digite no chat (use % para o número)", NamedTextColor.GRAY))),
                c -> askChat(p, "menu.digite-nome", text -> {
                    d.name = text;
                    openSpawner(p);
                }));
        set(m, 11, item(Material.PLAYER_HEAD, t("Skin: " + (d.skin.isEmpty() ? "(mesmo nome)" : d.skin), NamedTextColor.WHITE),
                List.of(t("Clique e digite o nome de um jogador", NamedTextColor.GRAY))), c -> askChat(p, "menu.digite-skin", text -> {
                    d.skin = text.equals("-") ? "" : text;
                    openSpawner(p);
                }));
        set(m, 12, item(Material.SLIME_BALL, t("Quantidade: " + d.amount, NamedTextColor.WHITE), List.of(t("Esquerdo +1, direito -1, shift ±5", NamedTextColor.GRAY))), c -> {
            int step = c.isShiftClick() ? 5 : 1;
            d.amount = Math.max(1, Math.min(100, d.amount + (c.isLeftClick() ? step : -step)));
            openSpawner(p);
        });
        set(m, 13, item(Material.NETHERITE_SWORD, t("Dificuldade: " + d.difficulty.label(), NamedTextColor.WHITE), List.of(t("Clique para trocar", NamedTextColor.GRAY))), c -> {
            BotDifficulty[] all = BotDifficulty.values();
            d.difficulty = all[(d.difficulty.ordinal() + 1) % all.length];
            openSpawner(p);
        });
        set(m, 14, item(Material.EXPERIENCE_BOTTLE, t("Fase inicial: " + (d.phase == 0 ? "padrão" : d.phase), NamedTextColor.WHITE),
                List.of(t("Esquerdo +1, direito -1", NamedTextColor.GRAY))), c -> {
                    d.phase = Math.max(0, Math.min(10, d.phase + (c.isLeftClick() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 15, item(Material.BLAZE_POWDER, t("Agressividade: " + d.aggression, NamedTextColor.WHITE),
                List.of(t("Usada pelas capacidades especiais", NamedTextColor.GRAY))), c -> {
                    d.aggression = Math.max(0, Math.min(10, d.aggression + (c.isLeftClick() ? 1 : -1)));
                    openSpawner(p);
                });
        set(m, 16, item(Material.LIME_CONCRETE, t("Criar aqui", NamedTextColor.GREEN), List.of(t("Modo de spawn: " + settings().spawnMode.display(), NamedTextColor.GRAY))), c -> {
            p.closeInventory();
            if (manager().manualRingPointMissing()) {
                msg().send(p, "roda.sem-ponto");
            }
            msg().send(p, "comando.criando", Messages.p("quantidade", d.amount), Messages.p("dificuldade", d.difficulty.label()), Messages.p("nome", d.name));
            manager().createBots(manager().ringCenterFor(p.getLocation()), d.name, d.skin.isEmpty() ? null : d.skin, d.amount, d.difficulty, d.phase, d.aggression)
                    .whenComplete((bots, err) -> {
                        if (err != null) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao criar bots", err);
                            msg().send(p, "comando.erro-criar");
                        } else {
                            msg().send(p, "comando.criados", Messages.p("quantidade", bots.size()), Messages.p("nome", d.name));
                        }
                    });
        });
        back(m, () -> openBotsSection(p));
        p.openInventory(m.inventory);
    }

    private void openBots(Player p, int page) {
        List<Bot> bots = new ArrayList<>(manager().allBots());
        Menu m = create(6, "menu.titulo-bots");
        int per = 45;
        int start = page * per;
        for (int i = 0; i < per && start + i < bots.size(); i++) {
            Bot b = bots.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.displayName(t(b.visualName() + " (" + b.name() + ")", b.isAlive() ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            Player target = b.target();
            meta.lore(List.of(
                    t("Fase " + b.progression().phase() + " • " + b.difficulty().label(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    t("Vida " + String.format(Locale.ROOT, "%.0f", b.health()) + " • Abates " + b.kills(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    t("Alvo: " + (target == null ? "-" : target.getName()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    t("Esquerdo: ir até ele • Direito: trazer • Shift+direito: remover", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
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
            set(m, 45, item(Material.ARROW, t("Página anterior", NamedTextColor.GRAY), List.of()), c -> openBots(p, page - 1));
        }
        back(m, () -> openBotsSection(p));
        if (start + per < bots.size()) {
            set(m, 53, item(Material.ARROW, t("Próxima página", NamedTextColor.GRAY), List.of()), c -> openBots(p, page + 1));
        }
        p.openInventory(m.inventory);
    }

    private void openGroups(Player p) {
        Menu m = create(6, "menu.titulo-grupos");
        int i = 0;
        for (BotGroup g : manager().groups()) {
            if (i >= 45) {
                break;
            }
            boolean escape = g.role() == BotRole.ESCAPE;
            set(m, i++, item(escape ? Material.RABBIT_FOOT : Material.IRON_SWORD, t(g.name(), NamedTextColor.GOLD), List.of(
                    t("Papel: " + (escape ? "fugitivo" : "caçador"), NamedTextColor.GRAY),
                    t("Persegue jogadores: " + (g.trackPlayers() ? "sim" : "não"), NamedTextColor.GRAY),
                    t("Inimigos: " + (g.enemies().isEmpty() ? "-" : String.join(", ", g.enemies())), NamedTextColor.GRAY),
                    t("Esquerdo: trocar papel • Direito: perseguir jogadores", NamedTextColor.DARK_GRAY),
                    t("Shift+direito: remover o grupo e os bots", NamedTextColor.DARK_GRAY))), c -> {
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
        Menu m = create(3, "menu.titulo-configuracoes");
        Material[] icons = {Material.BOOK, Material.ARMOR_STAND, Material.IRON_SWORD, Material.FEATHER, Material.MAP, Material.TNT, Material.GLOWSTONE_DUST};
        int slot = 10;
        for (int i = 0; i < CONFIG_CATEGORIES.size(); i++) {
            Category cat = CONFIG_CATEGORIES.get(i);
            set(m, slot++, item(icons[i], t(categoryName(cat), NamedTextColor.YELLOW),
                    List.of(t(settings().inCategory(cat).size() + " opções • clique para editar", NamedTextColor.GRAY))), c -> openSettings(p, cat));
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
                t(glow.label() + ": " + (on ? "ligado" : "desligado"), on ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(t("Cor: ", NamedTextColor.GRAY).append(t(color.display(), color.get().color())),
                        t("Esquerdo: ligar/desligar • Direito: trocar a cor", NamedTextColor.DARK_GRAY))), c -> {
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

    private static String categoryName(Category c) {
        return switch (c) {
            case GERAL -> "Geral";
            case BOTS -> "Bots";
            case COMBATE -> "Combate";
            case MOBILIDADE -> "Mobilidade";
            case PATHFINDING -> "Pathfinding";
            case TATICAS -> "Táticas";
            case MANHUNT -> "Regras da caçada";
            case APARENCIA -> "Aparência";
        };
    }

    private void openSettings(Player p, Category cat) {
        List<Setting<?>> list = settings().inCategory(cat);
        Menu m = create(Math.max(2, (list.size() + 8) / 9 + 1), "menu.titulo-opcoes");
        int i = 0;
        for (Setting<?> s : list) {
            setSetting(m, i++, s, () -> openSettings(p, cat));
        }
        back(m, () -> openConfig(p));
        p.openInventory(m.inventory);
    }

    /** Uma opção como item: nome, descrição do config.yml, valor e comando; o clique muda o valor. */
    private void setSetting(Menu m, int slot, Setting<?> s, Runnable reopen) {
        Material icon;
        List<Component> lore = new ArrayList<>();
        for (String line : description(s)) {
            lore.add(t(line, NamedTextColor.GRAY));
        }
        String value = s instanceof Setting.Bool b ? (b.on() ? "ligado" : "desligado") : s.display();
        lore.add(t("Valor: " + value, NamedTextColor.WHITE));
        lore.add(t("/manhunt settings " + s.key(), NamedTextColor.DARK_GRAY));
        if (s instanceof Setting.Bool b) {
            icon = b.on() ? Material.LIME_DYE : Material.GRAY_DYE;
            lore.add(t("Clique para ligar/desligar", NamedTextColor.GRAY));
        } else if (s instanceof Setting.Enumerated<?>) {
            icon = Material.PAPER;
            lore.add(t("Clique para a próxima opção", NamedTextColor.GRAY));
        } else {
            icon = Material.REPEATER;
            lore.add(t("Esquerdo +, direito -, shift = passo maior", NamedTextColor.GRAY));
        }
        set(m, slot, item(icon, t(s.label(), NamedTextColor.YELLOW), lore), c -> {
            adjust(s, c);
            plugin.onSettingChanged(s);
            reopen.run();
        });
    }

    /** Descrição da opção, tirada dos comentários do config.yml que vem dentro do plugin. */
    private List<String> description(Setting<?> s) {
        if (descriptions == null) {
            descriptions = new HashMap<>();
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    settings().descriptions(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .forEach((path, text) -> descriptions.put(path, wrap(text, 40)));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Não foi possível ler as descrições das opções: " + e.getMessage());
            }
        }
        return descriptions.getOrDefault(s.path(), List.of());
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
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
            if (!text.equalsIgnoreCase("cancelar") && !text.isEmpty()) {
                handler.accept(text);
            } else {
                msg().send(event.getPlayer(), "menu.cancelado");
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
