package dev.manhuntstar.gui;

import dev.manhuntstar.platform.GamePlayer;

/**
 * Shows the menus as chest screens and the texts they ask for as dialogs on the platform. The platform
 * sends the clicks on an open menu to {@link MenuManager#clicked}, a dialog's answer to
 * {@link MenuManager#textEntered} or {@link MenuManager#textCancelled} and the players who leave to
 * {@link MenuManager#forget}.
 */
public interface MenuScreens {

    /** Opens the menu for the player, in place of whatever screen is open. */
    void open(GamePlayer player, Menu menu);

    /** Closes the player's open screen. */
    void close(GamePlayer player);

    /** Shows the dialog with the text box, in place of whatever screen is open. */
    void askText(GamePlayer player, TextPrompt prompt);
}
