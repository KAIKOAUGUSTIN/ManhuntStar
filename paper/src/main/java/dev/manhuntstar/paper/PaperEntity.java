package dev.manhuntstar.paper;

import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.PaperCompat;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** A Bukkit {@link Entity} as a core {@link GameEntity}: every call is the Bukkit one. */
public class PaperEntity implements GameEntity {

    private static final Map<String, NamespacedKey> MARK_KEYS = new ConcurrentHashMap<>();

    /** The Bukkit key of a mark: the plugin's namespace and the mark's name. */
    static NamespacedKey markKey(String key) {
        return MARK_KEYS.computeIfAbsent(key, k -> new NamespacedKey(PaperServer.plugin(), k));
    }

    protected final Entity entity;

    PaperEntity(Entity entity) {
        this.entity = entity;
    }

    public Entity bukkit() {
        return entity;
    }

    @Override
    public UUID uuid() {
        return entity.getUniqueId();
    }

    @Override
    public int entityId() {
        return entity.getEntityId();
    }

    @Override
    public EntityKind kind() {
        return Kinds.of(entity.getType());
    }

    @Override
    public GameWorld world() {
        return PaperWorld.of(entity.getWorld());
    }

    @Override
    public Vec3 position() {
        Location l = entity.getLocation();
        return new Vec3(l.getX(), l.getY(), l.getZ());
    }

    @Override
    public boolean hasMark(String key) {
        return entity.getPersistentDataContainer().has(markKey(key), PersistentDataType.STRING);
    }

    @Override
    public void setMark(String key, String value) {
        entity.getPersistentDataContainer().set(markKey(key), PersistentDataType.STRING, value);
    }

    @Override
    public void removeMark(String key) {
        entity.getPersistentDataContainer().remove(markKey(key));
    }

    @Override
    public void setCustomName(Component name) {
        entity.customName(name);
    }

    @Override
    public void setCustomNameVisible(boolean visible) {
        entity.setCustomNameVisible(visible);
    }

    @Override
    public Place place() {
        return Locations.place(entity.getLocation());
    }

    @Override
    public float yaw() {
        return entity.getLocation().getYaw();
    }

    @Override
    public float pitch() {
        return entity.getLocation().getPitch();
    }

    @Override
    public Vec3 direction() {
        return Locations.vec(entity.getLocation().getDirection());
    }

    @Override
    public Vec3 velocity() {
        Vector v = entity.getVelocity();
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        entity.setVelocity(new Vector(velocity.x(), velocity.y(), velocity.z()));
    }

    @Override
    public boolean isValid() {
        return entity.isValid();
    }

    @Override
    public boolean isDead() {
        return entity.isDead();
    }

    @Override
    public double height() {
        return entity.getHeight();
    }

    @Override
    public double width() {
        return entity.getWidth();
    }

    @Override
    public Box boundingBox() {
        return Locations.box(entity.getBoundingBox());
    }

    @Override
    public boolean isOnGround() {
        return entity.isOnGround();
    }

    @Override
    public boolean isInWater() {
        return entity.isInWater();
    }

    @Override
    public boolean isInLava() {
        return entity.isInLava();
    }

    @Override
    public boolean isUnderWater() {
        return entity.isUnderWater();
    }

    @Override
    public int fireTicks() {
        return entity.getFireTicks();
    }

    @Override
    public void setFireTicks(int ticks) {
        entity.setFireTicks(ticks);
    }

    @Override
    public float fallDistance() {
        return entity.getFallDistance();
    }

    @Override
    public void setFallDistance(float distance) {
        entity.setFallDistance(distance);
    }

    @Override
    public List<GameEntity> passengers() {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : entity.getPassengers()) {
            out.add(PaperEntities.of(e));
        }
        return out;
    }

    @Override
    public GameEntity vehicle() {
        return PaperEntities.of(entity.getVehicle());
    }

    @Override
    public boolean isInsideVehicle() {
        return entity.isInsideVehicle();
    }

    @Override
    public boolean addPassenger(GameEntity passenger) {
        return entity.addPassenger(PaperEntities.bukkit(passenger));
    }

    @Override
    public boolean removePassenger(GameEntity passenger) {
        return entity.removePassenger(PaperEntities.bukkit(passenger));
    }

    @Override
    public boolean teleport(GameWorld world, Vec3 position, float yaw, float pitch) {
        return entity.teleport(new Location(PaperWorld.bukkit(world), position.x(), position.y(), position.z(), yaw, pitch));
    }

    @Override
    public void remove() {
        entity.remove();
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        entity.setRotation(yaw, pitch);
    }

    @Override
    public int ticksLived() {
        return entity.getTicksLived();
    }

    @Override
    public int portalCooldown() {
        return entity.getPortalCooldown();
    }

    @Override
    public void setPortalCooldown(int ticks) {
        entity.setPortalCooldown(ticks);
    }

    @Override
    public boolean hasGravity() {
        return entity.hasGravity();
    }

    @Override
    public void setGravity(boolean gravity) {
        entity.setGravity(gravity);
    }

    @Override
    public boolean isInvulnerable() {
        return PaperCompat.isMarkedInvulnerable(entity);
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        entity.setInvulnerable(invulnerable);
    }

    @Override
    public List<GameEntity> nearbyEntities(double x, double y, double z) {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : entity.getNearbyEntities(x, y, z)) {
            out.add(PaperEntities.of(e));
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PaperEntity other && other.entity.equals(entity);
    }

    @Override
    public int hashCode() {
        return entity.hashCode();
    }

    @Override
    public String toString() {
        return entity.toString();
    }
}
