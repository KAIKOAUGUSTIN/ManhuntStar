package dev.manhuntstar.paper;

import dev.manhuntstar.platform.EntityKind;
import org.bukkit.entity.EntityType;

/** Conversions between Bukkit's {@link EntityType} and core's {@link EntityKind}. */
public final class Kinds {

    private static final EntityKind[] ENTITIES;

    static {
        EntityType[] all = EntityType.values();
        ENTITIES = new EntityKind[all.length];
        for (EntityType type : all) {
            ENTITIES[type.ordinal()] = EntityKind.register(type.name());
        }
    }

    private Kinds() {
    }

    /** Registers the game's entity types in core (before reading config.yml and bots.yml). */
    public static void init() {
        // the work is done by the static block, once
    }

    public static EntityKind of(EntityType type) {
        return ENTITIES[type.ordinal()];
    }
}
