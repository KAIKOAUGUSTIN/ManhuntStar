package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {

    /**
     * /kill on the dragon: the game removes it and counts the fight as won, without the death that drops its
     * loot (where the other deaths are seen). It still ends the match as a victory.
     */
    @Inject(method = "kill", at = @At("HEAD"))
    private void manhuntstar$killed(ServerLevel level, CallbackInfo ci) {
        ModHooks.dragonKilled();
    }
}
