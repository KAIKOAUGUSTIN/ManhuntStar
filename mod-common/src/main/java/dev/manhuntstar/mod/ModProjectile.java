package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameProjectile;
import net.minecraft.world.entity.projectile.Projectile;

/** A Minecraft {@link Projectile} as a core {@link GameProjectile}, doing what Bukkit's {@code CraftProjectile} does. */
public class ModProjectile extends ModEntity implements GameProjectile {

    ModProjectile(Projectile projectile) {
        super(projectile);
    }

    @Override
    public Projectile vanilla() {
        return (Projectile) super.vanilla();
    }

    @Override
    public void setShooter(GameLiving shooter) {
        vanilla().setOwner(ModEntities.vanilla(shooter));
    }

    @Override
    public GameEntity shooter() {
        return ModEntities.of(vanilla().getOwner());
    }
}
