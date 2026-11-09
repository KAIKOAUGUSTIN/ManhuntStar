package dev.manhuntstar.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A sound of the game, by its id ({@code entity.player.attack.sweep}). One instance per id; the platform
 * keeps its own object for it in {@link #handle()}.
 */
public final class GameSound {

    private static final Map<String, GameSound> BY_KEY = new ConcurrentHashMap<>();

    private final String key;
    private volatile Object handle;

    private GameSound(String key) {
        this.key = key;
    }

    public static GameSound of(String key) {
        return BY_KEY.computeIfAbsent(key, GameSound::new);
    }

    /** The game's id, without the {@code minecraft:} namespace. */
    public String key() {
        return key;
    }

    /** The platform's object for this sound (resolved and kept by the platform). */
    public Object handle() {
        return handle;
    }

    public void setHandle(Object handle) {
        this.handle = handle;
    }

    @Override
    public String toString() {
        return key;
    }
}
