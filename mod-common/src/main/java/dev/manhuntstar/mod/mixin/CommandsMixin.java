package dev.manhuntstar.mod.mixin;

import com.mojang.brigadier.ParseResults;
import dev.manhuntstar.mod.ModHooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    /** /list, typed by a player or on the console, shows the bots among the players. */
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$command(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
        if (ModHooks.command(command.getContext().getSource(), commandString)) {
            ci.cancel();
        }
    }
}
