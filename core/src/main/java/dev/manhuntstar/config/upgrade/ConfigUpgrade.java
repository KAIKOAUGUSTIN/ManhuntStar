package dev.manhuntstar.config.upgrade;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
 * <p>Versions: 0 is the Portuguese config.yml of versions up to 1.5 (ManhuntCore's legacy migration
 * takes it to the current version); 1 is 2.0.0 (no key yet); 2 adds the faststats section.
 */
public final class ConfigUpgrade {

    public static final FileVersions VERSIONS = new FileVersions("config-version", 1, List.of(
            // 1 -> 2: the faststats section (new options only)
            FileVersions.nothing()));

    /** The first version with FastStats: a config.yml older than it gets bStats' choice (ManhuntCore). */
    public static final int FASTSTATS = 2;

    private ConfigUpgrade() {
    }

    /**
     * Upgrades {@code dataFolder/config.yml} when it is older than {@link FileVersions#latest}. A
     * config.yml from a newer ManhuntStar is read as it is, with a warning.
     *
     * @param bundled opens the config.yml inside the jar
     * @return the version the file had, including on failure (the latest when no upgrade was needed)
     */
    public static int upgrade(YamlFiles yaml, File dataFolder, Supplier<InputStream> bundled, Settings settings, Logger logger) {
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
        File backup = new File(new File(dataFolder, "old"), "config-v" + from + ".yml");
        File temporary = null;
        try {
            Files.createDirectories(backup.getParentFile().toPath());
            Files.copy(config.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            temporary = Files.createTempFile(dataFolder.toPath(), "config-", ".tmp").toFile();
            try (InputStream in = bundled.get()) {
                if (in == null) {
                    throw new IOException("Missing bundled config.yml");
                }
                Files.copy(in, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            settings.load(node, logger);
            settings.saveOrThrow(yaml, temporary);
            Files.move(temporary.toPath(), config.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Could not update config.yml to version " + latest + ": " + e.getMessage());
            return from;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary.toPath());
                } catch (IOException e) {
                    logger.warning("Could not remove temporary config.yml: " + e.getMessage());
                }
            }
        }
        logger.info("config.yml updated to version " + latest + " (new options added with their comments); the previous one is in old/"
                + backup.getName() + ".");
        return from;
    }
}
