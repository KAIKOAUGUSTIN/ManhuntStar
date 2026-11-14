package dev.manhuntstar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void aMomentOfInvulnerabilityAfterDamageIsNotMarkedInvulnerability() {
        // on 26.3, isMarkedInvulnerable() is also true for a moment after the entity is hurt; the
        // server's own entity tells the flag set by setInvulnerable apart
        assertFalse(PaperCompat.isMarkedInvulnerable(entityWithHandle(true, false)));
        assertTrue(PaperCompat.isMarkedInvulnerable(entityWithHandle(true, true)));
        assertTrue(PaperCompat.isMarkedInvulnerable(entityWithHandle(false, true)));
    }

    @Test
    void whatCantBeReadFromTheServersEntityIsLeftToIsMarkedInvulnerable() {
        assertNull(PaperCompat.permanentlyInvulnerable(new NoHandle()));
        assertNull(PaperCompat.permanentlyInvulnerable(new NullHandle()));
        assertNull(PaperCompat.permanentlyInvulnerable(new FailingHandle()));
        assertNull(PaperCompat.permanentlyInvulnerable(new HandleOf(new Object())));
        assertNull(PaperCompat.permanentlyInvulnerable(new HandleOf(new NotABoolean())));
        assertEquals(Boolean.TRUE, PaperCompat.permanentlyInvulnerable(new HandleOf(new Handle(true))));
        assertEquals(Boolean.FALSE, PaperCompat.permanentlyInvulnerable(new HandleOf(new Handle(false))));
    }

    /** An entity of a server without getHandle(): isMarkedInvulnerable() as it is. */
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

    /** An entity like Paper's CraftEntity, whose getHandle() is the server's own entity. */
    private static Entity entityWithHandle(boolean marked, boolean permanent) {
        Handle handle = new Handle(permanent);
        return (Entity) Proxy.newProxyInstance(PaperCompatTest.class.getClassLoader(), new Class<?>[] {Entity.class, HasHandle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isMarkedInvulnerable" -> marked;
                    case "getHandle" -> handle;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TestCraftEntity";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    public interface HasHandle {
        Object getHandle();
    }

    /** The server's entity: the flag set by setInvulnerable, without the moment after damage. */
    public static final class Handle {

        private final boolean permanent;

        Handle(boolean permanent) {
            this.permanent = permanent;
        }

        public boolean isPermanentlyInvulnerable() {
            return permanent;
        }
    }

    public static final class NoHandle {
    }

    public static final class NullHandle {
        public Object getHandle() {
            return null;
        }
    }

    public static final class FailingHandle {
        public Object getHandle() {
            throw new IllegalStateException("not on this thread");
        }
    }

    public static final class HandleOf {

        private final Object handle;

        HandleOf(Object handle) {
            this.handle = handle;
        }

        public Object getHandle() {
            return handle;
        }
    }

    /** A server entity whose isPermanentlyInvulnerable() isn't the one Minecraft has. */
    public static final class NotABoolean {
        public String isPermanentlyInvulnerable() {
            return "yes";
        }
    }
}
