package dev.manhuntstar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What FastStats is sent besides its own metrics, and the token the jars get at build time. */
class UsageStatsTest {

    private static final List<String> IDS = List.of(UsageStats.BOTS, UsageStats.LANGUAGE, UsageStats.DEFAULT_DIFFICULTY);

    /**
     * FastStats' own fields: the dashboard doesn't take a data source with one of these Reference IDs, so a
     * value sent under one of them can't be charted (that's why the language isn't "language").
     */
    private static final Set<String> FASTSTATS_FIELDS = Set.of(
            "bounce_rate", "browser", "browser_version", "client", "cookieless", "core_count", "country", "created_at",
            "device", "entry_page", "event", "event_properties", "exit_page", "game_version", "is_identified",
            "java_vendor", "java_version", "language", "minecraft_version", "online_mode", "os", "os_arch", "os_name",
            "os_version", "outbound_link", "page", "platform_version", "player_count", "plugin_version", "project_type",
            "referrer", "referrer_domain", "screen", "server_count", "server_id", "server_type", "session_duration",
            "session_id", "timezone", "title", "url", "user_id", "utm_campaign", "utm_content", "utm_medium",
            "utm_source", "utm_term");

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

    @Test
    void theIdsAreOnesTheSdkTakes() {
        for (String id : IDS) {
            assertTrue(id.matches("[a-z0-9_]+"), id);
        }
    }

    @Test
    void noIdIsTheNameOfAFastStatsField() {
        for (String id : IDS) {
            assertFalse(FASTSTATS_FIELDS.contains(id), id + " is one of FastStats' own fields");
        }
    }

    @Test
    void theLanguageIsPluginLanguage() {
        // the Reference ID of the data source on the FastStats project
        assertEquals("plugin_language", UsageStats.LANGUAGE);
    }
}
