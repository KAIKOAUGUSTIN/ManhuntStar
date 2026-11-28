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
 *
 * <p>Each value is sent under its ID ({@link #BOTS}, {@link #LANGUAGE}, {@link #DEFAULT_DIFFICULTY}), and
 * FastStats only keeps it when the project has a data source with that same Reference ID.
 */
public final class UsageStats {

    /** The number of bots alive (a number). */
    public static final String BOTS = "bots";
    /**
     * The language of the messages (a string: en, pt-br... or the name of the server's own file in lang/).
     * Not "language": FastStats has a field of its own by that name (the browser's language, for websites),
     * and doesn't take a data source with the name of one of its fields.
     */
    public static final String LANGUAGE = "plugin_language";
    /** The bots' default difficulty (a string: easy, medium, hard or terminator). */
    public static final String DEFAULT_DIFFICULTY = "default_difficulty";

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
