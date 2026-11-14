package dev.manhuntstar.metrics;

import dev.manhuntstar.ManhuntCore;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * What the plugin and the mods send to FastStats (https://faststats.dev) besides what the SDK sends on
 * its own: the number of bots, the language and the default difficulty.
 *
 * <p>FastStats reads the values off the server thread, where the bots can't be read, so core copies them
 * on the server thread once a minute ({@link #update}) and FastStats reads the copies.
 */
public final class UsageStats {

    /** How often the values are copied, in ticks (1 minute). */
    public static final int UPDATE_TICKS = 20 * 60;

    /** What a FastStats project token looks like (the SDK refuses any other). */
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{32}");

    private volatile int bots;
    private volatile String language;
    private volatile String defaultDifficulty;

    /** Copies the values; on the server thread. */
    public void update(ManhuntCore core) {
        bots = core.botManager().aliveBots().size();
        language = core.messages().language();
        defaultDifficulty = core.settings().defaultDifficulty.get().configName();
    }

    public int bots() {
        return bots;
    }

    /** Null before the first {@link #update}. */
    public String language() {
        return language;
    }

    /** Null before the first {@link #update}. */
    public String defaultDifficulty() {
        return defaultDifficulty;
    }

    /**
     * The FastStats project token put into manhuntstar.properties at build time (FASTSTATS_TOKEN), or an
     * empty string: builds without it (forks, local builds) send nothing.
     */
    public static String token() {
        try (InputStream in = UsageStats.class.getResourceAsStream("/manhuntstar.properties")) {
            if (in == null) {
                return "";
            }
            Properties props = new Properties();
            props.load(in);
            String token = props.getProperty("faststats-token", "").trim();
            return TOKEN.matcher(token).matches() ? token : "";
        } catch (IOException e) {
            return "";
        }
    }
}
