package dev.manhuntstar.platform;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * An entity of the server, like Bukkit's Entity: reading it reads the entity as it is now. The platform
 * keeps one object per entity, so {@code ==} behaves like it does on Bukkit entities.
 */
public interface GameEntity {

    UUID uuid();

    /** The entity's number in this server session (for packets such as block damage). */
    int entityId();

    EntityKind kind();

    default boolean is(EntityTrait trait) {
        return kind().is(trait);
    }

    GameWorld world();

    /** Position of the feet. */
    Vec3 position();

    default Pos blockPos() {
        return position().toPos();
    }

    float yaw();

    float pitch();

    /** Unit vector the entity is looking along. */
    default Vec3 direction() {
        return Vec3.fromRotation(yaw(), pitch());
    }

    Vec3 velocity();

    void setVelocity(Vec3 velocity);

    /** Alive and in a loaded world. */
    boolean isValid();

    boolean isDead();

    double height();

    double width();

    Box boundingBox();

    boolean isOnGround();

    boolean isInWater();

    boolean isInLava();

    boolean isUnderWater();

    int fireTicks();

    void setFireTicks(int ticks);

    float fallDistance();

    void setFallDistance(float distance);

    List<GameEntity> passengers();

    GameEntity vehicle();

    default boolean isInsideVehicle() {
        return vehicle() != null;
    }

    boolean addPassenger(GameEntity passenger);

    boolean removePassenger(GameEntity passenger);

    boolean teleport(GameWorld world, Vec3 position, float yaw, float pitch);

    default boolean teleport(Place place) {
        return teleport(place.world(), place.pos(), place.yaw(), place.pitch());
    }

    /** Where the entity is, with its facing (Bukkit's getLocation). */
    Place place();

    void remove();

    void setRotation(float yaw, float pitch);

    int ticksLived();

    int portalCooldown();

    void setPortalCooldown(int ticks);

    boolean hasGravity();

    void setGravity(boolean gravity);

    /** Made invulnerable with {@link #setInvulnerable} (players in creative don't count). */
    boolean isInvulnerable();

    void setInvulnerable(boolean invulnerable);

    /** The entity has a persistent mark (saved with it) with this key. */
    boolean hasMark(String key);

    /** Sets a persistent mark (saved with the entity in the world). */
    void setMark(String key, String value);

    void removeMark(String key);

    /** The name shown over the entity. */
    void setCustomName(Component name);

    /** Whether the name shows above the entity even when no one looks at it. */
    void setCustomNameVisible(boolean visible);

    /** Entities whose box is within the given distances of this one's box. */
    List<GameEntity> nearbyEntities(double x, double y, double z);
}
