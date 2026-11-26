package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.faststats.Config;
import dev.faststats.FastStatsContext;
import dev.faststats.SimpleContext;
import dev.faststats.internal.PlatformLoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.error.YAMLException;

/** Which errors ErrorReports sends: only ManhuntStar's, and not the server's config typos. */
class ErrorReportsTest {

    /** What the SDK sets on FastStats' first run on the server (see ErrorReports.FIRST_RUN). */
    private static final String FIRST_RUN = "faststats.first-run";

    @TempDir
    Path dir;

    @AfterEach
    void forgetFirstRun() {
        System.clearProperty(FIRST_RUN);
    }

    private File fastStatsConfig(String text) throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, text);
        return file.toFile();
    }

    @Test
    void trackingStartsWithTheReportsAndCloseGivesBackTheJvmsHandler() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        ErrorReports reports = new ErrorReports(logger);
        try {
            assertEquals(1, logger.getHandlers().length, "what the logger logs as SEVERE is tracked");
            assertTrue(reports.tracker().isContextAttached());
            assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler(), "uncaught errors are tracked too");
            // through the handler: a SEVERE with an exception is tracked, a warning isn't
            logger.log(Level.SEVERE, "Manhunt* could not start", new IllegalStateException("boom"));
            logger.log(Level.WARNING, "MineSkin is down", new IllegalStateException("boom"));
            Handler handler = logger.getHandlers()[0];
            handler.flush();
            handler.close();
        } finally {
            reports.close();
        }
        assertEquals(0, logger.getHandlers().length);
        assertFalse(reports.tracker().isContextAttached());
        assertSame(before, Thread.getDefaultUncaughtExceptionHandler(), "the JVM's own handler is back");
    }

    @Test
    void errorReportsOffNeverTrack() throws IOException {
        File sends = fastStatsConfig("enabled=true\nsubmitErrors=true\n");
        assertEquals(ErrorReports.Mode.NONE, ErrorReports.mode(false, true, sends));
        assertEquals(ErrorReports.Mode.NONE, ErrorReports.mode(false, false, sends));
    }

    @Test
    void errorsAreTrackedWhenFastStatsSendsThem() throws IOException {
        File sends = fastStatsConfig("enabled=true\nsubmitErrors=true\n");
        assertEquals(ErrorReports.Mode.TRACK, ErrorReports.mode(true, true, sends));
        assertEquals(ErrorReports.Mode.TRACK, ErrorReports.mode(true, false, sends));
    }

    @Test
    void onlyErrorReportsOnFastStatsFirstRunGetAnIdleTracker() {
        File missing = dir.resolve("config.properties").toFile();
        assertEquals(ErrorReports.Mode.IDLE, ErrorReports.mode(true, false, missing));
        assertEquals(ErrorReports.Mode.NONE, ErrorReports.mode(true, true, missing), "statistics are the service");
    }

    @Test
    void errorsTurnedOffInFastStatsAreNotTracked() throws IOException {
        File off = fastStatsConfig("enabled=true\nsubmitErrors=false\n");
        assertEquals(ErrorReports.Mode.IDLE, ErrorReports.mode(true, false, off));
        assertEquals(ErrorReports.Mode.NONE, ErrorReports.mode(true, true, off));
    }

    @Test
    void notSentOnFastStatsFirstRun() {
        assertFalse(ErrorReports.sentBy(dir.resolve("config.properties").toFile()));
    }

    @Test
    void sentWhenFastStatsSendsErrors() throws IOException {
        assertTrue(ErrorReports.sentBy(fastStatsConfig("enabled=true\nsubmitErrors=true\nsubmitMetrics=true\n")));
        assertTrue(ErrorReports.sentBy(fastStatsConfig("enabled=true\n")));
    }

    @Test
    void notSentWhenFastStatsIsOffOrErrorsAre() throws IOException {
        assertFalse(ErrorReports.sentBy(fastStatsConfig("enabled=false\nsubmitErrors=true\n")));
        assertFalse(ErrorReports.sentBy(fastStatsConfig("enabled=true\nsubmitErrors=false\n")));
    }

    @Test
    void notSentWhenAnotherPluginHadFastStatsFirstRunInThisJvm() throws IOException {
        File sends = fastStatsConfig("enabled=true\nsubmitErrors=true\n");
        System.setProperty(FIRST_RUN, "true");
        assertFalse(ErrorReports.sentBy(sends));
        assertEquals(ErrorReports.Mode.NONE, ErrorReports.mode(true, true, sends));
    }

    /** A FastStats context as the plugin and the mods make it, without a platform: nothing is ever sent. */
    private static FastStatsContext context(ErrorReports reports, boolean errorTracking) {
        Config config = new Config() {
            private final UUID id = UUID.randomUUID();

            @Override
            public UUID serverId() {
                return id;
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public boolean submitMetrics() {
                return false;
            }

            @Override
            public boolean errorTracking() {
                return errorTracking;
            }

            @Override
            public boolean additionalMetrics() {
                return false;
            }

            @Override
            public boolean debug() {
                return false;
            }
        };
        return new SimpleContext.Builder("test")
                .loggerFactory(new PlatformLoggerFactory((level, error, message) -> {
                }))
                .config(config)
                .projectName("manhuntstar")
                .token("0123456789abcdef0123456789abcdef")
                .errorTrackerService(reports.tracker())
                .onReady(reports::closeUnlessSent)
                .build();
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    @Test
    void trackingGoesOnWhenTheContextSendsTheReports() {
        Logger logger = quietLogger();
        ErrorReports reports = new ErrorReports(logger);
        FastStatsContext context = context(reports, true);
        try {
            context.ready();
            assertTrue(reports.tracker().isContextAttached());
            assertEquals(1, logger.getHandlers().length);
        } finally {
            context.shutdown();
            reports.close();
        }
    }

    @Test
    void trackingStopsOnFastStatsFirstRun() {
        Logger logger = quietLogger();
        ErrorReports reports = new ErrorReports(logger);
        FastStatsContext context = context(reports, true);
        try {
            // what the SDK's own hook does just before, when it shows its first-run notice
            System.setProperty(FIRST_RUN, "true");
            context.ready();
            assertFalse(reports.tracker().isContextAttached());
            assertEquals(0, logger.getHandlers().length);
        } finally {
            context.shutdown();
            reports.close();
        }
    }

    @Test
    void trackingStopsWhenFastStatsDoesntSendErrors() {
        Logger logger = quietLogger();
        ErrorReports reports = new ErrorReports(logger);
        FastStatsContext context = context(reports, false);
        try {
            context.ready();
            assertFalse(reports.tracker().isContextAttached());
            assertEquals(0, logger.getHandlers().length);
        } finally {
            context.shutdown();
            reports.close();
        }
    }

    private static Throwable thrownAt(String className, Throwable cause) {
        Throwable t = new IllegalStateException("boom", cause);
        t.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(className, "run", "X.java", 1),
                new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1)});
        return t;
    }

    @Test
    void anErrorThroughOurCodeIsOurs() {
        assertTrue(ErrorReports.isOurs(thrownAt("dev.manhuntstar.bot.Bot", null)));
    }

    @Test
    void anErrorFromAnotherModIsNotOurs() {
        assertFalse(ErrorReports.isOurs(thrownAt("com.example.othermod.Thing", null)));
        assertFalse(ErrorReports.isOurs(thrownAt("net.minecraft.server.MinecraftServer", null)));
    }

    @Test
    void ourCodeInACauseCounts() {
        assertTrue(ErrorReports.isOurs(thrownAt("net.minecraft.server.MinecraftServer", thrownAt("dev.manhuntstar.mod.ModHooks", null))));
    }

    @Test
    void librariesMovedIntoOurPackageAreNotOurs() {
        assertFalse(ErrorReports.isOurs(thrownAt("dev.manhuntstar.libs.faststats.SimpleMetrics", null)));
    }

    @Test
    void severeRecordsWithAnExceptionAreReported() {
        LogRecord record = new LogRecord(Level.SEVERE, "Manhunt* could not start");
        record.setThrown(new IllegalStateException("boom"));
        assertTrue(ErrorReports.isReported(record));
    }

    @Test
    void warningsAndRecordsWithoutAnExceptionAreNot() {
        LogRecord warning = new LogRecord(Level.WARNING, "MineSkin is down");
        warning.setThrown(new IllegalStateException("boom"));
        assertFalse(ErrorReports.isReported(warning));
        assertFalse(ErrorReports.isReported(new LogRecord(Level.SEVERE, "no exception")));
    }

    @Test
    void yamlErrorsAreNotReported() {
        LogRecord record = new LogRecord(Level.SEVERE, "Cannot load config.yml");
        record.setThrown(new RuntimeException("wrapped", new YAMLException("mapping values are not allowed here")));
        assertFalse(ErrorReports.isReported(record));
    }
}
