package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /** A player who respawns is a new entity; the core player follows it (as Bukkit's Player does). */
    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void manhuntstar$follow(ServerPlayer old, boolean restoreAll, CallbackInfo ci) {
        ModEntities.moved(old, (ServerPlayer) (Object) this);
    }
}
