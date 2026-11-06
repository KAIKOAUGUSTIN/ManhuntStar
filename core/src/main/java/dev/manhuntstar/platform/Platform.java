package dev.manhuntstar.platform;

/**
 * The server the plugin or mod is running on, set by the platform when it starts. Static like Bukkit's
 * {@code Bukkit} class: there's one server per process.
 */
public final class Platform {

    private static volatile GameServer server;

    private Platform() {
    }

    public static GameServer server() {
        GameServer s = server;
        if (s == null) {
            throw new IllegalStateException("the platform hasn't started yet");
        }
        return s;
    }

    public static void set(GameServer s) {
        server = s;
    }
}
