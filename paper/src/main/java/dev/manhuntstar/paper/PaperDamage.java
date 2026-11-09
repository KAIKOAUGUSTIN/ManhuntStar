package dev.manhuntstar.paper;

import dev.manhuntstar.platform.DamageKind;
import org.bukkit.damage.DamageType;

/** Conversions between core {@link DamageKind}s and Bukkit {@link DamageType}s. */
public final class PaperDamage {

    private PaperDamage() {
    }

    public static DamageType type(DamageKind kind) {
        return switch (kind) {
            case PLAYER_ATTACK -> DamageType.PLAYER_ATTACK;
            case ENDER_PEARL -> DamageType.ENDER_PEARL;
            case STARVE -> DamageType.STARVE;
            case FALL -> DamageType.FALL;
            case ON_FIRE -> DamageType.ON_FIRE;
            case IN_FIRE -> DamageType.IN_FIRE;
            case LAVA -> DamageType.LAVA;
            case OUT_OF_WORLD -> DamageType.OUT_OF_WORLD;
            case GENERIC_KILL -> DamageType.GENERIC_KILL;
            case MAGIC -> DamageType.MAGIC;
            case INDIRECT_MAGIC -> DamageType.INDIRECT_MAGIC;
            case DROWN -> DamageType.DROWN;
            case MOB_ATTACK -> DamageType.MOB_ATTACK;
            case SPEAR -> DamageType.SPEAR;
            case OTHER -> DamageType.GENERIC;
        };
    }

    public static DamageKind kind(DamageType type) {
        for (DamageKind k : DamageKind.values()) {
            if (k != DamageKind.OTHER && type(k).equals(type)) {
                return k;
            }
        }
        return DamageKind.OTHER;
    }
}
