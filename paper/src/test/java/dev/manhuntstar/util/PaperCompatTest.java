package dev.manhuntstar.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

/** The tests run on the newest Paper API, so PaperCompat must take the 26.3 path. */
class PaperCompatTest {

    @Test
    void usesIsMarkedInvulnerableWhenTheServerHasIt() {
        assertTrue(PaperCompat.hasMarkedInvulnerable());
        // marked invulnerable, but isInvulnerable() (generic damage) says otherwise, as on 26.3
        assertTrue(PaperCompat.isMarkedInvulnerable(entity(true, false)));
        assertFalse(PaperCompat.isMarkedInvulnerable(entity(false, true)));
    }

    private static Entity entity(boolean marked, boolean generic) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isMarkedInvulnerable" -> marked;
            case "isInvulnerable" -> generic;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "TestEntity";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
