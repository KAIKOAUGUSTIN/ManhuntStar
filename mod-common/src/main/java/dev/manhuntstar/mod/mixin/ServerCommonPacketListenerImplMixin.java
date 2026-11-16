package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    /**
     * A dialog's custom click (the menu's text box), from a player in game. At the end, so on the main
     * thread: the game first sends the packet there and leaves the network thread before this point.
     */
    @Inject(method = "handleCustomClickAction", at = @At("TAIL"))
    private void manhuntstar$customClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            ModHooks.dialog(game.player, packet.id(), packet.payload());
        }
    }
}
