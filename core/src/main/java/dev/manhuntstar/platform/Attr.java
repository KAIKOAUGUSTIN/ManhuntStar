package dev.manhuntstar.platform;

/** The attributes (of entities and of item modifiers) the AI looks at or changes, by their game id. */
public enum Attr {
    ARMOR("armor"),
    ARMOR_TOUGHNESS("armor_toughness"),
    ATTACK_DAMAGE("attack_damage"),
    ATTACK_SPEED("attack_speed"),
    FOLLOW_RANGE("follow_range"),
    MAX_HEALTH("max_health"),
    MOVEMENT_SPEED("movement_speed");

    private final String key;

    Attr(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
