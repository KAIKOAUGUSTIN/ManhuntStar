package dev.manhuntstar.platform;

import dev.manhuntstar.util.Vec3;

/** Where some damage came from, like Bukkit's DamageSource. */
public interface DamageInfo {

    /** The damage type, or {@link DamageKind#OTHER} for one the AI doesn't name. */
    DamageKind kind();

    /** The type is in the game's {@code bypasses_shield} tag. */
    boolean bypassesShield();

    /** The point the damage came from, or null (suffocation, cactus, the wither effect...). */
    Vec3 sourcePosition();

    /** Who caused it (the shooter of an arrow, the one who lit the TNT...), or null. */
    GameEntity causingEntity();

    /** What hit (the arrow itself, the attacker in melee...), or null. */
    GameEntity directEntity();
}
