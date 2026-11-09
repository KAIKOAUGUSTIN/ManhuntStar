package dev.manhuntstar.gui;

import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.ItemType;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * What a menu slot shows: an item with a name and description lines.
 *
 * @param head for a player head, the online player whose skin it wears (null for the plain head)
 */
public record MenuIcon(ItemType type, Component name, List<Component> lore, GamePlayer head) {
}
