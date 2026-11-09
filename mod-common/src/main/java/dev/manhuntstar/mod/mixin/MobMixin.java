package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.MobExt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity implements MobExt {

    @Unique
    private boolean manhuntstar$aware = true;

    protected MobMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean manhuntstar$aware() {
        return manhuntstar$aware;
    }

    @Override
    public void manhuntstar$setAware(boolean aware) {
        manhuntstar$aware = aware;
    }

    /** A mob that isn't aware doesn't think, look or move on its own (as Paper's {@code setAware(false)}). */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$skipWhenUnaware(CallbackInfo ci) {
        if (!manhuntstar$aware) {
            this.noActionTime++;
            ci.cancel();
        }
    }
}
