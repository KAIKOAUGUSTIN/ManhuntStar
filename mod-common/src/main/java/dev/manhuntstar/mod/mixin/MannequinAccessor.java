package dev.manhuntstar.mod.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mannequin.class)
public interface MannequinAccessor {

    @Invoker("setProfile")
    void manhuntstar$setProfile(ResolvableProfile profile);

    @Invoker("setImmovable")
    void manhuntstar$setImmovable(boolean immovable);

    @Invoker("setHideDescription")
    void manhuntstar$setHideDescription(boolean hide);
}
