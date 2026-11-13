package dev.manhuntstar.config.upgrade;

import dev.manhuntstar.platform.ConfigNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The versions of one of the files the server owner can edit (config.yml, the languages, bots.yml): the
 * key that holds the version, and the steps that bring a file from each version to the next one.
 * {@code steps.get(i)} goes from version {@code first + i} to {@code first + i + 1}, so adding a version
 * is adding a step at the end (and raising the version in the file inside the jar, when there is one).
 *
 * <p>The steps change the values the owner has (rename, remove, change a value); new options and texts
 * come from the file inside the jar, with its comments, when the file is written again.
 */
public final class FileVersions {

    private final String key;
    private final int first;
    private final List<Consumer<ConfigNode>> steps;

    /**
     * @param key the version's key in the file
     * @param first the version of a file without the key (the one before the key existed)
     * @param steps from each version to the next, oldest first
     */
    public FileVersions(String key, int first, List<Consumer<ConfigNode>> steps) {
        this.key = key;
        this.first = first;
        this.steps = List.copyOf(steps);
    }

    public String key() {
        return key;
    }

    /** The version this ManhuntStar writes. */
    public int latest() {
        return first + steps.size();
    }

    /** The file's version: its key, or {@code first} without one. */
    public int versionOf(ConfigNode node) {
        return node.getInt(key, first);
    }

    /**
     * Applies the steps from the node's version to {@link #latest} and writes the new version in it. A
     * node of the latest version, or of a newer one (a newer ManhuntStar wrote it), isn't changed.
     *
     * @return the version the node had
     */
    public int upgrade(ConfigNode node) {
        int from = versionOf(node);
        if (from >= latest()) {
            return from;
        }
        for (int v = Math.max(from, first); v < latest(); v++) {
            steps.get(v - first).accept(node);
        }
        node.set(key, latest());
        return from;
    }

    // ------------------------------------------------------------------ steps

    /** A version that only brings new options or texts (they come from the file inside the jar). */
    public static Consumer<ConfigNode> nothing() {
        return node -> {
        };
    }

    /** Moves a value, or a whole section, to a new path (kept where the new path already has one). */
    public static Consumer<ConfigNode> rename(String from, String to) {
        return node -> {
            Object value = node.get(from);
            if (value == null) {
                return;
            }
            if (value instanceof ConfigNode section) {
                for (String sub : section.keys(true)) {
                    Object v = section.get(sub);
                    if (!(v instanceof ConfigNode) && !node.contains(to + "." + sub)) {
                        node.set(to + "." + sub, v);
                    }
                }
            } else if (!node.contains(to)) {
                node.set(to, value);
            }
            node.set(from, null);
        };
    }

    /** Removes a value or a section that no longer exists. */
    public static Consumer<ConfigNode> remove(String path) {
        return node -> node.set(path, null);
    }

    /** Replaces values that no longer exist with today's ones ({@code "both"} becomes {@code "all"}). */
    public static Consumer<ConfigNode> mapValues(String path, Map<String, String> values) {
        return node -> {
            String current = node.getString(path);
            if (current != null && values.containsKey(current)) {
                node.set(path, values.get(current));
            }
        };
    }

    /** Several changes in one version. */
    @SafeVarargs
    public static Consumer<ConfigNode> all(Consumer<ConfigNode>... changes) {
        return node -> {
            for (Consumer<ConfigNode> change : changes) {
                change.accept(node);
            }
        };
    }
}
