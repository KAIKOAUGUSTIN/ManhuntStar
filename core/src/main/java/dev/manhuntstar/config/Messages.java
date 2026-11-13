package dev.manhuntstar.config;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.config.upgrade.LanguageUpgrade;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The plugin's texts (MiniMessage format), one file per language in the {@code lang/} folder. Underneath
 * everything are the English texts inside the plugin, so a key missing from a language (or from an
 * edited file) shows up in English instead of disappearing.
 */
public final class Messages {

    public static final String DEFAULT_LANGUAGE = "en";
    /** Languages that come inside the plugin (core/src/main/resources/lang). */
    public static final List<String> BUNDLED_LANGUAGES = List.of("en", "pt-br", "fr", "ru", "zh-cn");
    public static final String FOLDER = "lang";

    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, String> values = new HashMap<>();
    private String prefix = "";
    private String language = DEFAULT_LANGUAGE;

    /** "PT_BR" and "pt-br" are the same language. */
    public static String normalize(String language) {
        return language == null ? DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String resource(String language) {
        return FOLDER + "/" + language + ".yml";
    }

    /**
     * Copies the missing languages to {@code lang/}. A file from an older version (the default texts
     * changed, {@code version} field) is updated keeping the owner's texts ({@link #update}); a copy of it
     * goes to {@code lang/old/<language>-v<version>.yml}.
     */
    public static void install(YamlFiles yaml, File langDir, Function<String, InputStream> resources, Logger logger) {
        if (!langDir.isDirectory() && !langDir.mkdirs()) {
            logger.warning("Could not create the " + FOLDER + " folder.");
            return;
        }
        for (String lang : BUNDLED_LANGUAGES) {
            File file = new File(langDir, lang + ".yml");
            ConfigNode bundled = bundled(yaml, resources, lang, logger);
            if (bundled == null) {
                continue;
            }
            try {
                if (file.exists()) {
                    ConfigNode own = yaml.load(file);
                    int current = LanguageUpgrade.VERSIONS.versionOf(own);
                    if (current < bundled.getInt(LanguageUpgrade.VERSIONS.key(), 1)) {
                        update(yaml, file, own, current, resources, lang, logger);
                    }
                    continue;
                }
                copyBundled(resources, lang, file);
            } catch (IOException e) {
                logger.warning("Could not write " + FOLDER + "/" + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Brings an older language file up to date: the steps rename or remove keys, the file inside the jar
     * is written (new texts, with its comments) and the owner's texts go over it, except those whose new
     * default replaces them ({@link LanguageUpgrade#resetSince}) and keys that no longer exist.
     */
    private static void update(YamlFiles yaml, File file, ConfigNode own, int current, Function<String, InputStream> resources,
            String lang, Logger logger) throws IOException {
        LanguageUpgrade.VERSIONS.upgrade(own);
        File old = new File(file.getParentFile(), "old");
        Files.createDirectories(old.toPath());
        File backup = new File(old, lang + "-v" + current + ".yml");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File temporary = Files.createTempFile(file.getParentFile().toPath(), lang + "-", ".tmp").toFile();
        try {
            copyBundled(resources, lang, temporary);
            ConfigNode merged = yaml.load(temporary);
            Set<String> reset = LanguageUpgrade.resetSince(current);
            for (String key : own.keys(true)) {
                Object text = own.get(key);
                boolean known = merged.contains(key) && !(merged.get(key) instanceof ConfigNode);
                if (known && !(text instanceof ConfigNode) && !key.equals(LanguageUpgrade.VERSIONS.key()) && !reset.contains(key)) {
                    merged.set(key, text);
                }
            }
            yaml.save(merged, temporary);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
        logger.info(FOLDER + "/" + file.getName() + " updated to version " + LanguageUpgrade.VERSIONS.latest()
                + ", keeping your texts; the previous one is in " + FOLDER + "/old/" + backup.getName() + ".");
    }

    private static void copyBundled(Function<String, InputStream> resources, String lang, File file) throws IOException {
        try (InputStream in = resources.apply(resource(lang))) {
            if (in == null) {
                throw new IOException("Missing bundled language " + lang);
            }
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Available languages: the ones inside the plugin and the .yml files in the lang/ folder. */
    public static List<String> available(File langDir) {
        TreeSet<String> out = new TreeSet<>(BUNDLED_LANGUAGES);
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                out.add(normalize(f.getName().substring(0, f.getName().length() - 4)));
            }
        }
        return new ArrayList<>(out);
    }

    private static ConfigNode bundled(YamlFiles yaml, Function<String, InputStream> resources, String lang, Logger logger) {
        try (InputStream in = resources.apply(resource(lang))) {
            if (in == null) {
                return null;
            }
            return yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warning("Could not read the bundled language " + lang + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads a language: the English inside the plugin, on top of it the language inside the plugin and on
     * top of that the server's {@code lang/<language>.yml}. A language that doesn't exist becomes English, with a warning.
     */
    public void load(YamlFiles yaml, File langDir, String requested, Function<String, InputStream> resources, Logger logger) {
        String lang = normalize(requested);
        File file = new File(langDir, lang + ".yml");
        ConfigNode own = bundled(yaml, resources, lang, logger);
        if (own == null && !file.exists()) {
            logger.warning("Language '" + lang + "' not found in " + FOLDER + "/; using " + DEFAULT_LANGUAGE + ".");
            lang = DEFAULT_LANGUAGE;
            file = new File(langDir, lang + ".yml");
            own = null;
        }
        values.clear();
        put(bundled(yaml, resources, DEFAULT_LANGUAGE, logger));
        if (!lang.equals(DEFAULT_LANGUAGE)) {
            put(own);
        }
        if (file.exists()) {
            put(yaml.load(file));
        }
        language = lang;
        prefix = values.getOrDefault("prefix", "");
    }

    private void put(ConfigNode cfg) {
        if (cfg == null) {
            return;
        }
        for (String key : cfg.keys(true)) {
            if (cfg.isString(key)) {
                values.put(key, cfg.getString(key));
            }
        }
    }

    /** The language loaded now. */
    public String language() {
        return language;
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), TagResolver.resolver(resolvers));
    }

    /** Multi-line text (item lore, help): one Component per line. */
    public List<Component> lines(String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(resolvers);
        List<Component> out = new ArrayList<>();
        for (String line : raw(key).split("\n")) {
            out.add(mini.deserialize(line, all));
        }
        return out;
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return mini.deserialize(prefix + raw(key), TagResolver.resolver(resolvers));
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(prefixed(key, resolvers));
    }

    public String yesNo(boolean value) {
        return raw(value ? "common.yes" : "common.no");
    }

    public String onOff(boolean value) {
        return raw(value ? "common.on" : "common.off");
    }

    public String difficulty(BotDifficulty d) {
        return raw("difficulty." + d.configName());
    }

    /** Name of an option in the menu ({@code options.<key>.name}). */
    public String optionName(Setting<?> s) {
        return raw("options." + s.key() + ".name");
    }

    /** Error for an option's value, already with the placeholders from {@link Setting.InvalidValue}. */
    public Component invalidValue(Setting.InvalidValue e) {
        Object[] args = e.placeholders();
        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            resolvers.add(p(String.valueOf(args[i]), args[i + 1]));
        }
        return get(e.getMessage(), resolvers.toArray(TagResolver[]::new));
    }

    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
