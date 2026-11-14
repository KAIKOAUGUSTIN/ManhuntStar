package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Portuguese config.yml of 1.5 that couldn't be migrated yet: English keys written since then win. */
class LegacySettingsTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    @TempDir
    Path dir;

    private Settings load(String text) throws IOException {
        File file = dir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        assertTrue(Settings.isLegacy(yaml, file));
        Settings settings = new Settings();
        settings.setLanguages(() -> List.of("en", "pt-br", "fr"));
        settings.loadLegacy(yaml, file, Logger.getAnonymousLogger());
        return settings;
    }

    @Test
    void onlyPortugueseKeysAreRead() throws IOException {
        Settings s = load("geral:\n  tab-list: false\nbots:\n  vida-maxima: 35\n  respawn: nao\n");
        assertEquals(35.0, s.botHealth.value());
        assertFalse(s.tabList.on());
        assertFalse(s.respawn.on(), "bots.respawn is the same key in both, with a Portuguese value");
        assertEquals("pt-br", s.language.get(), "1.5 only had Portuguese");
    }

    @Test
    void aClearedRingPointStaysCleared() throws IOException {
        Settings s = load("geral:\n  tab-list: true\nmanhunt:\n  ponto-roda:\n    mundo: world\n    x: 10\n    y: 64\n    z: -5\n");
        assertEquals("world", s.ringPoint().world());
        s.setRingPoint(null);
        File file = dir.resolve("config.yml").toFile();
        s.save(yaml, file, Logger.getAnonymousLogger());
        Settings reloaded = new Settings();
        reloaded.setLanguages(() -> List.of("en", "pt-br"));
        reloaded.loadLegacy(yaml, file, Logger.getAnonymousLogger());
        assertNull(reloaded.ringPoint(), "the old manhunt.ponto-roda doesn't come back");
    }

    @Test
    void aConfigWithAYamlErrorIsNotOverwrittenByASave() throws IOException {
        File file = dir.resolve("config.yml").toFile();
        String broken = "general:\n  language: en\nbots: !!manhuntstar.Unknown {x: 1}\n";
        Files.writeString(file.toPath(), broken, StandardCharsets.UTF_8);
        Settings s = new Settings();
        s.setLanguages(() -> List.of("en"));
        assertThrows(IOException.class, () -> s.saveOrThrow(yaml, file));
        s.save(yaml, file, Logger.getAnonymousLogger());
        assertEquals(broken, Files.readString(file.toPath(), StandardCharsets.UTF_8), "a change in game doesn't replace it");
    }

    @Test
    void englishKeysSavedSinceThenWin() throws IOException {
        Settings s = load("""
                geral:
                  tab-list: false
                general:
                  language: fr
                  tab-list: true
                bots:
                  vida-maxima: 35
                  max-health: 50
                faststats:
                  statistics: false
                  error-reports: false
                """);
        assertEquals(50.0, s.botHealth.value());
        assertTrue(s.tabList.on());
        assertEquals("fr", s.language.get());
        assertFalse(s.statistics.on(), "the FastStats options only have English keys");
        assertFalse(s.errorReports.on());
    }
}
