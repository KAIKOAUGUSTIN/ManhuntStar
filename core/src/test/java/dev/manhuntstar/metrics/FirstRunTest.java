package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** When ManhuntStar takes it for FastStats' first run on the server or computer, and doesn't start FastStats. */
class FirstRunTest {

    @TempDir
    Path dir;

    @AfterEach
    void forgetFirstRun() {
        System.clearProperty(FirstRun.PROPERTY);
    }

    private File config() throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "enabled=true\n");
        return file.toFile();
    }

    @Test
    void noFileYetIsLeftToTheSdk() {
        // ManhuntStar's own context creates it, and the SDK keeps that first run from sending
        assertFalse(FirstRun.inThisJvm(dir.resolve("config.properties").toFile()));
    }

    @Test
    void aFileCreatedSinceThisJvmStartedIsAFirstRun() throws IOException {
        File config = config();
        // another plugin or mod created it while this JVM runs
        assertTrue(FirstRun.inThisJvm(config));
        assertTrue(FirstRun.createdSince(config, System.currentTimeMillis() - 60_000));
    }

    @Test
    void aFileFromAnEarlierRunIsNot() throws IOException {
        // created before "this JVM" started
        assertFalse(FirstRun.createdSince(config(), System.currentTimeMillis() + 60_000));
    }

    @Test
    void theSdksFirstRunCounts() {
        System.setProperty(FirstRun.PROPERTY, "true");
        assertTrue(FirstRun.inThisJvm(dir.resolve("config.properties").toFile()));
    }
}
