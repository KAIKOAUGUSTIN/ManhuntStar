package dev.manhuntstar.platform;

/** The enchantments the AI looks at or gives, by their game id. */
public enum Enchant {
    BANE_OF_ARTHROPODS("bane_of_arthropods"),
    EFFICIENCY("efficiency"),
    FIRE_ASPECT("fire_aspect"),
    KNOCKBACK("knockback"),
    LOYALTY("loyalty"),
    PROTECTION("protection"),
    SHARPNESS("sharpness"),
    SMITE("smite"),
    SWEEPING_EDGE("sweeping_edge");

    private final String key;

    Enchant(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
