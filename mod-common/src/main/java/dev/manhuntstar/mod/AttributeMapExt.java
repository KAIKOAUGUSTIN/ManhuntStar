package dev.manhuntstar.mod;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

/** What the mod adds to attribute maps (through a mixin): giving an entity an attribute its type lacks. */
public interface AttributeMapExt {

    /** Adds the attribute with its default value (Paper's {@code registerAttribute}). */
    void manhuntstar$register(Holder<Attribute> attribute);
}
