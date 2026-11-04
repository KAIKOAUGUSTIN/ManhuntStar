package dev.manhuntstar.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

    private PaperCompat() {
    }

    /**
     * Whether the entity was made invulnerable with {@code setInvulnerable(true)}. On 26.3,
     * {@code isInvulnerable()} is deprecated and checks invulnerability to generic damage instead of
     * that flag, so {@code isMarkedInvulnerable()} is used there.
     */
    public static boolean isMarkedInvulnerable(Entity entity) {
        if (IS_MARKED_INVULNERABLE != null) {
            try {
                return (boolean) IS_MARKED_INVULNERABLE.invokeExact(entity);
            } catch (Throwable t) {
                throw new IllegalStateException("Entity#isMarkedInvulnerable failed", t);
            }
        }
        return legacyIsInvulnerable(entity);
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
