package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.EntityExt;
import dev.manhuntstar.mod.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityExt {

    @Unique
    private Object manhuntstar$wrapper;
    @Unique
    private boolean manhuntstar$fixedPose;
    @Unique
    private boolean manhuntstar$transient;

    @Shadow
    protected abstract void setSharedFlag(int flag, boolean value);

    @Override
    public void manhuntstar$setSharedFlag(int flag, boolean value) {
        setSharedFlag(flag, value);
    }

    @Override
    public Object manhuntstar$wrapper() {
        return manhuntstar$wrapper;
    }

    @Override
    public void manhuntstar$setWrapper(Object wrapper) {
        manhuntstar$wrapper = wrapper;
    }

    @Override
    public boolean manhuntstar$fixedPose() {
        return manhuntstar$fixedPose;
    }

    @Override
    public void manhuntstar$setFixedPose(boolean fixed) {
        manhuntstar$fixedPose = fixed;
    }

    @Override
    public boolean manhuntstar$transient() {
        return manhuntstar$transient;
    }

    @Override
    public void manhuntstar$setTransient(boolean value) {
        manhuntstar$transient = value;
    }

    /** A fixed pose stays until the mod changes it (as Paper's {@code setPose(pose, true)}). */
    @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$keepFixedPose(Pose pose, CallbackInfo ci) {
        if (manhuntstar$fixedPose) {
            ci.cancel();
        }
    }

    /** A transient entity is never written to the chunk (as Paper's {@code setPersistent(false)}). */
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void manhuntstar$skipTransient(CallbackInfoReturnable<Boolean> cir) {
        if (manhuntstar$transient) {
            cir.setReturnValue(false);
        }
    }

    /** An entity that moves to another dimension is copied; the core object follows it (as on Bukkit). */
    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void manhuntstar$follow(Entity old, CallbackInfo ci) {
        ModEntities.moved(old, (Entity) (Object) this);
    }
}
