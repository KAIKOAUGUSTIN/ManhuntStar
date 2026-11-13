package dev.manhuntstar.config.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.config.FailingSaveYamlFiles;
import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** config.yml from an older ManhuntStar: new options with their comments, the owner's values kept. */
class ConfigUpgradeTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());
    private final Logger logger = Logger.getAnonymousLogger();

    @TempDir
    Path dir;

    /** What the platforms' saveResource does: the config.yml inside the jar, where there is none. */
    private void saveBundled() {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            Files.copy(in, dir.resolve("config.yml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Settings settings() {
        Settings s = new Settings();
        s.setLanguages(() -> List.of("en", "pt-br"));
        return s;
    }

    /** A 2.0.0 config.yml: no version, no faststats section, some values changed by the owner. */
    private static final String V1 = """
            general:
              language: pt-br
              update-check: false
            bots:
              max-health: 40
              mobs-that-ignore-bots:
              - CREEPER
            manhunt:
              player-list:
              - Steve
            """;

    @Test
    void failedSaveLeavesActiveConfigAndBackupIntact() throws IOException {
        Path active = dir.resolve("config.yml");
        Files.writeString(active, V1);
        YamlFiles failing = new FailingSaveYamlFiles(yaml, temporary -> {
            assertFalse(temporary.toPath().equals(active));
            assertEquals("pt-br", yaml.load(active.toFile()).getString("general.language"));
        });
        assertEquals(ConfigUpgrade.VERSIONS.latest(), ConfigUpgrade.upgrade(failing, dir.toFile(),
                () -> getClass().getResourceAsStream("/config.yml"), settings(), logger));
        assertEquals(V1, Files.readString(active));
        assertEquals(V1, Files.readString(dir.resolve("old/config-v1.yml")));
        try (var files = Files.list(dir)) {
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".tmp")));
        }
    }

    @Test
    void customLanguageSurvivesUpgrade() throws IOException {
        Files.writeString(dir.resolve("config.yml"), V1.replace("pt-br", "custom"));
        Settings settings = settings();
        settings.setLanguages(() -> List.of("en", "custom"));
        ConfigUpgrade.upgrade(yaml, dir.toFile(), () -> getClass().getResourceAsStream("/config.yml"), settings, logger);
        assertEquals("custom", yaml.load(dir.resolve("config.yml").toFile()).getString("general.language"));
    }

    @Test
    void anOlderConfigGetsTheNewOptionsAndKeepsTheValues() throws IOException {
        Files.writeString(dir.resolve("config.yml"), V1, StandardCharsets.UTF_8);
        int from = ConfigUpgrade.upgrade(yaml, dir.toFile(), () -> getClass().getResourceAsStream("/config.yml"), settings(), logger);

        assertEquals(1, from);
        assertTrue(Files.isRegularFile(dir.resolve("old/config-v1.yml")));
        String text = Files.readString(dir.resolve("config.yml"), StandardCharsets.UTF_8);
        assertTrue(text.contains("# Anonymous statistics"), "the new options come with their comments");
        ConfigNode cfg = yaml.load(dir.resolve("config.yml").toFile());
        assertEquals(ConfigUpgrade.VERSIONS.latest(), cfg.getInt("config-version", 0));
        assertEquals("pt-br", cfg.getString("general.language"));
        assertFalse(cfg.getBoolean("general.update-check", true));
        assertEquals(40, cfg.getInt("bots.max-health", 0));
        assertEquals(List.of("CREEPER"), cfg.getStringList("bots.mobs-that-ignore-bots"));
        assertEquals(List.of("Steve"), cfg.getStringList("manhunt.player-list"));
        assertTrue(cfg.getBoolean("faststats.statistics", false));
        assertTrue(cfg.getBoolean("faststats.error-reports", false));
    }

    @Test
    void theLatestVersionIsLeftAlone() throws IOException {
        saveBundled();
        String before = Files.readString(dir.resolve("config.yml"));
        assertEquals(ConfigUpgrade.VERSIONS.latest(), ConfigUpgrade.upgrade(yaml, dir.toFile(), () -> getClass().getResourceAsStream("/config.yml"), settings(), logger));
        assertEquals(before, Files.readString(dir.resolve("config.yml")));
        assertFalse(Files.exists(dir.resolve("old")));
    }

    @Test
    void aConfigFromANewerManhuntStarIsLeftAlone() throws IOException {
        String newer = "config-version: 99\ngeneral:\n  language: fr\n";
        Files.writeString(dir.resolve("config.yml"), newer);
        ConfigUpgrade.upgrade(yaml, dir.toFile(), () -> getClass().getResourceAsStream("/config.yml"), settings(), logger);
        assertEquals(newer, Files.readString(dir.resolve("config.yml")));
        assertFalse(Files.exists(dir.resolve("old")));
    }

    @Test
    void theConfigInsideTheJarIsTheLatestVersion() {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            ConfigNode cfg = yaml.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(ConfigUpgrade.VERSIONS.latest(), cfg.getInt(ConfigUpgrade.VERSIONS.key(), 0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
