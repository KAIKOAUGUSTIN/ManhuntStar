package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameSlime;
import org.bukkit.entity.Slime;

/** A Bukkit {@link Slime} (or magma cube) as a core {@link GameSlime}. */
public final class PaperSlime extends PaperMob implements GameSlime {

    PaperSlime(Slime slime) {
        super(slime);
    }

    @Override
    public Slime bukkit() {
        return (Slime) entity;
    }

    @Override
    public int size() {
        return bukkit().getSize();
    }
}
