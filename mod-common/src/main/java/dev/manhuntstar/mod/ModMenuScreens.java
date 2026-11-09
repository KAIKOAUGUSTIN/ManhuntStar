package dev.manhuntstar.mod;

import dev.manhuntstar.ManhuntCore;
import dev.manhuntstar.gui.Menu;
import dev.manhuntstar.gui.MenuClick;
import dev.manhuntstar.gui.MenuIcon;
import dev.manhuntstar.gui.MenuScreens;
import dev.manhuntstar.platform.GamePlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Core's menus as vanilla chest screens, so players need nothing installed. No item ever moves: every
 * click is turned into a {@link MenuClick} for core (as Bukkit's click types read it) and the game sends
 * the slots back as they were.
 */
public final class ModMenuScreens implements MenuScreens {

    private final ManhuntCore core;

    public ModMenuScreens(ManhuntCore core) {
        this.core = core;
    }

    /** The chest screen of a menu. */
    private final class Screen extends ChestMenu {
        final Menu menu;

        Screen(MenuType<?> type, int id, Inventory inventory, SimpleContainer container, int rows, Menu menu) {
            super(type, id, inventory, container, rows);
            this.menu = menu;
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (slot < 0 || slot >= menu.size() || !(player instanceof ServerPlayer p)) {
                return;
            }
            MenuClick click = click(input, button);
            if (click != null) {
                core.menus().clicked(ModEntities.of(p), menu, slot, click);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /**
     * Bukkit's click type of a click, as left/right/shift: LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT and
     * DOUBLE_CLICK; a number key, the offhand key, a middle click or a drop is neither (null for a drag,
     * which Bukkit doesn't report as a click).
     */
    static MenuClick click(ContainerInput input, int button) {
        return switch (input) {
            case PICKUP -> new MenuClick(button == 0, button == 1, false);
            case QUICK_MOVE -> new MenuClick(button == 0, button == 1, true);
            case PICKUP_ALL -> new MenuClick(true, false, false);
            case SWAP, CLONE, THROW -> new MenuClick(false, false, false);
            case QUICK_CRAFT -> null;
        };
    }

    private static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public void open(GamePlayer player, Menu menu) {
        ServerPlayer p = ModEntities.vanilla(player);
        int rows = menu.size() / 9;
        SimpleContainer container = new SimpleContainer(menu.size());
        for (int i = 0; i < menu.size(); i++) {
            container.setItem(i, stack(menu.icon(i)));
        }
        Component title = ModServer.get().vanilla(menu.title());
        p.openMenu(new SimpleMenuProvider((id, inventory, who) -> new Screen(type(rows), id, inventory, container, rows, menu), title));
    }

    @Override
    public void close(GamePlayer player) {
        ModEntities.vanilla(player).closeContainer();
    }

    private static ItemStack stack(MenuIcon icon) {
        if (icon == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(ModTypes.item(icon.type()));
        ModServer server = ModServer.get();
        if (icon.name() != null) {
            s.set(DataComponents.CUSTOM_NAME, server.vanilla(icon.name()));
        }
        if (icon.lore() != null && !icon.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (net.kyori.adventure.text.Component line : icon.lore()) {
                lines.add(server.vanilla(line));
            }
            s.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (icon.head() != null) {
            s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(ModEntities.vanilla(icon.head()).getGameProfile()));
        }
        return s;
    }
}
