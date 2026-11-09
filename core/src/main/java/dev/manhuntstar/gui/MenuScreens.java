package dev.manhuntstar.gui;

import dev.manhuntstar.platform.GamePlayer;

/**
 * Shows the menus as chest screens on the platform. The platform sends the clicks on an open menu to
 * {@link MenuManager#clicked}, what a player types in chat to {@link MenuManager#chatTyped} and the
 * players who leave to {@link MenuManager#forget}.
 */
public interface MenuScreens {

    /** Opens the menu for the player, in place of whatever screen is open. */
    void open(GamePlayer player, Menu menu);

    /** Closes the player's open screen. */
    void close(GamePlayer player);
}
