package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.platform.ConfigNode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

/** The plugin's languages have the same keys and the same placeholders as English. */
class LanguageFilesTest {

    /** The Brain source, which stays in the paper module until the AI moves to core. */
    private static final String BRAIN = "src/main/java/dev/manhuntstar/bot/ai/Brain.java";
    private static final Pattern TAG = Pattern.compile("<([a-z0-9_-]+)>");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z-]+)\"");
    private static final Set<String> STYLE = Set.of("bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st",
            "obfuscated", "obf", "reset", "newline", "br");

    private static ConfigNode load(String lang) throws Exception {
        try (InputStream in = LanguageFilesTest.class.getResourceAsStream("/lang/" + lang + ".yml")) {
            assertNotNull(in, "language not found: " + lang);
            return new SnakeYamlFiles(Logger.getAnonymousLogger()).load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Set<String> keys(ConfigNode cfg) {
        Set<String> out = new TreeSet<>();
        for (String key : cfg.keys(true)) {
            if (!cfg.isSection(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** A text's placeholders: the tags that are neither color nor style. */
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
        ConfigNode en = load("en");
        Set<String> enKeys = keys(en);
        for (String lang : Messages.BUNDLED_LANGUAGES) {
            ConfigNode other = load(lang);
            assertEquals(enKeys, keys(other), "keys differ from English in " + lang);
            for (String key : enKeys) {
                if (en.isString(key)) {
                    assertTrue(other.isString(key), lang + ": " + key + " is not text");
                    assertEquals(placeholders(en.getString(key)), placeholders(other.getString(key)), lang + ": placeholders de " + key);
                }
            }
        }
    }

    @Test
    void everyOptionCategoryAndDifficultyHasText() throws Exception {
        ConfigNode en = load("en");
        Settings settings = new Settings();
        for (Setting<?> s : settings.all()) {
            assertTrue(en.isString("options." + s.key() + ".name"), "no name: " + s.key());
            assertTrue(en.isString("options." + s.key() + ".description"), "no description: " + s.key());
        }
        for (Setting.Category c : Setting.Category.values()) {
            assertTrue(en.isString("category." + c.name().toLowerCase(Locale.ROOT)), "no name: " + c);
        }
        for (BotDifficulty d : BotDifficulty.values()) {
            assertTrue(en.isString("difficulty." + d.configName()), "no name: " + d);
        }
    }

    /** Every state the Brain uses ({@code state("...")}) has a text. */
    @Test
    void everyBrainStateHasText() throws Exception {
        ConfigNode en = load("en");
        Set<String> used = new TreeSet<>();
        try (Stream<String> lines = Files.lines(Path.of(BRAIN))) {
            lines.filter(line -> line.contains("state(") && !line.contains("void state(")).forEach(line -> {
                Matcher m = LITERAL.matcher(line);
                while (m.find()) {
                    used.add(m.group(1));
                }
            });
        }
        assertTrue(used.size() > 20, "no states found in Brain.java");
        for (String state : used) {
            assertTrue(en.isString("state." + state), "no text: state." + state);
        }
    }
}
