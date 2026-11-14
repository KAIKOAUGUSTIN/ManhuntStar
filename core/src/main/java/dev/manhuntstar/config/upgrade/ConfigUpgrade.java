package dev.manhuntstar.config.upgrade;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.metrics.BStatsOptOut;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.util.SafeFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Brings config.yml to the version of this ManhuntStar ({@code config-version}). The file is rewritten
 * from the one inside the jar, so new options come with their comments, and the owner's values (moved
 * by the steps when an option changed) are written over it. The previous file stays in
 * {@code old/config-v<version>.yml}.
 *
 * <p>Every config.yml is written whole into a temporary file first and only then put in place
 * ({@link SafeFiles#replace}): a failed write leaves the previous file, which the next start updates
 * again. bStats' choice ({@link BStatsOptOut}) goes into the same write as the faststats section, so a
 * config.yml of version 2 or later never exists without it.
 *
 * <p>Versions: 0 is the Portuguese config.yml of versions up to 1.5 (ManhuntCore's legacy migration
 * takes it to the current version with {@link #rewrite}); 1 is 2.0.0 (no key yet); 2 adds the faststats
 * section.
 */
public final class ConfigUpgrade {

    public static final FileVersions VERSIONS = new FileVersions("config-version", 1, List.of(
            // 1 -> 2: the faststats section (new options only)
            FileVersions.nothing()));

    /** The first version with FastStats: a config.yml older than it gets bStats' choice. */
    public static final int FASTSTATS = 2;

    private ConfigUpgrade() {
    }

    /**
     * Writes a new config.yml from the one inside the jar (the first start), with both FastStats options
     * off when {@code fastStatsOff}.
     *
     * @return whether it was written (nothing is written otherwise)
     */
    public static boolean create(YamlFiles yaml, File dataFolder, Supplier<InputStream> bundled, boolean fastStatsOff, Logger logger) {
        try {
            Files.createDirectories(dataFolder.toPath());
            write(dataFolder, bundled, temporary -> {
                if (fastStatsOff) {
                    ConfigNode cfg = yaml.load(temporary.toFile());
                    BStatsOptOut.turnOff(cfg);
                    yaml.save(cfg, temporary.toFile());
                }
            });
        } catch (IOException e) {
            logger.warning("Could not write config.yml: " + e.getMessage());
            return false;
        }
        if (fastStatsOff) {
            BStatsOptOut.logCarriedOver(logger);
        }
        return true;
    }

    /** Upgrades config.yml without bStats' choice; see {@link #upgrade(YamlFiles, File, Supplier, Settings, Logger, boolean)}. */
    public static int upgrade(YamlFiles yaml, File dataFolder, Supplier<InputStream> bundled, Settings settings, Logger logger) {
        return upgrade(yaml, dataFolder, bundled, settings, logger, false);
    }

    /**
     * Upgrades {@code dataFolder/config.yml} when it is older than {@link FileVersions#latest}. A
     * config.yml from a newer ManhuntStar is read as it is, with a warning. With {@code fastStatsOff}
     * (bStats was off for every plugin), a config.yml that gets the faststats section gets it turned off
     * in the same write.
     *
     * @param bundled opens the config.yml inside the jar
     * @return the version the file had, including on failure (the latest when no upgrade was needed)
     */
    public static int upgrade(YamlFiles yaml, File dataFolder, Supplier<InputStream> bundled, Settings settings, Logger logger,
            boolean fastStatsOff) {
        File config = new File(dataFolder, "config.yml");
        ConfigNode node = yaml.load(config);
        int from = VERSIONS.versionOf(node);
        int latest = VERSIONS.latest();
        if (from > latest) {
            logger.warning("config.yml is from a newer ManhuntStar (version " + from + "; this one writes " + latest
                    + "): it is read as it is.");
            return latest;
        }
        if (from == latest) {
            return latest;
        }
        VERSIONS.upgrade(node);
        boolean carryOver = fastStatsOff && from < FASTSTATS;
        if (carryOver) {
            BStatsOptOut.turnOff(node);
        }
        File backup = new File(new File(dataFolder, "old"), "config-v" + from + ".yml");
        try {
            Files.createDirectories(backup.getParentFile().toPath());
            Files.copy(config.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            settings.load(node, logger);
            rewrite(yaml, dataFolder, bundled, settings);
        } catch (IOException e) {
            logger.warning("Could not update config.yml to version " + latest + ": " + e.getMessage());
            return from;
        }
        logger.info("config.yml updated to version " + latest + " (new options added with their comments); the previous one is in old/"
                + backup.getName() + ".");
        if (carryOver) {
            BStatsOptOut.logCarriedOver(logger);
        }
        return from;
    }

    /**
     * Writes config.yml again from the one inside the jar with the values in {@code settings} over it,
     * in one step (the legacy migration and {@link #upgrade} use it).
     */
    public static void rewrite(YamlFiles yaml, File dataFolder, Supplier<InputStream> bundled, Settings settings) throws IOException {
        write(dataFolder, bundled, temporary -> settings.saveOrThrow(yaml, temporary.toFile()));
    }

    /** Does config.yml still lack the faststats section (missing, Portuguese, or older than version 2)? */
    public static boolean withoutFastStats(YamlFiles yaml, File config) {
        return !config.isFile() || Settings.isLegacy(yaml, config) || VERSIONS.versionOf(yaml.load(config)) < FASTSTATS;
    }

    @FunctionalInterface
    private interface Change {
        void apply(Path temporary) throws IOException;
    }

    /** The config.yml inside the jar into a temporary file, changed, then put in place of config.yml. */
    private static void write(File dataFolder, Supplier<InputStream> bundled, Change change) throws IOException {
        Path temporary = Files.createTempFile(dataFolder.toPath(), "config-", ".tmp");
        try {
            try (InputStream in = bundled.get()) {
                if (in == null) {
                    throw new IOException("Missing bundled config.yml");
                }
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            change.apply(temporary);
            SafeFiles.replace(temporary, new File(dataFolder, "config.yml").toPath());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
