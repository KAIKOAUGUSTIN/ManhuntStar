package dev.manhuntstar.paper;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.gui.Menu;
import dev.manhuntstar.gui.MenuClick;
import dev.manhuntstar.gui.MenuIcon;
import dev.manhuntstar.gui.MenuScreens;
import dev.manhuntstar.platform.GamePlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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

/** Core's menus as Bukkit chest inventories; clicks, chat typed for the menus and quits go back to core. */
public final class PaperMenuScreens implements MenuScreens, Listener {

    private final ManhuntStarPlugin plugin;

    /** Menu inventory: the menu it shows. */
    private static final class Holder implements InventoryHolder {
        final Menu menu;
        Inventory inventory;

        Holder(Menu menu) {
            this.menu = menu;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public PaperMenuScreens(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void open(GamePlayer player, Menu menu) {
        Holder holder = new Holder(menu);
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        for (int i = 0; i < menu.size(); i++) {
            holder.inventory.setItem(i, stack(menu.icon(i)));
        }
        PaperEntities.bukkit(player).openInventory(holder.inventory);
    }

    @Override
    public void close(GamePlayer player) {
        PaperEntities.bukkit(player).closeInventory();
    }

    private static ItemStack stack(MenuIcon icon) {
        ItemStack s = new ItemStack(PaperItems.material(icon.type()));
        ItemMeta meta = s.getItemMeta();
        meta.displayName(icon.name());
        meta.lore(icon.lore());
        if (icon.head() != null) {
            ((SkullMeta) meta).setOwningPlayer(PaperEntities.bukkit(icon.head()));
        }
        s.setItemMeta(meta);
        return s;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (plugin.menus().chatTyped(PaperEntities.of(event.getPlayer()), text)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        ClickType c = event.getClick();
        plugin.menus().clicked(PaperEntities.of(p), holder.menu, event.getRawSlot(), new MenuClick(c.isLeftClick(), c.isRightClick(), c.isShiftClick()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.menus().forget(event.getPlayer().getUniqueId());
    }
}
