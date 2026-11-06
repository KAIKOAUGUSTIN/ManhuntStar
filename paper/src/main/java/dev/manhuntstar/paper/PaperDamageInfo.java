package dev.manhuntstar.paper;

import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.util.Vec3;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.tag.DamageTypeTags;

/** A Bukkit {@link DamageSource} as a core {@link DamageInfo}: every call is the Bukkit one. */
public record PaperDamageInfo(DamageSource bukkit) implements DamageInfo {

    /** The Bukkit source behind a core one. */
    public static DamageSource bukkit(DamageInfo info) {
        return ((PaperDamageInfo) info).bukkit();
    }

    @Override
    public DamageKind kind() {
        return PaperDamage.kind(bukkit.getDamageType());
    }

    @Override
    public boolean bypassesShield() {
        return DamageTypeTags.BYPASSES_SHIELD.isTagged(bukkit.getDamageType());
    }

    @Override
    public Vec3 sourcePosition() {
        Location l = bukkit.getSourceLocation();
        return l == null ? null : new Vec3(l.getX(), l.getY(), l.getZ());
    }

    @Override
    public GameEntity causingEntity() {
        return PaperEntities.of(bukkit.getCausingEntity());
    }

    @Override
    public GameEntity directEntity() {
        return PaperEntities.of(bukkit.getDirectEntity());
    }
}
