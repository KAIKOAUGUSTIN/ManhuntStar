package dev.manhuntstar.gui;

/**
 * How a menu slot was clicked: {@code left} (also a double click), {@code right}, and whether with shift.
 * A middle click or a number key is neither left nor right.
 */
public record MenuClick(boolean left, boolean right, boolean shift) {
}
