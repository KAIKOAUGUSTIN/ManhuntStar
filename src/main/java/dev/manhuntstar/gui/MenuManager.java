package dev.manhuntstar.gui;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Setting.Category;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
 * {@code /bot menu}: menus em baú no lugar da tela do cliente do mod (criar bots, gerenciar,
 * opções por categoria, grupos e ações rápidas). Textos (nome do bot, skin) são digitados no chat.
 */
public final class MenuManager implements Listener {

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, SpawnDraft> drafts = new HashMap<>();
    private final Map<UUID, Consumer<String>> chatInput = new HashMap<>();

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

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    // ------------------------------------------------------------------ menu principal

    public void openMain(Player p) {
        Menu m = create(4, "menu.titulo-principal");
        set(m, 11, item(Material.ZOMBIE_HEAD, t("Criar bots", NamedTextColor.GREEN), List.of(t("Nome, quantidade, dificuldade e fase", NamedTextColor.GRAY))), c -> openSpawner(p));
        set(m, 13, item(Material.PLAYER_HEAD, t("Gerenciar bots", NamedTextColor.AQUA),
                List.of(t(plugin.botManager().aliveCount() + " vivos de " + plugin.botManager().allBots().size(), NamedTextColor.GRAY))), c -> openBots(p, 0));
        set(m, 15, item(Material.WHITE_BANNER, t("Grupos", NamedTextColor.GOLD), List.of(t("Papel, inimigos e progressão", NamedTextColor.GRAY))), c -> openGroups(p));
        int slot = 19;
        Material[] icons = {Material.COMPASS, Material.ARMOR_STAND, Material.IRON_SWORD, Material.FEATHER, Material.MAP, Material.TNT, Material.CLOCK, Material.GLOWSTONE_DUST};
        for (Category c : Category.values()) {
            Category cat = c;
            set(m, slot++, item(icons[c.ordinal()], t("Opções: " + categoryName(c), NamedTextColor.YELLOW), List.of(t("Clique para editar", NamedTextColor.GRAY))),
                    ct -> openSettings(p, cat));
        }
        boolean running = plugin.botManager().countdown().isRunning();
        set(m, 29, item(Material.LIME_DYE, t(running ? "Começar a caçada agora" : "Soltar bots da roda", NamedTextColor.GREEN), List.of()), c -> {
            p.closeInventory();
            p.performCommand("bot start");
        });
        set(m, 31, item(Material.ENDER_PEARL, t("Trazer todos os bots até mim", NamedTextColor.LIGHT_PURPLE), List.of()), c -> {
            int n = plugin.botManager().teleportAllTo(p);
            msg().send(p, "comando.tpall", Messages.p("quantidade", n));
        });
        set(m, 33, item(Material.BARRIER, t("Apagar todos os bots", NamedTextColor.RED), List.of(t("Pede confirmação", NamedTextColor.GRAY))), c -> openConfirmDelete(p));
        p.openInventory(m.inventory);
    }

    private static String categoryName(Category c) {
        return switch (c) {
            case GERAL -> "Geral";
            case BOTS -> "Bots";
            case COMBATE -> "Combate";
            case MOBILIDADE -> "Mobilidade";
            case PATHFINDING -> "Pathfinding";
            case TATICAS -> "Táticas";
            case MANHUNT -> "Manhunt";
            case APARENCIA -> "Aparência";
        };
    }

