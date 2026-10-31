package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Os idiomas do plugin têm as mesmas chaves e os mesmos placeholders do inglês. */
class LanguageFilesTest {

    private static final Pattern TAG = Pattern.compile("<([a-z0-9_-]+)>");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z-]+)\"");
    private static final Set<String> STYLE = Set.of("bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st",
            "obfuscated", "obf", "reset", "newline", "br");

    private static YamlConfiguration load(String lang) throws Exception {
        try (InputStream in = LanguageFilesTest.class.getResourceAsStream("/lang/" + lang + ".yml")) {
            assertNotNull(in, "idioma não encontrado: " + lang);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Set<String> keys(YamlConfiguration cfg) {
        Set<String> out = new TreeSet<>();
        for (String key : cfg.getKeys(true)) {
            if (!cfg.isConfigurationSection(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** Placeholders de um texto: as tags que não são cor nem estilo. */
    private static Set<String> placeholders(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            String tag = m.group(1);
            if (!STYLE.contains(tag) && NamedTextColor.NAMES.value(tag) == null) {
                out.add(tag);
            }
        }
        return out;
    }

    @Test
    void everyLanguageMatchesEnglish() throws Exception {
        YamlConfiguration en = load("en");
        Set<String> enKeys = keys(en);
        for (String lang : Messages.BUNDLED_LANGUAGES) {
            YamlConfiguration other = load(lang);
            assertEquals(enKeys, keys(other), "chaves diferentes do inglês em " + lang);
            for (String key : enKeys) {
                if (en.isString(key)) {
                    assertTrue(other.isString(key), lang + ": " + key + " não é texto");
                    assertEquals(placeholders(en.getString(key)), placeholders(other.getString(key)), lang + ": placeholders de " + key);
                }
            }
        }
    }

    @Test
    void everyOptionCategoryAndDifficultyHasText() throws Exception {
        YamlConfiguration en = load("en");
        Settings settings = new Settings();
        for (Setting<?> s : settings.all()) {
            assertTrue(en.isString("options." + s.key() + ".name"), "sem nome: " + s.key());
            assertTrue(en.isString("options." + s.key() + ".description"), "sem descrição: " + s.key());
        }
        for (Setting.Category c : Setting.Category.values()) {
            assertTrue(en.isString("category." + c.name().toLowerCase(Locale.ROOT)), "sem nome: " + c);
        }
        for (BotDifficulty d : BotDifficulty.values()) {
            assertTrue(en.isString("difficulty." + d.configName()), "sem nome: " + d);
        }
    }

    /** Cada estado que o Brain usa ({@code state("...")}) tem texto. */
    @Test
    void everyBrainStateHasText() throws Exception {
        YamlConfiguration en = load("en");
        Set<String> used = new TreeSet<>();
        try (Stream<String> lines = Files.lines(Path.of("src/main/java/dev/manhuntstar/bot/ai/Brain.java"))) {
            lines.filter(line -> line.contains("state(") && !line.contains("void state(")).forEach(line -> {
                Matcher m = LITERAL.matcher(line);
                while (m.find()) {
                    used.add(m.group(1));
                }
            });
        }
        assertTrue(used.size() > 20, "estados não encontrados no Brain.java");
        for (String state : used) {
            assertTrue(en.isString("state." + state), "sem texto: state." + state);
        }
    }
}
