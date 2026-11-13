package dev.manhuntstar.metrics;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Up to 2.0.0 the Paper plugin sent its statistics to bStats, which server owners turn off for every
 * plugin with {@code enabled: false} in {@code plugins/bStats/config.yml}. That choice is kept: when
 * config.yml gets the faststats section (a new config.yml, or an older one being updated) and bStats is
 * off, both FastStats options start off. Afterwards they are the owner's to change.
 */
public final class BStatsOptOut {

    private BStatsOptOut() {
    }

    /** Is bStats off for every plugin? False without its config (the mods, or a server that never had it). */
    public static boolean isOff(YamlFiles yaml, File bStatsConfig) {
        return bStatsConfig != null && bStatsConfig.isFile() && !yaml.load(bStatsConfig).getBoolean("enabled", true);
    }

    /**
     * Turns off {@code faststats.statistics} and {@code faststats.error-reports} in config.yml when bStats
     * is off (the file keeps its comments).
     *
     * @return whether it did
     */
    public static boolean carryOver(YamlFiles yaml, File bStatsConfig, File config, Logger logger) {
        if (!isOff(yaml, bStatsConfig)) {
            return false;
        }
        ConfigNode cfg = yaml.load(config);
        cfg.set(Settings.STATISTICS_PATH, false);
        cfg.set(Settings.ERROR_REPORTS_PATH, false);
        try {
            yaml.save(cfg, config);
        } catch (IOException e) {
            logger.warning("Could not write config.yml: " + e.getMessage());
            return false;
        }
        logger.info("bStats was off for every plugin, so FastStats starts off too (faststats section in config.yml).");
        return true;
    }
}
