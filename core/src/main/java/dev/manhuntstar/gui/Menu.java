package dev.manhuntstar.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** A chest menu: a title, an icon in every slot and what clicking each slot does. */
public final class Menu {

    private final Component title;
    private final MenuIcon[] icons;
    private final Map<Integer, Consumer<MenuClick>> actions = new HashMap<>();

    Menu(Component title, int rows, MenuIcon filler) {
        this.title = title;
        this.icons = new MenuIcon[rows * 9];
        Arrays.fill(icons, filler);
    }

    public Component title() {
        return title;
    }

    /** Number of slots (rows times 9). */
    public int size() {
        return icons.length;
    }

    public MenuIcon icon(int slot) {
        return icons[slot];
    }

    void set(int slot, MenuIcon icon, Consumer<MenuClick> action) {
        icons[slot] = icon;
        if (action != null) {
            actions.put(slot, action);
        }
    }

    Consumer<MenuClick> action(int slot) {
        return actions.get(slot);
    }
}
