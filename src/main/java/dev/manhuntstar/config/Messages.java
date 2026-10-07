package dev.manhuntstar.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

/** Mensagens do messages.yml (formato MiniMessage), com os textos padrão do jar como reserva. */
public final class Messages {

    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, String> values = new HashMap<>();
    private String prefix = "";

    public void load(File file, InputStream defaults, Logger logger) {
        values.clear();
        if (defaults != null) {
            try (InputStreamReader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                for (String key : def.getKeys(true)) {
                    if (def.isString(key)) {
                        values.put(key, def.getString(key));
                    }
                }
            } catch (Exception e) {
                logger.warning("Falha ao ler as mensagens padrão: " + e.getMessage());
            }
        }
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            for (String key : cfg.getKeys(true)) {
                if (cfg.isString(key)) {
                    values.put(key, cfg.getString(key));
                }
            }
        }
        prefix = values.getOrDefault("prefixo", "");
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), TagResolver.resolver(resolvers));
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return mini.deserialize(prefix + raw(key), TagResolver.resolver(resolvers));
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(prefixed(key, resolvers));
    }

    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
