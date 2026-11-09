package dev.manhuntstar.platform;

/** The potion effects the AI looks at or gives, by their game id. */
public enum Effect {
    ABSORPTION("absorption"),
    BLINDNESS("blindness"),
    DOLPHINS_GRACE("dolphins_grace"),
    FIRE_RESISTANCE("fire_resistance"),
    JUMP_BOOST("jump_boost"),
    REGENERATION("regeneration"),
    SLOWNESS("slowness"),
    SPEED("speed"),
    STRENGTH("strength"),
    WEAKNESS("weakness");

    private final String key;

    Effect(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
