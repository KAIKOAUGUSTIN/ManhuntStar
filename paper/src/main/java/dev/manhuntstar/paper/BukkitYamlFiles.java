package dev.manhuntstar.paper;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML files with Bukkit's {@link YamlConfiguration}, which keeps the comments when it saves. */
public final class BukkitYamlFiles implements YamlFiles {

    private static final Logger LOGGER = Logger.getLogger("ManhuntStar");

    @Override
    public ConfigNode load(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.options().parseComments(true);
        try {
            cfg.load(file);
        } catch (FileNotFoundException ignored) {
            // a missing file is an empty document
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.log(Level.SEVERE, "Cannot load " + file, e);
            // not the keys read before the error (YamlConfiguration.loadConfiguration keeps them): an empty
            // document is how a file that can't be read looks to the callers (FileVersions.unreadable),
            // and they then don't rewrite it
            return new BukkitConfigNode(new YamlConfiguration());
        }
        return new BukkitConfigNode(cfg);
    }

    @Override
    public ConfigNode load(Reader reader) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(reader);
        } catch (IOException | InvalidConfigurationException e) {
            LOGGER.log(Level.SEVERE, "Cannot load configuration from stream", e);
            return new BukkitConfigNode(new YamlConfiguration());
        }
        return new BukkitConfigNode(cfg);
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
