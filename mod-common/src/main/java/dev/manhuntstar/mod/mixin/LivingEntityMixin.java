package dev.manhuntstar.mod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.manhuntstar.mod.ModHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * The damage an entity is about to take, once the game decided it can be hurt (Bukkit's damage event):
     * core may change it or cancel the hit.
     */
    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getUseItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
    private void manhuntstar$hurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) LocalFloatRef damage) {
        float d = ModHooks.hurt((LivingEntity) (Object) this, source, damage.get());
        if (d < 0) {
            cir.setReturnValue(false);
        } else {
            damage.set(d);
        }
    }

    /** A bot's body drops what core decides, not the game's loot. */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$botDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (ModHooks.botDrops((LivingEntity) (Object) this, level, source)) {
            ci.cancel();
        }
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void manhuntstar$totem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ModHooks.totemUsed((LivingEntity) (Object) this);
        }
    }

    /**
     * A chorus fruit or an enderman's teleport. Minecraft 26.2 does it in randomTeleport(x, y, z, particles),
     * 26.3 in the overload that takes what to avoid (the other 26.3 overload calls this one).
     */
    @Inject(method = {"randomTeleport(DDDZ)Z", "randomTeleport(DDDZLjava/util/function/Predicate;)Z"}, at = @At("RETURN"))
    private void manhuntstar$randomTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ModHooks.teleported((LivingEntity) (Object) this);
        }
    }
}
