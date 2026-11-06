package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;
import org.bukkit.entity.Projectile;

/** A Bukkit {@link Projectile} as a core {@link GameProjectile}: every call is the Bukkit one. */
public class PaperProjectile extends PaperEntity implements GameProjectile {

    PaperProjectile(Projectile projectile) {
        super(projectile);
    }

    @Override
    public Projectile bukkit() {
        return (Projectile) entity;
    }

    @Override
    public void setShooter(GameLiving shooter) {
        bukkit().setShooter(PaperEntities.bukkit(shooter));
    }
}
