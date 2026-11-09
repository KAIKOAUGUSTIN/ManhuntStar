package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    /** Water or lava flows into a block (Bukkit's BlockFromToEvent). */
    @Inject(method = "spreadTo", at = @At("HEAD"))
    private void manhuntstar$flow(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target, CallbackInfo ci) {
        ModHooks.blockChanged(level, pos);
    }
}
