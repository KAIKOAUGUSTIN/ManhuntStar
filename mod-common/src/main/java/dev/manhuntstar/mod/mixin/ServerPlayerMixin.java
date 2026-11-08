package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.ModEntities;
import dev.manhuntstar.mod.ModHooks;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /** A player who respawns is a new entity; the core player follows it (as Bukkit's Player does). */
    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void manhuntstar$follow(ServerPlayer old, boolean restoreAll, CallbackInfo ci) {
        ModEntities.moved(old, (ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void manhuntstar$died(DamageSource source, CallbackInfo ci) {
        ModHooks.playerDied((ServerPlayer) (Object) this, source);
    }

    /** A crafting table's screen opened. */
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void manhuntstar$openedMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (cir.getReturnValue().isPresent() && self.containerMenu instanceof CraftingMenu) {
            ModHooks.craftingTable(self);
        }
    }
}
