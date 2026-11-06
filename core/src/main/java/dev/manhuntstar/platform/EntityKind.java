package dev.manhuntstar.platform;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A type of entity ({@code CREEPER}, {@code ZOMBIE}...), named like in config.yml: the game's id in
 * capitals. There's one instance per name, so they can be compared with {@code ==}. The platform
 * registers the types that exist in the game ({@link #register}) when it starts.
 */
public final class EntityKind {

    private static final Map<String, EntityKind> BY_NAME = new ConcurrentHashMap<>();

    private final String name;
    private volatile boolean known;
    private volatile long traits;

    private EntityKind(String name) {
        this.name = name;
    }

    /** The type with this name (registered or not). */
    public static EntityKind of(String name) {
        return BY_NAME.computeIfAbsent(name, EntityKind::new);
    }

    /** The type with exactly this name, if the game has it; null otherwise. */
    public static EntityKind find(String name) {
        EntityKind kind = BY_NAME.get(name);
        return kind != null && kind.known ? kind : null;
    }

    /** Marks a type as existing in the game (called by the platform). */
    public static EntityKind register(String name, EntityTrait... traits) {
        EntityKind kind = of(name);
        long bits = 0;
        for (EntityTrait t : traits) {
            bits |= t.bit();
        }
        kind.traits = bits;
        kind.known = true;
        return kind;
    }

    public boolean is(EntityTrait trait) {
        return (traits & trait.bit()) != 0;
    }

    public String name() {
        return name;
    }

    /** The game's id ({@code creeper}). */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name;
    }
}
