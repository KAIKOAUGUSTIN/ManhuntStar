package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWarden;
import org.bukkit.entity.Warden;

/** A Bukkit {@link Warden} as a core {@link GameWarden}. */
public final class PaperWarden extends PaperMob implements GameWarden {

    PaperWarden(Warden warden) {
        super(warden);
    }

    @Override
    public Warden bukkit() {
        return (Warden) entity;
    }

    @Override
    public void increaseAnger(GameEntity target, int amount) {
        bukkit().increaseAnger(PaperEntities.bukkit(target), amount);
    }
}
