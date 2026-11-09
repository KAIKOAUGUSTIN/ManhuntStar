package dev.manhuntstar.mod.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {

    @Invoker("setFlag")
    void manhuntstar$setFlag(int flag, boolean value);
}
