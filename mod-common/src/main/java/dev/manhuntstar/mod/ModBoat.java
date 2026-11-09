package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/** A Minecraft boat or raft as a core {@link GameBoat}. */
public final class ModBoat extends ModEntity implements GameBoat {

    ModBoat(AbstractBoat boat) {
        super(boat);
    }
}
