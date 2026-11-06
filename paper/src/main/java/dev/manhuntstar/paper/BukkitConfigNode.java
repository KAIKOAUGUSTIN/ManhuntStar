package dev.manhuntstar.paper;

import dev.manhuntstar.platform.ConfigNode;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/** A {@link ConfigNode} over a Bukkit {@link ConfigurationSection}: every call is the Bukkit one. */
public final class BukkitConfigNode implements ConfigNode {

    private final ConfigurationSection section;

    public BukkitConfigNode(ConfigurationSection section) {
        this.section = section;
    }

    public ConfigurationSection bukkit() {
        return section;
    }

    private static ConfigNode wrap(ConfigurationSection s) {
        return s == null ? null : new BukkitConfigNode(s);
    }

    @Override
    public Object get(String path) {
        Object value = section.get(path);
        return value instanceof ConfigurationSection s ? new BukkitConfigNode(s) : value;
    }

    @Override
    public boolean contains(String path) {
        return section.contains(path);
    }

    @Override
    public boolean isString(String path) {
        return section.isString(path);
    }

    @Override
    public boolean isList(String path) {
        return section.isList(path);
    }

    @Override
    public boolean isSection(String path) {
        return section.isConfigurationSection(path);
    }

    @Override
    public String getString(String path) {
        return section.getString(path);
    }

    @Override
    public int getInt(String path, int def) {
        return section.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def) {
        return section.getLong(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return section.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return section.getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return section.getStringList(path);
    }

    @Override
    public ConfigNode section(String path) {
        return wrap(section.getConfigurationSection(path));
    }

    @Override
    public ConfigNode createSection(String path) {
        return wrap(section.createSection(path));
    }

    @Override
    public Set<String> keys(boolean deep) {
        return section.getKeys(deep);
    }

    @Override
    public void set(String path, Object value) {
        section.set(path, value instanceof BukkitConfigNode node ? node.section : value);
    }
}
