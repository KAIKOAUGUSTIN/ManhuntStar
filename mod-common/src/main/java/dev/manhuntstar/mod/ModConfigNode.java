package dev.manhuntstar.mod;

import dev.manhuntstar.platform.ConfigNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A YAML document or a section of one, kept the way Bukkit's {@code MemorySection} keeps it: the keys in
 * file order, each with its value and the comments above it and on its line. Paths use dots. Reading
 * and writing values follows Bukkit's rules exactly, so config.yml means the same on Paper and in the mods.
 */
public class ModConfigNode implements ConfigNode {

    /** A key's value and comments (Bukkit's {@code SectionPathData}); null in a comment list is a blank line. */
    static final class Entry {
        Object data;
        List<String> comments = Collections.emptyList();
        List<String> inlineComments = Collections.emptyList();

        Entry(Object data) {
            this.data = data;
        }
    }

    final Map<String, Entry> map = new LinkedHashMap<>();
    /** The comments at the top and the bottom of a document (Bukkit's header and footer). */
    List<String> header = Collections.emptyList();
    List<String> footer = Collections.emptyList();
    private final ModConfigNode parent;
    private final String name;

    /** A new document. */
    ModConfigNode() {
        this.parent = null;
        this.name = "";
    }

    private ModConfigNode(ModConfigNode parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Override
    public Object get(String path) {
        if (path.isEmpty()) {
            return this;
        }
        int dot = path.lastIndexOf('.');
        ModConfigNode section = this;
        if (dot >= 0) {
            for (String node : path.substring(0, dot).split("\\.", -1)) {
                Entry e = section.map.get(node);
                if (e == null || !(e.data instanceof ModConfigNode next)) {
                    return null;
                }
                section = next;
            }
        }
        Entry e = section.map.get(path.substring(dot + 1));
        return e == null ? null : e.data;
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
        return get(path) instanceof ModConfigNode;
    }

    @Override
    public String getString(String path) {
        Object v = get(path);
        return v != null ? v.toString() : null;
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
            return new ArrayList<>(0);
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String || o instanceof Integer || o instanceof Boolean || o instanceof Character || o instanceof Byte
                    || o instanceof Short || o instanceof Double || o instanceof Long || o instanceof Float) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @Override
    public ConfigNode section(String path) {
        return get(path) instanceof ModConfigNode s ? s : null;
    }

    @Override
    public ModConfigNode createSection(String path) {
        int dot = path.indexOf('.');
        if (dot >= 0) {
            String node = path.substring(0, dot);
            ModConfigNode sub = map.get(node) != null && map.get(node).data instanceof ModConfigNode s ? s : createSection(node);
            return sub.createSection(path.substring(dot + 1));
        }
        ModConfigNode result = new ModConfigNode(this, path);
        map.put(path, new Entry(result));
        return result;
    }

    @Override
    public Set<String> keys(boolean deep) {
        Set<String> out = new LinkedHashSet<>();
        addKeys(out, "", deep);
        return out;
    }

    private void addKeys(Set<String> out, String prefix, boolean deep) {
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            out.add(prefix + e.getKey());
            if (deep && e.getValue().data instanceof ModConfigNode sub) {
                sub.addKeys(out, prefix + e.getKey() + ".", true);
            }
        }
    }

    @Override
    public void set(String path, Object value) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Cannot set to an empty path");
        }
        int dot = path.indexOf('.');
        if (dot >= 0) {
            String node = path.substring(0, dot);
            Entry e = map.get(node);
            ModConfigNode sub;
            if (e != null && e.data instanceof ModConfigNode s) {
                sub = s;
            } else if (value == null) {
                return;
            } else {
                sub = createSection(node);
            }
            sub.set(path.substring(dot + 1), value);
            return;
        }
        if (value == null) {
            map.remove(path);
            return;
        }
        Entry e = map.get(path);
        if (e == null) {
            map.put(path, new Entry(value));
        } else {
            e.data = value;
        }
    }

    /** The values of this section, in order (sections stay sections). */
    Map<String, Object> values() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            out.put(e.getKey(), e.getValue().data);
        }
        return out;
    }

    /** The full path of this section in its document. */
    String path() {
        if (parent == null) {
            return "";
        }
        String up = parent.path();
        return up.isEmpty() ? name : up + "." + name;
    }

    /** The document this section belongs to. */
    ModConfigNode root() {
        ModConfigNode n = this;
        while (n.parent != null) {
            n = n.parent;
        }
        return n;
    }

    @Override
    public String toString() {
        return "ModConfigNode[path='" + path() + "']";
    }
}
