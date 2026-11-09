package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameMob;
import org.bukkit.entity.Mob;

/** A Bukkit {@link Mob} as a core {@link GameMob}: every call is the Bukkit one. */
public class PaperMob extends PaperLiving implements GameMob {

    PaperMob(Mob mob) {
        super(mob);
    }

    @Override
    public Mob bukkit() {
        return (Mob) entity;
    }

    @Override
    public GameLiving target() {
        return PaperEntities.of(bukkit().getTarget());
    }

    @Override
    public void setTarget(GameLiving target) {
        bukkit().setTarget(PaperEntities.bukkit(target));
    }

    @Override
    public boolean isAware() {
        return bukkit().isAware();
    }

    @Override
    public void setAware(boolean aware) {
        bukkit().setAware(aware);
    }

    @Override
    public void stopPathfinding() {
        bukkit().getPathfinder().stopPathfinding();
    }
}
