package dev.manhuntstar.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.manhuntstar.platform.BodyProfile;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Pose;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Mannequin;

/** A bot's {@link Mannequin} as a core {@link BotBody}: every call is the Bukkit one. */
public final class PaperBotBody extends PaperLiving implements BotBody {

    PaperBotBody(Mannequin mannequin) {
        super(mannequin);
    }

    @Override
    public Mannequin bukkit() {
        return (Mannequin) entity;
    }

    @Override
    public void setPose(Pose pose, boolean fixed) {
        bukkit().setPose(org.bukkit.entity.Pose.valueOf(pose.name()), fixed);
    }

    @Override
    public void setProfile(BodyProfile profile) {
        bukkit().setProfile(profile(profile));
    }

    /** The Paper profile of a body: name, id and, with a skin, the signed "textures" property. */
    static ResolvableProfile profile(BodyProfile p) {
        ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(p.name()).uuid(p.id());
        if (p.skinValue() != null) {
            b.addProperty(new ProfileProperty("textures", p.skinValue(), p.skinSignature()));
        }
        return b.build();
    }
}
