package dev.manhuntstar.gui;

import net.kyori.adventure.text.Component;

/**
 * A text the menu asks for (bot name, skin, player), shown by the platform as the game's own dialog: a
 * title, a text box with a label and the current value, and the confirm and cancel buttons. Confirming
 * sends {@link #SUBMIT} with the box's text under {@link #INPUT}; cancelling, or closing it with Esc,
 * sends {@link #CANCEL}.
 */
public record TextPrompt(Component title, Component label, String initial, int maxLength, Component confirm, Component cancel) {

    /** The text box's key in the dialog's answer. */
    public static final String INPUT = "text";
    /** The confirm button's custom click. */
    public static final String SUBMIT = "manhuntstar:menu/submit";
    /** The cancel button's custom click, also sent when the dialog is closed with Esc. */
    public static final String CANCEL = "manhuntstar:menu/cancel";

    /** The width of the text box and of the buttons, as the game measures them. */
    public static final int INPUT_WIDTH = 200;
    public static final int BUTTON_WIDTH = 100;

    public TextPrompt {
        if (initial.length() > maxLength) {
            initial = initial.substring(0, maxLength);
        }
    }
}
