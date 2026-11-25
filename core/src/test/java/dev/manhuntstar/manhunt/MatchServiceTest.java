package dev.manhuntstar.manhunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.Modes.Defeat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The end of the match: when a death ends it, the hunt time and the bot that killed the most. */
class MatchServiceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void firstDeathEndsAtOnceAndNeverDoesNot() {
        assertTrue(MatchService.deathEndsMatch(Defeat.FIRST_DEATH, false));
        assertFalse(MatchService.deathEndsMatch(Defeat.NEVER, true));
    }

    @Test
    void allDiedEndsOnceEveryoneOnlineIsOut() {
        assertFalse(MatchService.deathEndsMatch(Defeat.ALL_DIED, MatchService.everyoneOut(List.of(a, b), Set.of(a))));
        assertTrue(MatchService.deathEndsMatch(Defeat.ALL_DIED, MatchService.everyoneOut(List.of(a, b), Set.of(a, b))));
    }

    @Test
    void someoneOfflineDoesNotHoldTheMatchUp() {
        // b is offline: only a counts, and a is out
        assertTrue(MatchService.everyoneOut(List.of(a), Set.of(a, b)));
        assertTrue(MatchService.everyoneOut(List.of(a), Set.of(a)));
        // nobody online: no defeat
        assertFalse(MatchService.everyoneOut(List.of(), Set.of(a)));
    }

    @Test
    void onlyNeverKeepsTheDeadInTheGame() {
        assertTrue(Defeat.ALL_DIED.eliminates());
        assertTrue(Defeat.FIRST_DEATH.eliminates());
        assertFalse(Defeat.NEVER.eliminates());
    }

    @Test
    void huntTimeHasHoursOnlyWhenNeeded() {
        assertEquals("0:00", MatchService.formatTime(0));
        assertEquals("0:45", MatchService.formatTime(45));
        assertEquals("12:05", MatchService.formatTime(725));
        assertEquals("1:12:05", MatchService.formatTime(4325));
        assertEquals("0:00", MatchService.formatTime(-3));
    }

    @Test
    void topKillerIsTheBotWithTheMostKillsAndTheFirstOnATie() {
        assertNull(MatchService.topKiller(Map.of()));
        Map<UUID, Integer> kills = new LinkedHashMap<>();
        kills.put(a, 2);
        kills.put(b, 3);
        assertEquals(b, MatchService.topKiller(kills));
        kills.put(b, 2);
        assertEquals(a, MatchService.topKiller(kills));
    }
}
