package dev.manhuntstar.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Fabric mod's entry point. */
public final class ManhuntStarFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Manhunt*");

    @Override
    public void onInitialize() {
        LOGGER.info("Manhunt* loaded on Fabric");
    }
}
