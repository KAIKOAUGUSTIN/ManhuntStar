package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** general.metrics and general.error-reports read from config.yml before core starts (the mods do). */
class SettingsBeforeStartTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    @TempDir
    Path dir;

    private File config(String text) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toFile();
    }

    @Test
    void onWithoutAConfigFile() {
        File missing = dir.resolve("config.yml").toFile();
        assertTrue(Settings.metricsEnabled(yaml, missing));
        assertTrue(Settings.errorReportsEnabled(yaml, missing));
    }

    @Test
    void onWhenTheKeysAreMissing() throws IOException {
        File file = config("general:\n  language: en\n");
        assertTrue(Settings.metricsEnabled(yaml, file));
        assertTrue(Settings.errorReportsEnabled(yaml, file));
    }

    @Test
    void eachKeyTurnsOffItsOwnThing() throws IOException {
        File file = config("general:\n  metrics: false\n  error-reports: true\n");
        assertFalse(Settings.metricsEnabled(yaml, file));
        assertTrue(Settings.errorReportsEnabled(yaml, file));

        file = config("general:\n  metrics: on\n  error-reports: off\n");
        assertTrue(Settings.metricsEnabled(yaml, file));
        assertFalse(Settings.errorReportsEnabled(yaml, file));
    }

    @Test
    void anInvalidValueKeepsTheDefault() throws IOException {
        File file = config("general:\n  metrics: maybe\n");
        assertTrue(Settings.metricsEnabled(yaml, file));
    }
}
