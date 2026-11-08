package dev.manhuntstar.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.manhuntstar.mod.ModHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    /** A player placed a block (Bukkit's BlockPlaceEvent). */
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean manhuntstar$placed(BlockItem self, BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        boolean placed = original.call(self, context, state);
        if (placed && context.getPlayer() instanceof ServerPlayer) {
            ModHooks.playerPlacedBlock(context.getLevel(), context.getClickedPos());
        }
        return placed;
    }
}
