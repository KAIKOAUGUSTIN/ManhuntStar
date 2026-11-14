package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** What FastStats is sent besides its own metrics, and the token the jars get at build time. */
class UsageStatsTest {

    /**
     * core's tests have a manhuntstar.properties whose token isn't a FastStats project token (32
     * lowercase letters and digits): a build with a wrong FASTSTATS_TOKEN sends nothing.
     */
    @Test
    void aTokenThatIsntAProjectTokenIsNeverUsed() {
        assertEquals("", UsageStats.token());
    }

    @Test
    void nothingIsCountedBeforeTheFirstUpdate() {
        UsageStats usage = new UsageStats();
        assertEquals(0, usage.bots());
        assertNull(usage.language());
        assertNull(usage.defaultDifficulty());
    }
}
