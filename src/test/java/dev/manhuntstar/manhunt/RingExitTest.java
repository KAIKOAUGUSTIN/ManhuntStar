package dev.manhuntstar.manhunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Quem dispara a roda: só quem esteve dentro e saiu por alguns ticks seguidos. */
class RingExitTest {

    private static final double LEAVE = 6.5;

    /** Fica fora por {@code ticks} ticks; devolve se disparou em algum deles. */
    private static boolean stayOutside(RingExit exit, UUID p, int ticks) {
        boolean fired = false;
        for (int i = 0; i < ticks; i++) {
            fired |= exit.observe(p, 20.0);
        }
        return fired;
    }

    @Test
    void someoneWhoNeverEnteredDoesNotTrigger() {
        RingExit exit = new RingExit(LEAVE);
        UUID far = UUID.randomUUID();
        assertFalse(stayOutside(exit, far, 100));
    }

    @Test
    void leavingTriggersAfterSomeTicksOutside() {
        RingExit exit = new RingExit(LEAVE);
        UUID p = UUID.randomUUID();
        assertFalse(exit.observe(p, 2.0));
        assertFalse(stayOutside(exit, p, RingExit.OUTSIDE_TICKS - 1));
        assertTrue(exit.observe(p, 20.0));
    }

    @Test
    void aShortPushOutsideDoesNotTrigger() {
        RingExit exit = new RingExit(LEAVE);
        UUID p = UUID.randomUUID();
        exit.observe(p, 2.0);
        assertFalse(stayOutside(exit, p, RingExit.OUTSIDE_TICKS - 1));
        assertFalse(exit.observe(p, LEAVE));
        assertFalse(stayOutside(exit, p, RingExit.OUTSIDE_TICKS - 1));
    }

    @Test
    void anyoneWhoWasInsideCanTriggerAndTheFirstWins() {
        RingExit exit = new RingExit(LEAVE);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        exit.observe(a, 1.0);
        exit.observe(b, 3.0);
        boolean aFired = false;
        boolean bFired = false;
        for (int i = 0; i < RingExit.OUTSIDE_TICKS && !bFired; i++) {
            aFired |= exit.observe(a, 1.0);
            bFired = exit.observe(b, 15.0);
        }
        assertFalse(aFired);
        assertTrue(bFired);
    }

    @Test
    void enteringLaterAlsoCounts() {
        RingExit exit = new RingExit(LEAVE);
        UUID p = UUID.randomUUID();
        assertFalse(stayOutside(exit, p, 10));
        exit.observe(p, 4.0);
        assertTrue(stayOutside(exit, p, RingExit.OUTSIDE_TICKS));
    }

    @Test
    void forgottenPlayerStopsCounting() {
        RingExit exit = new RingExit(LEAVE);
        UUID p = UUID.randomUUID();
        exit.observe(p, 1.0);
        exit.forget(p);
        assertFalse(stayOutside(exit, p, 10));
        assertTrue(exit.inside().isEmpty());
    }

    @Test
    void protectionLastsTheRingCountdownWithAMinimum() {
        assertEquals(HuntPolicy.MIN_PROTECTION_SECONDS, HuntPolicy.protectionFor(0));
        assertEquals(5, HuntPolicy.protectionFor(3));
        assertEquals(60, HuntPolicy.protectionFor(60));
    }
}
