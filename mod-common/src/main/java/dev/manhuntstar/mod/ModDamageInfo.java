package dev.manhuntstar.mod;

import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.util.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** A Minecraft {@link DamageSource} as a core {@link DamageInfo}, as Bukkit's {@code CraftDamageSource} reads it. */
public record ModDamageInfo(DamageSource vanilla) implements DamageInfo {

    /** The source behind a core one. */
    public static DamageSource vanilla(DamageInfo info) {
        return ((ModDamageInfo) info).vanilla();
    }

    @Override
    public DamageKind kind() {
        return ModRegistry.kind(vanilla.typeHolder());
    }

    @Override
    public boolean bypassesShield() {
        return vanilla.is(DamageTypeTags.BYPASSES_SHIELD);
    }

    @Override
    public Vec3 sourcePosition() {
        net.minecraft.world.phys.Vec3 p = vanilla.getSourcePosition();
        return p == null ? null : ModConvert.vec(p);
    }

    @Override
    public GameEntity causingEntity() {
        return ModEntities.of(vanilla.getEntity());
    }

    @Override
    public GameEntity directEntity() {
        return ModEntities.of(vanilla.getDirectEntity());
    }
}
