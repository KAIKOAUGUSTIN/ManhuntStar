package dev.manhuntstar.paper;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.gui.Menu;
import dev.manhuntstar.gui.MenuClick;
import dev.manhuntstar.gui.MenuIcon;
import dev.manhuntstar.gui.MenuScreens;
import dev.manhuntstar.gui.TextPrompt;
import dev.manhuntstar.platform.GamePlayer;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
 * Core's menus as Bukkit chest inventories and the texts they ask for as the game's dialogs; clicks,
 * dialog answers and quits go back to core.
 */
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

    @Override
    public void askText(GamePlayer player, TextPrompt prompt) {
        // PaperEntities.bukkit gives null for no player
        Player p = PaperEntities.bukkit(player);
        if (p == null) {
            return;
        }
        DialogBase base = DialogBase.builder(prompt.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text(TextPrompt.INPUT, prompt.label())
                        .width(TextPrompt.INPUT_WIDTH)
                        .initial(prompt.initial())
                        .maxLength(prompt.maxLength())
                        .build()))
                .build();
        // a confirmation dialog: Esc does what its second button does
        DialogType type = DialogType.confirmation(button(prompt.confirm(), TextPrompt.SUBMIT_CLICK),
                button(prompt.cancel(), TextPrompt.CANCEL_CLICK));
        p.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /** A button that sends {@code click} with the dialog's inputs. */
    private static ActionButton button(Component label, String click) {
        return ActionButton.create(label, null, TextPrompt.BUTTON_WIDTH, DialogAction.customClick(Key.key(click), null));
    }

    /** The answer to a dialog of {@link #askText}: the confirmed text or the cancel. */
    @EventHandler
    public void onDialog(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        String id = event.getIdentifier().asString();
        if (id.equals(TextPrompt.SUBMIT_CLICK)) {
            DialogResponseView answer = event.getDialogResponseView();
            String text = answer == null ? null : answer.getText(TextPrompt.INPUT);
            plugin.core().menus().textEntered(PaperEntities.of(connection.getPlayer()), text);
        } else if (id.equals(TextPrompt.CANCEL_CLICK)) {
            plugin.core().menus().textCancelled(PaperEntities.of(connection.getPlayer()));
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
        plugin.core().menus().clicked(PaperEntities.of(p), holder.menu, event.getRawSlot(), new MenuClick(c.isLeftClick(), c.isRightClick(), c.isShiftClick()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.core().menus().forget(event.getPlayer().getUniqueId());
    }
}
