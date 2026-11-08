package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    /** What a player types in chat; a menu waiting for it takes it, and nobody else sees it. */
    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$chat(PlayerChatMessage message, CallbackInfo ci) {
        if (ModHooks.chat(player, message.signedContent())) {
            ci.cancel();
        }
    }
}
