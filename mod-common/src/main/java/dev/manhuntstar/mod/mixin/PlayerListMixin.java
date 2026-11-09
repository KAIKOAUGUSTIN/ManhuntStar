package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void manhuntstar$joined(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        ModHooks.playerJoined(player);
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void manhuntstar$respawned(ServerPlayer old, boolean keepAll, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
        ModHooks.playerRespawned(cir.getReturnValue());
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void manhuntstar$quit(ServerPlayer player, CallbackInfo ci) {
        ModHooks.playerQuit(player);
    }
}
