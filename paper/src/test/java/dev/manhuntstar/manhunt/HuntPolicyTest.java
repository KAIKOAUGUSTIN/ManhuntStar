package dev.manhuntstar.manhunt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Late-joiner protection: it lasts as long as the ring countdown, never less than the minimum. */
class HuntPolicyTest {

    @Test
    void protectionLastsTheRingCountdownWithAMinimum() {
        assertEquals(HuntPolicy.MIN_PROTECTION_SECONDS, HuntPolicy.protectionFor(0));
        assertEquals(5, HuntPolicy.protectionFor(3));
        assertEquals(60, HuntPolicy.protectionFor(60));
    }
}
