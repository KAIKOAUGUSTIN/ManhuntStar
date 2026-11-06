package dev.manhuntstar.paper;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML files with Bukkit's {@link YamlConfiguration}, which keeps the comments when it saves. */
public final class BukkitYamlFiles implements YamlFiles {

    @Override
    public ConfigNode load(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.options().parseComments(true);
        return new BukkitConfigNode(cfg);
    }

    @Override
    public ConfigNode load(Reader reader) {
        return new BukkitConfigNode(YamlConfiguration.loadConfiguration(reader));
    }

    @Override
    public ConfigNode create() {
        return new BukkitConfigNode(new YamlConfiguration());
    }

    @Override
    public void save(ConfigNode node, File file) throws IOException {
        root(node).save(file);
    }

    @Override
    public String saveToString(ConfigNode node) {
        return root(node).saveToString();
    }

    private static YamlConfiguration root(ConfigNode node) {
        ConfigurationSection section = ((BukkitConfigNode) node).bukkit();
        if (section.getRoot() instanceof YamlConfiguration yaml) {
            return yaml;
        }
        throw new IllegalArgumentException("not a YAML document: " + section.getCurrentPath());
    }
}
