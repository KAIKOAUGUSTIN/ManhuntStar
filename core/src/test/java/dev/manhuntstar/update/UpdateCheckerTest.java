package dev.manhuntstar.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void comparesVersionsByNumber() {
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.3") > 0);
        assertTrue(UpdateChecker.compare("1.5.5", "1.6.0") < 0);
        assertTrue(UpdateChecker.compare("2.0", "1.99.99") > 0);
    }

    @Test
    void ignoresPrefixesSuffixesAndMissingParts() {
        assertEquals(0, UpdateChecker.compare("v1.6.0", "1.6.0"));
        assertEquals(0, UpdateChecker.compare("1.6", "1.6.0"));
        assertEquals(0, UpdateChecker.compare("1.6.0-SNAPSHOT", "1.6.0"));
        assertTrue(UpdateChecker.compare("1.6.1", "1.6.0+build.5") > 0);
    }
}
