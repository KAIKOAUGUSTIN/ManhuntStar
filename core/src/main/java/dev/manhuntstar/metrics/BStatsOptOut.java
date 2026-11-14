package dev.manhuntstar.metrics;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.util.logging.Logger;

/**
 * Up to 2.0.0 the Paper plugin sent its statistics to bStats, which server owners turn off for every
 * plugin with {@code enabled: false} in {@code plugins/bStats/config.yml}. That choice is kept: when
 * config.yml gets the faststats section (a new config.yml, an older one being updated, or the Portuguese
 * one of 1.5), both FastStats options are written off in the same write (ConfigUpgrade). Afterwards they
 * are the owner's to change.
 */
public final class BStatsOptOut {

    private BStatsOptOut() {
    }

    /** Is bStats off for every plugin? False without its config (the mods, or a server that never had it). */
    public static boolean isOff(YamlFiles yaml, File bStatsConfig) {
        return bStatsConfig != null && bStatsConfig.isFile() && !yaml.load(bStatsConfig).getBoolean("enabled", true);
    }

    /** Turns both FastStats options off in a config.yml being written. */
    public static void turnOff(ConfigNode config) {
        config.set(Settings.STATISTICS_PATH, false);
        config.set(Settings.ERROR_REPORTS_PATH, false);
    }

    /** Both options off in the settings (when config.yml couldn't be written with them this start). */
    public static void turnOff(Settings settings) {
        settings.statistics.set(false);
        settings.errorReports.set(false);
    }

    public static void logCarriedOver(Logger logger) {
        logger.info("bStats was off for every plugin, so FastStats starts off too (faststats section in config.yml).");
    }
}
