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
    VEHICLE,
    BOAT,
    /** Horse, donkey, mule, camel... (the game's AbstractHorse). */
    HORSE,
    ENDER_DRAGON,
    ENDERMAN,
    SPIDER,
    SLIME,
    WARDEN,
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
