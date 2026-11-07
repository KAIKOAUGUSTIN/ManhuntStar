package dev.manhuntstar.platform;

/** What kind of thing an entity type is, filled in by the platform for each {@link EntityKind}. */
public enum EntityTrait {
    LIVING,
    PLAYER,
    /** Has an AI (zombie, cow...). */
    MOB,
    /** Hostile (the game's Enemy). */
    ENEMY,
    PROJECTILE,
    ARROW,
    TRIDENT,
    ENDER_PEARL,
    FIREWORK,
    TNT,
    ITEM,
    EXPERIENCE_ORB,
    VEHICLE,
    BOAT,
    /** Horse, donkey, mule, camel... (the game's AbstractHorse). */
    HORSE,
    ENDER_DRAGON,
    ENDERMAN,
    SPIDER,
    SLIME,
    WARDEN,
    /** Piglin or piglin brute (the game's AbstractPiglin). */
    PIGLIN_FAMILY,
    /** The piglin itself (not the brute): leaves alone whoever wears gold. */
    PIGLIN,
    ZOMBIFIED_PIGLIN,
    HOGLIN,
    ZOGLIN,
    BREEZE,
    CREAKING,
    MAGMA_CUBE,
    MANNEQUIN,
    ARMOR_STAND,
    /** Takes extra damage from Smite (undead). */
    SENSITIVE_TO_SMITE,
    /** Takes extra damage from Bane of Arthropods. */
    SENSITIVE_TO_BANE;

    final long bit() {
        return 1L << ordinal();
    }
}
