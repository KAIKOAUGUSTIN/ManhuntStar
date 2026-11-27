package dev.manhuntstar.bot.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.perception.Sighting.Kind;
import dev.manhuntstar.util.Vec3;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/** The hunters' call: what one bot sees goes to its group and to the groups that aren't its enemies. */
class TeamIntelTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();
    /** "zombies" and "skeletons" hunt together; "rivals" is the enemy of both. */
    private static final BiPredicate<String, String> ALLIES = (a, b) -> !Set.of(a, b).contains("rivals");

    private static Sighting seen(Kind kind, long tick) {
        return new Sighting(PLAYER, WORLD, new Vec3(tick, 64, 0), kind, tick);
    }

    @Test
    void theGroupAndItsAlliesKnowItButNotTheEnemies() {
        TeamIntel intel = new TeamIntel();
        intel.report("Zombies", seen(Kind.BODY, 100));
        assertNotNull(intel.best("zombies", ALLIES, PLAYER, 100, 100));
        assertNotNull(intel.best("skeletons", ALLIES, PLAYER, 100, 100));
        assertNull(intel.best("rivals", ALLIES, PLAYER, 100, 100));
    }

    @Test
    void theBodyBeatsAGuessFromTheSameMoment() {
        TeamIntel intel = new TeamIntel();
        intel.report("zombies", seen(Kind.BODY, 100));
        intel.report("zombies", seen(Kind.SOUND, 102));
        assertEquals(Kind.BODY, intel.best("zombies", ALLIES, PLAYER, 102, 100).kind());
        // an ally's name sighting right after doesn't blur it either
        intel.report("skeletons", seen(Kind.NAMETAG, 101));
        assertEquals(Kind.BODY, intel.best("zombies", ALLIES, PLAYER, 102, 100).kind());
        // a clue well after is newer news
        intel.report("skeletons", seen(Kind.SOUND, 100 + PerceptionTuning.PRECISION_WINDOW_TICKS + 5));
        assertEquals(Kind.SOUND, intel.best("zombies", ALLIES, PLAYER, 120, 100).kind());
    }

    @Test
    void aClueIsWorthItOnlyForAWhile() {
        TeamIntel intel = new TeamIntel();
        intel.report("zombies", seen(Kind.NAMETAG, 100));
        assertNotNull(intel.best("zombies", ALLIES, PLAYER, 100 + PerceptionTuning.MEMORY_TICKS, PerceptionTuning.MEMORY_TICKS));
        assertNull(intel.best("zombies", ALLIES, PLAYER, 101 + PerceptionTuning.MEMORY_TICKS, PerceptionTuning.MEMORY_TICKS));
        intel.purge(101 + PerceptionTuning.MEMORY_TICKS, PerceptionTuning.MEMORY_TICKS);
        assertNull(intel.best("zombies", ALLIES, PLAYER, 101 + PerceptionTuning.MEMORY_TICKS, Long.MAX_VALUE), "forgotten");
    }

    @Test
    void aClueFoundEmptyIsDroppedForTheWholeTeam() {
        TeamIntel intel = new TeamIntel();
        intel.report("zombies", seen(Kind.SOUND, 100));
        intel.report("rivals", seen(Kind.SOUND, 100));
        intel.dismiss("skeletons", ALLIES, PLAYER, 100);
        assertNull(intel.best("zombies", ALLIES, PLAYER, 100, 100));
        assertNotNull(intel.best("rivals", ALLIES, PLAYER, 100, 100), "the enemies still believe it");
    }

    @Test
    void theAnnouncementTellsEveryoneAndAFoundPortalOnlyTheTeam() {
        TeamIntel intel = new TeamIntel();
        assertFalse(intel.knowsDeparture("zombies", ALLIES, PLAYER, 500));
        intel.announce(PLAYER, 499);
        assertTrue(intel.knowsDeparture("rivals", ALLIES, PLAYER, 500), "the chat is for everyone");
        assertFalse(intel.knowsDeparture("rivals", ALLIES, PLAYER, 900), "another trip, later");
        intel.discover("zombies", PLAYER, 900);
        assertTrue(intel.knowsDeparture("skeletons", ALLIES, PLAYER, 900));
        assertFalse(intel.knowsDeparture("rivals", ALLIES, PLAYER, 900));
        intel.clear();
        assertFalse(intel.knowsDeparture("zombies", ALLIES, PLAYER, 900), "a new hunt");
    }
}
