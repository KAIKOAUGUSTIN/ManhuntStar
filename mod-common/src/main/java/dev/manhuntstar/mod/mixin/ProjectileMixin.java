package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    /** A projectile hit a block or an entity (Bukkit's ProjectileHitEvent). */
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"))
    private void manhuntstar$hit(HitResult result, CallbackInfoReturnable<ProjectileDeflection> cir) {
        ModHooks.projectileHit((Projectile) (Object) this, result);
    }
}
