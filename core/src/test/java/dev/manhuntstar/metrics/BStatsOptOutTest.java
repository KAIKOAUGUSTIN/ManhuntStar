package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** bStats turned off for every plugin turns FastStats off; anything else leaves it on. */
class BStatsOptOutTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    @TempDir
    Path dir;

    private File bStats(String text) throws IOException {
        Path file = dir.resolve("bStats/config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file.toFile();
    }

    @Test
    void bStatsOffIsSeen() throws IOException {
        assertTrue(BStatsOptOut.isOff(yaml, bStats("enabled: false\nserverUuid: abc\n")));
    }

    @Test
    void bStatsOnOrMissingIsNotOff() throws IOException {
        assertFalse(BStatsOptOut.isOff(yaml, bStats("enabled: true\n")));
        assertFalse(BStatsOptOut.isOff(yaml, dir.resolve("none/config.yml").toFile()));
        assertFalse(BStatsOptOut.isOff(yaml, null));
    }

    @Test
    void turnsBothOptionsOffInAConfigBeingWritten() {
        ConfigNode cfg = yaml.load(new StringReader("faststats:\n  # stats\n  statistics: true\n  error-reports: true\n"));
        BStatsOptOut.turnOff(cfg);
        assertFalse(cfg.getBoolean("faststats.statistics", true));
        assertFalse(cfg.getBoolean("faststats.error-reports", true));
        assertTrue(yaml.saveToString(cfg).contains("# stats"), "the comments stay");
    }

    @Test
    void turnsBothOptionsOffInTheSettings() {
        Settings settings = new Settings();
        settings.setLanguages(() -> List.of("en"));
        BStatsOptOut.turnOff(settings);
        assertFalse(settings.statistics.on());
        assertFalse(settings.errorReports.on());
    }
}
