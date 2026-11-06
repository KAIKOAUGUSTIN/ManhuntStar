package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Pose;
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
}
