package dev.manhuntstar.metrics;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * FastStats' first run on this server or computer: nothing is sent until the next restart, so that the
 * owner can turn it off first. The SDK sees it in its own context, when that context creates
 * faststats/config.properties, and then sets {@link #PROPERTY} for every plugin and mod in the JVM, but
 * only once that context is made ready. A context built in between (another plugin or mod created the
 * file, and isn't ready yet) would take it for a later run and send. So ManhuntStar also counts as a
 * first run a config.properties created after this JVM started, and doesn't start FastStats then.
 */
public final class FirstRun {

    /** Set by the SDK (of any plugin or mod in this JVM) on FastStats' first run, when it shows its notice. */
    static final String PROPERTY = "faststats.first-run";

    private FirstRun() {
    }

    /**
     * Is this FastStats' first run on the server or computer, as far as can be told before ManhuntStar's
     * own context is made? Yes when the SDK has said so ({@link #PROPERTY}), or when config.properties was
     * created after this JVM started. No when the file isn't there yet: then ManhuntStar's own context
     * creates it, and the SDK keeps that first run from sending.
     */
    public static boolean inThisJvm(File fastStatsConfig) {
        return Boolean.getBoolean(PROPERTY) || createdSince(fastStatsConfig, ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    /**
     * Was {@code file} created at {@code since} (milliseconds since the epoch) or later? Where the file
     * system doesn't keep creation times, Java gives the last change instead: a file changed during this
     * run then also counts, and ManhuntStar sends nothing until the next restart. A file whose dates can't
     * be read counts too.
     */
    static boolean createdSince(File file, long since) {
        if (!file.isFile()) {
            return false;
        }
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis() >= since;
        } catch (IOException e) {
            return true;
        }
    }
}
