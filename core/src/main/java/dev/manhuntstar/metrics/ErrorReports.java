package dev.manhuntstar.metrics;

import dev.faststats.ErrorTracker;
import dev.faststats.SimpleContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * ManhuntStar's error reports on FastStats (https://faststats.dev), shared by the plugin and the mods:
 * the tracker given to the FastStats context ({@code errorTrackerService}). Turned off with
 * {@code general.error-reports: false}, or for every plugin and mod with {@code submitErrors=false} in
 * faststats/config.properties.
 *
 * <p>What it reports:
 * <ul>
 *   <li>errors nobody caught, but only when their stack trace goes through ManhuntStar's code: on the
 *       mods every mod (and Minecraft, on Fabric) shares one class loader, so the class loader alone
 *       would also report other mods' errors;</li>
 *   <li>the errors ManhuntStar logs as SEVERE with an exception ("could not start", a game event that
 *       failed...), except those reading YAML: a typo in the server's own config.yml isn't a bug, and the
 *       message would carry a piece of it.</li>
 * </ul>
 * The SDK takes out IP addresses, the system user name and a few secrets from the messages, and sends
 * them with the metrics (every 30 minutes and when the server stops).
 */
public final class ErrorReports {

    private static final String OUR_PACKAGE = "dev.manhuntstar.";
    /** Libraries moved into ManhuntStar's package in the plugin jar (FastStats itself): not our code. */
    private static final String OUR_LIBRARIES = "dev.manhuntstar.libs.";
    private static final String YAML_PACKAGE = "org.yaml.snakeyaml.";

    private final ErrorTracker tracker;
    private final Logger logger;
    private final Handler handler;

    /** Starts tracking: the uncaught errors right away, and what {@code logger} logs as SEVERE. */
    public ErrorReports(Logger logger) {
        this.logger = logger;
        tracker = ErrorTracker.contextAware(ErrorReports.class.getClassLoader());
        tracker.addExclusionRule((thread, error) -> !isOurs(error));
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (isReported(record)) {
                    tracker.trackError(record.getThrown());
                }
            }

            @Override
            public void flush() {
                // nothing buffered
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        logger.addHandler(handler);
    }

    /**
     * Would FastStats send error reports, going by its config.properties as it is now? Read only (the
     * SDK writes the file when it starts): no when the file isn't there yet (FastStats' first run on
     * the server sends nothing), when {@code enabled} or {@code submitErrors} is false, or with
     * {@code -Dfaststats.enabled=false}. Checked before making an {@code ErrorReports}, so that nothing
     * is tracked when nothing will be sent.
     */
    public static boolean sentBy(File fastStatsConfig) {
        if (!Boolean.parseBoolean(System.getProperty("faststats.enabled", "true")) || !fastStatsConfig.isFile()) {
            return false;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(fastStatsConfig.toPath())) {
            props.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return isTrue(props, "enabled") && isTrue(props, "submitErrors");
    }

    /** A missing key is true, like FastStats' own defaults. */
    private static boolean isTrue(Properties props, String key) {
        return Boolean.parseBoolean(props.getProperty(key, "true").trim());
    }

    /** For the FastStats context: {@code .errorTrackerService(reports.tracker())}. */
    public ErrorTracker tracker() {
        return tracker;
    }

    /**
     * Keeps tracking only if {@code context} sends the reports. It doesn't when FastStats is off for every
     * plugin (faststats/config.properties: enabled or submitErrors false) or on FastStats' first run on
     * this server; then nothing would send or clear what the tracker collects, so it stops.
     *
     * @return whether the reports are sent (and the tracking goes on)
     */
    public boolean closeUnlessSentBy(SimpleContext context) {
        boolean sent = context.submissionActive && context.getConfig().errorTracking() && context.errorTrackerService().isPresent();
        if (!sent) {
            close();
        }
        return sent;
    }

    /** Stops tracking (the plugin being disabled): the JVM's handler for uncaught errors goes back. */
    public void close() {
        logger.removeHandler(handler);
        tracker.detachErrorContext();
    }

    /** Is ManhuntStar's own code in the stack trace of the error or of one of its causes? */
    static boolean isOurs(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            for (StackTraceElement frame : t.getStackTrace()) {
                String name = frame.getClassName();
                if (name.startsWith(OUR_PACKAGE) && !name.startsWith(OUR_LIBRARIES)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A record sent as a handled error: SEVERE, with an exception that isn't about reading YAML. */
    static boolean isReported(LogRecord record) {
        Throwable thrown = record.getThrown();
        if (thrown == null || record.getLevel().intValue() < Level.SEVERE.intValue()) {
            return false;
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = thrown; t != null && seen.add(t); t = t.getCause()) {
            if (t.getClass().getName().startsWith(YAML_PACKAGE)) {
                return false;
            }
        }
        return true;
    }
}
