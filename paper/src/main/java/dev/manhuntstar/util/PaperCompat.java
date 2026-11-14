package dev.manhuntstar.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.entity.Entity;

/**
 * Differences between the supported Paper versions (26.2 and 26.3), in one place.
 *
 * <p>The plugin is compiled against the oldest supported API. Anything newer is looked up once, when
 * the class loads, and each method here picks the new call when the server has it and the old one
 * otherwise. The {@code compileAgainstLatest} Gradle task checks that the code still compiles
 * against the newest API.
 */
public final class PaperCompat {

    /** {@code Entity#isMarkedInvulnerable()}, added in Paper 26.3; {@code null} on 26.2. */
    private static final MethodHandle IS_MARKED_INVULNERABLE = find(Entity.class, "isMarkedInvulnerable", MethodType.methodType(boolean.class));

    /** {@code getHandle()} of each Bukkit entity class (Paper's CraftEntity), the server's own entity. */
    private static final ClassValue<Optional<Method>> GET_HANDLE = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            return publicMethod(type, "getHandle");
        }
    };

    /** {@code isPermanentlyInvulnerable()} of each class of the server's entities (Minecraft 26.3). */
    private static final ClassValue<Optional<Method>> IS_PERMANENTLY_INVULNERABLE = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            return publicMethod(type, "isPermanentlyInvulnerable").filter(m -> m.getReturnType() == boolean.class);
        }
    };

    private PaperCompat() {
    }

    /**
     * Whether the entity was made invulnerable with {@code setInvulnerable(true)}. On 26.3,
     * {@code isInvulnerable()} is deprecated and checks invulnerability to generic damage instead of
     * that flag, and {@code isMarkedInvulnerable()} is also true for a moment after the entity is hurt
     * (Minecraft's temporary invulnerability), so the flag is read from the server's own entity, and
     * {@code isMarkedInvulnerable()} is only used where that can't be read.
     */
    public static boolean isMarkedInvulnerable(Entity entity) {
        if (IS_MARKED_INVULNERABLE != null) {
            Boolean permanent = permanentlyInvulnerable(entity);
            if (permanent != null) {
                return permanent;
            }
            try {
                return (boolean) IS_MARKED_INVULNERABLE.invokeExact(entity);
            } catch (Throwable t) {
                throw new IllegalStateException("Entity#isMarkedInvulnerable failed", t);
            }
        }
        return legacyIsInvulnerable(entity);
    }

    /**
     * The flag set by {@code setInvulnerable}, read from the server's own entity
     * ({@code getHandle().isPermanentlyInvulnerable()}), or {@code null} where it can't be read.
     */
    static Boolean permanentlyInvulnerable(Object entity) {
        Optional<Method> getHandle = GET_HANDLE.get(entity.getClass());
        if (getHandle.isEmpty()) {
            return null;
        }
        try {
            Object handle = getHandle.get().invoke(entity);
            if (handle == null) {
                return null;
            }
            Optional<Method> permanent = IS_PERMANENTLY_INVULNERABLE.get(handle.getClass());
            return permanent.isEmpty() ? null : (Boolean) permanent.get().invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Optional<Method> publicMethod(Class<?> type, String name) {
        try {
            return Optional.of(type.getMethod(name));
        } catch (NoSuchMethodException | SecurityException e) {
            return Optional.empty();
        }
    }

    /** Whether the running server has {@code Entity#isMarkedInvulnerable()} (Paper 26.3 or newer). */
    public static boolean hasMarkedInvulnerable() {
        return IS_MARKED_INVULNERABLE != null;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static boolean legacyIsInvulnerable(Entity entity) {
        return entity.isInvulnerable();
    }

    private static MethodHandle find(Class<?> owner, String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }
}
