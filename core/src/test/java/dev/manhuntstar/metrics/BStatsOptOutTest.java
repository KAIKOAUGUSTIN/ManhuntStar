package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import dev.manhuntstar.config.FailingSaveYamlFiles;

import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** bStats turned off for every plugin turns FastStats off in config.yml; anything else leaves it on. */
class BStatsOptOutTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());
    private final Logger logger = Logger.getAnonymousLogger();

    @TempDir
    Path dir;
    private File config;

    @BeforeEach
    void config() throws IOException {
        config = dir.resolve("config.yml").toFile();
        Files.writeString(config.toPath(), "# FastStats\nfaststats:\n  # stats\n  statistics: true\n  error-reports: true\n");
    }

    private File bStats(String text) throws IOException {
        Path file = dir.resolve("bStats/config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file.toFile();
    }

    private boolean fastStatsOn() {
        ConfigNode cfg = yaml.load(config);
        return cfg.getBoolean("faststats.statistics", true) || cfg.getBoolean("faststats.error-reports", true);
    }

    @Test
    void failedSaveIsPropagated() throws IOException {
        File disabled = bStats("enabled: false\n");
        YamlFiles failing = new FailingSaveYamlFiles(yaml, file -> {});
        assertThrows(IOException.class, () -> BStatsOptOut.carryOver(failing, disabled, config, logger));
        assertTrue(fastStatsOn());
    }

    @Test
    void bStatsOffTurnsFastStatsOff() throws IOException {
        assertTrue(BStatsOptOut.carryOver(yaml, bStats("enabled: false\nserverUuid: abc\n"), config, logger));
        ConfigNode cfg = yaml.load(config);
        assertFalse(cfg.getBoolean("faststats.statistics", true));
        assertFalse(cfg.getBoolean("faststats.error-reports", true));
        assertTrue(Files.readString(config.toPath()).contains("# stats"), "the comments stay");
    }

    @Test
    void bStatsOnLeavesFastStatsOn() throws IOException {
        assertFalse(BStatsOptOut.carryOver(yaml, bStats("enabled: true\n"), config, logger));
        assertTrue(fastStatsOn());
    }

    @Test
    void noBStatsLeavesFastStatsOn() throws IOException {
        assertFalse(BStatsOptOut.carryOver(yaml, dir.resolve("bStats/config.yml").toFile(), config, logger));
        assertFalse(BStatsOptOut.carryOver(yaml, null, config, logger));
        assertTrue(fastStatsOn());
    }
}
