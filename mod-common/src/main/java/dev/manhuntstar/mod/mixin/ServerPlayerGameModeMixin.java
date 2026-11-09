package dev.manhuntstar.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.manhuntstar.mod.ModHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerLevel level;

    /** A player broke a block (Bukkit's BlockBreakEvent), with the block it was. */
    @WrapMethod(method = "destroyBlock")
    private boolean manhuntstar$destroyed(BlockPos pos, Operation<Boolean> original) {
        BlockState was = level.getBlockState(pos);
        boolean destroyed = original.call(pos);
        if (destroyed) {
            ModHooks.playerBrokeBlock(level, pos, was);
        }
        return destroyed;
    }

    /** A right click on a crafting table (Bukkit's PlayerInteractEvent). */
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void manhuntstar$use(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (level.getBlockState(hit.getBlockPos()).is(Blocks.CRAFTING_TABLE)) {
            ModHooks.craftingTable(player);
        }
    }
}
