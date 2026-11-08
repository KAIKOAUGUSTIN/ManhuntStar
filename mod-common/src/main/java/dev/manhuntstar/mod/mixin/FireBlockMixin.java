package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    /** Fire is about to burn a block (Bukkit's BlockBurnEvent): after the chance roll, where the game reads the block again. */
    @Inject(method = "checkBurnOut", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 1))
    private void manhuntstar$burn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        ModHooks.blockChanged(level, pos);
    }
}
