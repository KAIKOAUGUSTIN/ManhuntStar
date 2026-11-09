package dev.manhuntstar.mod;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.manhuntstar.mod.mixin.MannequinAccessor;
import dev.manhuntstar.platform.BodyProfile;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;

/** A bot's {@link Mannequin} as a core {@link BotBody}, doing what Paper's {@code CraftMannequin} does. */
public final class ModBotBody extends ModLiving implements BotBody {

    ModBotBody(Mannequin mannequin) {
        super(mannequin);
    }

    @Override
    public Mannequin vanilla() {
        return (Mannequin) super.vanilla();
    }

    private MannequinAccessor access() {
        return (MannequinAccessor) vanilla();
    }

    @Override
    public void setPose(Pose pose, boolean fixed) {
        net.minecraft.world.entity.Pose p = switch (pose) {
            case STANDING -> net.minecraft.world.entity.Pose.STANDING;
            case SNEAKING -> net.minecraft.world.entity.Pose.CROUCHING;
            case SWIMMING -> net.minecraft.world.entity.Pose.SWIMMING;
        };
        EntityExt ext = (EntityExt) vanilla();
        ext.manhuntstar$setFixedPose(false);
        vanilla().setPose(p);
        ext.manhuntstar$setFixedPose(fixed);
    }

    @Override
    public void setProfile(BodyProfile profile) {
        access().manhuntstar$setProfile(profile(profile));
    }

    /** The game profile of a body: name, id and, with a skin, the signed "textures" property. */
    static ResolvableProfile profile(BodyProfile p) {
        ImmutableMultimap.Builder<String, Property> props = ImmutableMultimap.builder();
        if (p.skinValue() != null) {
            props.put("textures", new Property("textures", p.skinValue(), p.skinSignature()));
        }
        return ResolvableProfile.createResolved(new GameProfile(p.id(), p.name(), new PropertyMap(props.build())));
    }

    /** No "Mannequin" label under the name (Paper's {@code setDescription(null)}). */
    void hideDescription() {
        access().manhuntstar$setHideDescription(true);
    }

    void setImmovable(boolean immovable) {
        access().manhuntstar$setImmovable(immovable);
    }
}
