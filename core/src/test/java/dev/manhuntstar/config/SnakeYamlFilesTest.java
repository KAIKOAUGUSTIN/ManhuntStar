package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The SnakeYAML implementation, used by the platforms without Bukkit's YamlConfiguration. */
class SnakeYamlFilesTest extends YamlFilesContract {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    @Override
    protected YamlFiles yaml() {
        return yaml;
    }

    /** The bundled config.yml survives a save: every comment line and every value. */
    @Test
    void bundledConfigRoundTrip() throws Exception {
        String original;
        try (InputStream in = SnakeYamlFilesTest.class.getResourceAsStream("/config.yml")) {
            original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ConfigNode cfg = yaml.load(new StringReader(original));
        String saved = yaml.saveToString(cfg);
        for (String line : original.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                assertTrue(saved.contains(t), "comment lost: " + t);
            }
        }
        ConfigNode again = yaml.load(new StringReader(saved));
        assertEquals(cfg.keys(true), again.keys(true));
        for (String key : cfg.keys(true)) {
            if (!cfg.isSection(key)) {
                assertEquals(cfg.get(key), again.get(key), key);
            }
        }
    }

    @Test
    void bundledLanguagesLoad() throws Exception {
        for (String lang : Messages.BUNDLED_LANGUAGES) {
            try (InputStream in = SnakeYamlFilesTest.class.getResourceAsStream("/lang/" + lang + ".yml")) {
                ConfigNode cfg = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                assertTrue(cfg.isString("prefix"), lang);
            }
        }
    }
}
