package dev.manhuntstar.config.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.FailingSaveYamlFiles;
import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An older lang/ file is updated: new texts added, the owner's texts kept, old keys gone. */
class LanguageUpgradeTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    @TempDir
    Path dir;

    private InputStream resource(String path) {
        return getClass().getResourceAsStream("/" + path);
    }

    @Test
    void failedSaveLeavesActiveLanguageAndBackupIntact() throws IOException {
        Path active = dir.resolve("en.yml");
        String original = "version: 1\nprefix: mine\n";
        Files.writeString(active, original);
        YamlFiles failing = new FailingSaveYamlFiles(yaml, temporary -> {
            assertFalse(temporary.toPath().equals(active));
            assertEquals("mine", yaml.load(active.toFile()).getString("prefix"));
        });
        Messages.install(failing, dir.toFile(), this::resource, Logger.getAnonymousLogger());
        assertEquals(original, Files.readString(active));
        assertEquals(original, Files.readString(dir.resolve("old/en-v1.yml")));
        try (var files = Files.list(dir)) {
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".tmp")));
        }
    }

    @Test
    void anOlderLanguageKeepsTheOwnersTexts() throws IOException {
        Path lang = dir.resolve("lang");
        Files.createDirectories(lang);
        Files.writeString(lang.resolve("en.yml"), """
                version: 1
                prefix: "<red>MyServer</red> "
                no-longer-used: "old text"
                options:
                  updatecheck:
                    name: "My update check"
                """, StandardCharsets.UTF_8);

        Messages.install(yaml, lang.toFile(), this::resource, Logger.getAnonymousLogger());

        ConfigNode en = yaml.load(lang.resolve("en.yml").toFile());
        assertEquals(LanguageUpgrade.VERSIONS.latest(), en.getInt("version", 0));
        assertEquals("<red>MyServer</red> ", en.getString("prefix"));
        assertEquals("My update check", en.getString("options.updatecheck.name"));
        assertNotNull(en.getString("options.statistics.name"), "new texts are added");
        assertFalse(en.contains("no-longer-used"));
        assertTrue(Files.readString(lang.resolve("en.yml")).contains("MiniMessage format"), "the comments come back");
        assertTrue(Files.isRegularFile(lang.resolve("old/en-v1.yml")));
        assertTrue(Files.isRegularFile(lang.resolve("pt-br.yml")), "missing languages are copied");
    }

    @Test
    void theLatestVersionIsLeftAlone() throws IOException {
        Path lang = dir.resolve("lang");
        Files.createDirectories(lang);
        String mine = "version: " + LanguageUpgrade.VERSIONS.latest() + "\nprefix: \"mine\"\n";
        Files.writeString(lang.resolve("en.yml"), mine);
        Messages.install(yaml, lang.toFile(), this::resource, Logger.getAnonymousLogger());
        assertEquals(mine, Files.readString(lang.resolve("en.yml")));
        assertFalse(Files.exists(lang.resolve("old")));
    }

    @Test
    void everyLanguageInsideTheJarIsTheLatestVersion() {
        for (String l : List.of("en", "pt-br", "fr", "ru", "zh-cn")) {
            try (InputStream in = resource("lang/" + l + ".yml")) {
                ConfigNode node = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                assertEquals(LanguageUpgrade.VERSIONS.latest(), node.getInt("version", 0), l);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
