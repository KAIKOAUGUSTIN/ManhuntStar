package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    /** The scheduler's heartbeat, at the start of every tick (as Bukkit's). */
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void manhuntstar$tick(BooleanSupplier haveTime, CallbackInfo ci) {
        ModHooks.serverTick();
    }

    /** The server list: the bots count as online players. */
    @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
    private void manhuntstar$status(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus status = cir.getReturnValue();
        ServerStatus changed = ModHooks.serverStatus(status);
        if (changed != status) {
            cir.setReturnValue(changed);
        }
    }
}
