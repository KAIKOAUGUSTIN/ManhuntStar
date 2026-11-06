package dev.manhuntstar.platform;

/** The sounds of a block (breaking, hitting, placing) and their volume and pitch. */
public record SoundGroup(GameSound breakSound, GameSound hitSound, GameSound placeSound, float volume, float pitch) {
}
