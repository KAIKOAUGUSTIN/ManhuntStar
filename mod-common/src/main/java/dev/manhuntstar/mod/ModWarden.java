package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWarden;
import net.minecraft.world.entity.monster.warden.Warden;

/** A Minecraft {@link Warden} as a core {@link GameWarden}. */
public final class ModWarden extends ModMob implements GameWarden {

    ModWarden(Warden warden) {
        super(warden);
    }

    @Override
    public Warden vanilla() {
        return (Warden) super.vanilla();
    }

    @Override
    public void increaseAnger(GameEntity target, int amount) {
        vanilla().getAngerManagement().increaseAnger(ModEntities.vanilla(target), amount);
    }
}
