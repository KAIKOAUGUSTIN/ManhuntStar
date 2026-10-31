package dev.manhuntstar.config;

import dev.manhuntstar.bot.BotDifficulty;
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
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Textos do plugin (formato MiniMessage), um arquivo por idioma na pasta {@code lang/}. Por baixo de
 * tudo ficam os textos em inglês de dentro do plugin, então uma chave que falte num idioma (ou num
 * arquivo editado) aparece em inglês em vez de sumir.
 */
public final class Messages {

    public static final String DEFAULT_LANGUAGE = "en";
    /** Idiomas que vêm dentro do plugin (src/main/resources/lang). */
    public static final List<String> BUNDLED_LANGUAGES = List.of("en", "pt-br", "fr", "ru", "zh-cn");
    public static final String FOLDER = "lang";

    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, String> values = new HashMap<>();
    private String prefix = "";
    private String language = DEFAULT_LANGUAGE;

    /** "PT_BR" e "pt-br" são o mesmo idioma. */
    public static String normalize(String language) {
        return language == null ? DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String resource(String language) {
        return FOLDER + "/" + language + ".yml";
    }

    /**
     * Copia para {@code lang/} os idiomas que faltam. Um arquivo de uma versão anterior (os textos
     * padrão mudaram, campo {@code version}) vai para {@code lang/old/<idioma>-v<versão>.yml} e é
     * trocado pelo novo.
     */
    public static void install(File langDir, Function<String, InputStream> resources, Logger logger) {
        if (!langDir.isDirectory() && !langDir.mkdirs()) {
            logger.warning("Could not create the " + FOLDER + " folder.");
            return;
        }
        for (String lang : BUNDLED_LANGUAGES) {
            File file = new File(langDir, lang + ".yml");
            YamlConfiguration bundled = bundled(resources, lang, logger);
            if (bundled == null) {
                continue;
            }
            try {
                if (file.exists()) {
                    int current = YamlConfiguration.loadConfiguration(file).getInt("version", 1);
                    int latest = bundled.getInt("version", 1);
                    if (current >= latest) {
                        continue;
                    }
                    File old = new File(langDir, "old");
                    if (!old.isDirectory() && !old.mkdirs()) {
                        continue;
                    }
                    File backup = new File(old, lang + "-v" + current + ".yml");
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info(FOLDER + "/" + file.getName() + " updated to version " + latest + "; the previous one is in "
                            + FOLDER + "/old/" + backup.getName() + ".");
                }
                try (InputStream in = resources.apply(resource(lang))) {
                    if (in != null) {
                        Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException e) {
                logger.warning("Could not write " + FOLDER + "/" + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Idiomas disponíveis: os de dentro do plugin e os arquivos .yml da pasta lang/. */
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

    private static YamlConfiguration bundled(Function<String, InputStream> resources, String lang, Logger logger) {
        try (InputStream in = resources.apply(resource(lang))) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warning("Could not read the bundled language " + lang + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Carrega um idioma: o inglês de dentro do plugin, por cima o idioma de dentro do plugin e por
     * cima o {@code lang/<idioma>.yml} do servidor. Idioma que não existe vira inglês, com aviso.
     */
    public void load(File langDir, String requested, Function<String, InputStream> resources, Logger logger) {
        String lang = normalize(requested);
        File file = new File(langDir, lang + ".yml");
        YamlConfiguration own = bundled(resources, lang, logger);
        if (own == null && !file.exists()) {
            logger.warning("Language '" + lang + "' not found in " + FOLDER + "/; using " + DEFAULT_LANGUAGE + ".");
            lang = DEFAULT_LANGUAGE;
            file = new File(langDir, lang + ".yml");
            own = null;
        }
        values.clear();
        put(bundled(resources, DEFAULT_LANGUAGE, logger));
        if (!lang.equals(DEFAULT_LANGUAGE)) {
            put(own);
        }
        if (file.exists()) {
            put(YamlConfiguration.loadConfiguration(file));
        }
        language = lang;
        prefix = values.getOrDefault("prefix", "");
    }

    private void put(YamlConfiguration cfg) {
        if (cfg == null) {
            return;
        }
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                values.put(key, cfg.getString(key));
            }
        }
    }

    /** Idioma carregado agora. */
    public String language() {
        return language;
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), TagResolver.resolver(resolvers));
    }

    /** Texto de várias linhas (lore de item, ajuda): uma Component por linha. */
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

    /** Nome de uma opção no menu ({@code options.<chave>.name}). */
    public String optionName(Setting<?> s) {
        return raw("options." + s.key() + ".name");
    }

    /** Erro de valor de uma opção, já com os placeholders do {@link Setting.InvalidValue}. */
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