    private void openConfirmDelete(Player p) {
        Menu m = create(3, "menu.titulo-confirmar");
        set(m, 11, item(Material.RED_CONCRETE, t("Sim, apagar todos", NamedTextColor.RED), List.of()), c -> {
            int n = plugin.botManager().deleteAll();
            msg().send(p, "comando.todos-removidos", Messages.p("quantidade", n));
            p.closeInventory();
        });
        set(m, 15, item(Material.GREEN_CONCRETE, t("Cancelar", NamedTextColor.GREEN), List.of()), c -> openMain(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ criação

    private void openSpawner(Player p) {
        SpawnDraft d = drafts.computeIfAbsent(p.getUniqueId(), k -> new SpawnDraft());
        if (d.difficulty == null) {
            d.difficulty = plugin.settings().defaultDifficulty.get();
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
        set(m, 16, item(Material.LIME_CONCRETE, t("Criar aqui", NamedTextColor.GREEN), List.of(t("Modo de spawn: " + plugin.settings().spawnMode.display(), NamedTextColor.GRAY))), c -> {
            p.closeInventory();
            msg().send(p, "comando.criando", Messages.p("quantidade", d.amount), Messages.p("dificuldade", d.difficulty.label()), Messages.p("nome", d.name));
            plugin.botManager().createBots(p.getLocation(), d.name, d.skin.isEmpty() ? null : d.skin, d.amount, d.difficulty, d.phase, d.aggression)
                    .thenAccept(bots -> msg().send(p, "comando.criados", Messages.p("quantidade", bots.size()), Messages.p("nome", d.name)));
        });
        set(m, 18, item(Material.ARROW, t("Voltar", NamedTextColor.GRAY), List.of()), c -> openMain(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ bots

    private void openBots(Player p, int page) {
        List<Bot> bots = new ArrayList<>(plugin.botManager().allBots());
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
                    plugin.botManager().removeBot(b);
                    openBots(p, page);
                } else if (c.isRightClick()) {
                    if (b.isAlive()) {
                        plugin.botManager().teleportBot(b, p.getLocation());
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
        set(m, 49, item(Material.OAK_DOOR, t("Voltar", NamedTextColor.GRAY), List.of()), c -> openMain(p));
        if (start + per < bots.size()) {
            set(m, 53, item(Material.ARROW, t("Próxima página", NamedTextColor.GRAY), List.of()), c -> openBots(p, page + 1));
        }
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ grupos

    private void openGroups(Player p) {
        Menu m = create(6, "menu.titulo-grupos");
        int i = 0;
        for (BotGroup g : plugin.botManager().groups()) {
            if (i >= 45) {
                break;
            }
            boolean escape = g.role() == BotRole.ESCAPE;
            set(m, i++, item(escape ? Material.RABBIT_FOOT : Material.IRON_SWORD, t(g.name(), NamedTextColor.GOLD), List.of(
                    t("Papel: " + (escape ? "fugitivo" : "caçador"), NamedTextColor.GRAY),
                    t("Persegue jogadores: " + (g.trackPlayers() ? "sim" : "não"), NamedTextColor.GRAY),
                    t("Inimigos: " + (g.enemies().isEmpty() ? "-" : String.join(", ", g.enemies())), NamedTextColor.GRAY),
                    t("Esquerdo: trocar papel • Direito: perseguir jogadores", NamedTextColor.DARK_GRAY),
                    t("Shift+direito: apagar grupo e bots", NamedTextColor.DARK_GRAY))), c -> {
                        if (c == ClickType.SHIFT_RIGHT) {
                            plugin.botManager().deleteGroup(g.name());
                        } else if (c.isRightClick()) {
                            g.setTrackPlayers(!g.trackPlayers());
                        } else {
                            g.setRole(escape ? BotRole.HUNTER : BotRole.ESCAPE);
                            plugin.glow().applyAll(plugin.botManager().aliveBots());
                        }
                        plugin.botManager().markDirty();
                        openGroups(p);
                    });
        }
        set(m, 49, item(Material.OAK_DOOR, t("Voltar", NamedTextColor.GRAY), List.of()), c -> openMain(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ opções

    private void openSettings(Player p, Category cat) {
        List<Setting<?>> list = plugin.settings().inCategory(cat);
        Menu m = create(Math.max(2, (list.size() + 8) / 9 + 1), "menu.titulo-opcoes");
        int i = 0;
        for (Setting<?> s : list) {
            Material icon;
            List<Component> lore = new ArrayList<>();
            lore.add(t(s.path(), NamedTextColor.DARK_GRAY));
            lore.add(t("Valor: " + s.display(), NamedTextColor.WHITE));
            if (s instanceof Setting.Bool b) {
                icon = b.on() ? Material.LIME_DYE : Material.GRAY_DYE;
                lore.add(t("Clique para alternar", NamedTextColor.GRAY));
            } else if (s instanceof Setting.Enumerated<?>) {
                icon = Material.PAPER;
                lore.add(t("Clique para a próxima opção", NamedTextColor.GRAY));
            } else {
                icon = Material.REPEATER;
                lore.add(t("Esquerdo +, direito -, shift = passo maior", NamedTextColor.GRAY));
            }
            Setting<?> setting = s;
            set(m, i++, item(icon, t("/bot settings " + s.key(), NamedTextColor.YELLOW), lore), c -> {
                adjust(setting, c);
                plugin.onSettingChanged(setting);
                openSettings(p, cat);
            });
        }
        set(m, m.inventory.getSize() - 5, item(Material.OAK_DOOR, t("Voltar", NamedTextColor.GRAY), List.of()), c -> openMain(p));
        p.openInventory(m.inventory);
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
