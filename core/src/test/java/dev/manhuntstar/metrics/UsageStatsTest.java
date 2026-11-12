package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** core's tests have no manhuntstar.properties, like a build without FASTSTATS_TOKEN: nothing is sent. */
class UsageStatsTest {

    @Test
    void noTokenWithoutTheBuildFile() {
        assertEquals("", UsageStats.token());
    }
}
