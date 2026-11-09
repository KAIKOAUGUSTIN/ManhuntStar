package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    /** The blocks an explosion breaks (Bukkit's block and entity explode events). */
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void manhuntstar$exploded(List<BlockPos> blocks, CallbackInfo ci) {
        ModHooks.blocksExploded(level, blocks);
    }
}
