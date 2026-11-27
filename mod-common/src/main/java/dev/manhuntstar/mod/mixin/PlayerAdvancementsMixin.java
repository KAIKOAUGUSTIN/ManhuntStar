package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    /** A criterion was granted and the advancement is now done (Bukkit's PlayerAdvancementDoneEvent). */
    @Inject(method = "award", at = @At("RETURN"))
    private void manhuntstar$awarded(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone()) {
            ModHooks.advancementDone(player, advancement.id().toString());
        }
    }
}
