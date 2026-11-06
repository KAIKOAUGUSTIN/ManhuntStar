package dev.manhuntstar.platform;

import java.util.List;
import java.util.Set;

/**
 * A YAML document or a section of one (config.yml, language files, bots.yml). Paths use dots between
 * sections ({@code "manhunt.ring-point.x"}). Each platform reads and writes it in its own way: Bukkit's
 * YamlConfiguration on Paper.
 */
public interface ConfigNode {

    /** The value at the path (a nested section comes back as a {@link ConfigNode}), or null. */
    Object get(String path);

    boolean contains(String path);

    boolean isString(String path);

    boolean isList(String path);

    boolean isSection(String path);

    /** The value at the path as text, or null when there's nothing there. */
    String getString(String path);

    int getInt(String path, int def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    /** The list at the path with each text, number or true/false as text; empty when there's no list. */
    List<String> getStringList(String path);

    /** The section at the path, or null. */
    ConfigNode section(String path);

    /** Creates (or replaces) an empty section at the path. */
    ConfigNode createSection(String path);

    /** The keys of this section; with {@code deep}, also the keys inside nested sections, as full paths. */
    Set<String> keys(boolean deep);

    /**
     * Writes a value (text, number, true/false or a list of them) and creates the sections on the way;
     * null removes the path. Overwriting a key keeps its comments.
     */
    void set(String path, Object value);
}
