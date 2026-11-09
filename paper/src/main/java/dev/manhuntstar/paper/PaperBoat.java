package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameBoat;
import org.bukkit.entity.Boat;

/** A Bukkit {@link Boat} as a core {@link GameBoat}. */
public final class PaperBoat extends PaperEntity implements GameBoat {

    PaperBoat(Boat boat) {
        super(boat);
    }

    @Override
    public Boat bukkit() {
        return (Boat) entity;
    }
}
