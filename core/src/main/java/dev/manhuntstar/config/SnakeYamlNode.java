package dev.manhuntstar.config;

import dev.manhuntstar.platform.ConfigNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.comments.CommentLine;

/**
 * A section of a document read by {@link SnakeYamlFiles}: the keys in file order, each with its value
 * (a nested section is another {@code SnakeYamlNode}) and its comments. Reads and writes behave like
 * Bukkit's MemorySection, so the code that uses {@link ConfigNode} sees the same thing on every platform.
 */
final class SnakeYamlNode implements ConfigNode {

    static final class Entry {
        Object value;
        List<CommentLine> block;
        List<CommentLine> keyInline;
        List<CommentLine> valueInline;

        Entry(Object value) {
            this.value = value;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    List<CommentLine> endComments;

    Map<String, Entry> entries() {
        return entries;
    }

    /** Sets a key of this section (not a path), keeping the comments the key already had. */
    Entry put(String key, Object value) {
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(value);
            entries.put(key, entry);
        } else {
            entry.value = value;
        }
        return entry;
    }

    /** The section that holds the last part of the path; null when a part on the way isn't a section. */
    private SnakeYamlNode parent(String path, boolean create) {
        SnakeYamlNode section = this;
        int start = 0;
        int dot;
        while ((dot = path.indexOf('.', start)) >= 0) {
            String key = path.substring(start, dot);
            Entry entry = section.entries.get(key);
            if (entry != null && entry.value instanceof SnakeYamlNode child) {
                section = child;
            } else if (create) {
                SnakeYamlNode child = new SnakeYamlNode();
                section.put(key, child);
                section = child;
            } else {
                return null;
            }
            start = dot + 1;
        }
        return section;
    }

    private static String last(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    @Override
    public Object get(String path) {
        if (path.isEmpty()) {
            return this;
        }
        SnakeYamlNode section = parent(path, false);
        if (section == null) {
            return null;
        }
        Entry entry = section.entries.get(last(path));
        return entry == null ? null : entry.value;
    }

    @Override
    public boolean contains(String path) {
        return get(path) != null;
    }

    @Override
    public boolean isString(String path) {
        return get(path) instanceof String;
    }

    @Override
    public boolean isList(String path) {
        return get(path) instanceof List;
    }

    @Override
    public boolean isSection(String path) {
        return get(path) instanceof SnakeYamlNode;
    }

    @Override
    public String getString(String path) {
        Object value = get(path);
        return value == null ? null : value.toString();
    }

    @Override
    public int getInt(String path, int def) {
        return get(path) instanceof Number n ? n.intValue() : def;
    }

    @Override
    public long getLong(String path, long def) {
        return get(path) instanceof Number n ? n.longValue() : def;
    }

    @Override
    public double getDouble(String path, double def) {
        return get(path) instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return get(path) instanceof Boolean b ? b : def;
    }

    @Override
    public List<String> getStringList(String path) {
        if (!(get(path) instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Character) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @Override
    public ConfigNode section(String path) {
        return get(path) instanceof SnakeYamlNode s ? s : null;
    }

    @Override
    public ConfigNode createSection(String path) {
        SnakeYamlNode section = new SnakeYamlNode();
        set(path, section);
        return section;
    }

    @Override
    public Set<String> keys(boolean deep) {
        Set<String> out = new LinkedHashSet<>();
        collect(out, "", deep);
        return Collections.unmodifiableSet(out);
    }

    private void collect(Set<String> out, String prefix, boolean deep) {
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            String key = prefix + e.getKey();
            out.add(key);
            if (deep && e.getValue().value instanceof SnakeYamlNode child) {
                child.collect(out, key + ".", true);
            }
        }
    }

    @Override
    public void set(String path, Object value) {
        if (value == null) {
            SnakeYamlNode section = parent(path, false);
            if (section != null) {
                section.entries.remove(last(path));
            }
            return;
        }
        parent(path, true).put(last(path), convert(value));
    }

    /** Maps become sections, like in Bukkit; everything else is kept as it is. */
    private static Object convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            SnakeYamlNode section = new SnakeYamlNode();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                section.put(String.valueOf(e.getKey()), convert(e.getValue()));
            }
            return section;
        }
        return value;
    }

    @Override
    public String toString() {
        return "SnakeYamlNode" + entries.keySet();
    }
}
