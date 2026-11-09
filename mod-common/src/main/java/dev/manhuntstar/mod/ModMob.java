package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameMob;
import net.minecraft.world.entity.Mob;

/** A Minecraft {@link Mob} as a core {@link GameMob}, doing what Bukkit's {@code CraftMob} does. */
public class ModMob extends ModLiving implements GameMob {

    ModMob(Mob mob) {
        super(mob);
    }

    @Override
    public Mob vanilla() {
        return (Mob) super.vanilla();
    }

    @Override
    public GameLiving target() {
        return ModEntities.of(vanilla().getTarget());
    }

    @Override
    public void setTarget(GameLiving target) {
        vanilla().setTarget(ModEntities.vanilla(target));
    }

    @Override
    public boolean isAware() {
        return ((MobExt) vanilla()).manhuntstar$aware();
    }

    @Override
    public void setAware(boolean aware) {
        ((MobExt) vanilla()).manhuntstar$setAware(aware);
    }

    @Override
    public void stopPathfinding() {
        vanilla().getNavigation().stop();
    }
}
