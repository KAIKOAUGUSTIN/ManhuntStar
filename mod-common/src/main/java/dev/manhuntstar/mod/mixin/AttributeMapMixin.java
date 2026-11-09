package dev.manhuntstar.mod.mixin;

import dev.manhuntstar.mod.AttributeMapExt;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AttributeMap.class)
public abstract class AttributeMapMixin implements AttributeMapExt {

    @Shadow
    @Final
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Shadow
    protected abstract void onAttributeModified(AttributeInstance instance);

    @Override
    public void manhuntstar$register(Holder<Attribute> attribute) {
        attributes.put(attribute, new AttributeInstance(attribute, this::onAttributeModified));
    }
}
