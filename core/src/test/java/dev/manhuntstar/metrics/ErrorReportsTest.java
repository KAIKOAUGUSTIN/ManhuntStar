package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.error.YAMLException;

/** Which errors ErrorReports sends: only ManhuntStar's, and not the server's config typos. */
class ErrorReportsTest {

    @TempDir
    Path dir;

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
