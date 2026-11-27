package dev.manhuntstar.bot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.manhuntstar.bot.ai.Search.Kind;
import org.junit.jupiter.api.Test;

/** Where a bot goes after a target nobody on its team sees, in order. */
class SearchTest {

    @Test
    void inTheTargetsWorld() {
        assertEquals(Kind.CLUE, Search.pick(false, false, false, false, true, true, true, true), "a clue first");
        assertEquals(Kind.TOWER, Search.pick(false, false, false, false, false, true, true, true));
        assertEquals(Kind.DIG, Search.pick(false, false, false, false, false, false, true, true));
        assertEquals(Kind.COMPASS, Search.pick(false, false, false, false, false, false, false, true));
        assertNull(Search.pick(false, false, false, false, false, false, false, false));
    }

    @Test
    void withTheTargetInAnotherWorld() {
        assertEquals(Kind.PORTAL, Search.pick(true, true, true, false, true, false, false, true), "the portal, once known");
        assertEquals(Kind.COMPASS, Search.pick(true, true, false, false, false, false, false, true),
                "not known yet: the compass leads to it");
        assertEquals(Kind.WAY_BACK, Search.pick(true, false, false, true, true, false, false, false), "left without a portal");
        assertNull(Search.pick(true, false, false, false, false, true, true, false), "no tower or digging for someone elsewhere");
    }
}
