package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities entering and leaving a world (Paper's EntityAddToWorldEvent and EntityRemoveFromWorldEvent). */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityCallbacksMixin {

    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void manhuntstar$added(Entity entity, CallbackInfo ci) {
        ModHooks.entityAdded(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void manhuntstar$removed(Entity entity, CallbackInfo ci) {
        ModHooks.entityRemoved(entity);
    }
}
