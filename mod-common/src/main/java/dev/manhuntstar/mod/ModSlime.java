package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameSlime;
import net.minecraft.world.entity.monster.cubemob.Slime;

/** A Minecraft {@link Slime} (or magma cube) as a core {@link GameSlime}. */
public final class ModSlime extends ModMob implements GameSlime {

    ModSlime(Slime slime) {
        super(slime);
    }

    @Override
    public Slime vanilla() {
        return (Slime) super.vanilla();
    }

    @Override
    public int size() {
        return vanilla().getSize();
    }
}
