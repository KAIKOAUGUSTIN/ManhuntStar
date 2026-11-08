package dev.manhuntstar.mod;

import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * A Minecraft {@link Entity} as a core {@link GameEntity}, doing what Bukkit's {@code CraftEntity} does.
 * The entity behind it can change: when the game replaces an entity with a copy, the wrapper follows
 * (see {@link ModEntities#moved}).
 */
public class ModEntity implements GameEntity {

    /** Marks are entity tags {@code manhuntstar.<key>=<value>} (saved with the entity, like Bukkit's data container). */
    private static final String MARK_PREFIX = "manhuntstar.";

    private volatile Entity entity;

    ModEntity(Entity entity) {
        this.entity = entity;
    }

    public Entity vanilla() {
        return entity;
    }

    void setVanilla(Entity entity) {
        this.entity = entity;
    }

    /** Whether this wrapper can stand for a copy of its entity (the same kind of wrapper would be made for it). */
    boolean accepts(Entity copy) {
        return copy.getClass() == entity.getClass();
    }

    @Override
    public UUID uuid() {
        return entity.getUUID();
    }

    @Override
    public int entityId() {
        return entity.getId();
    }

    @Override
    public EntityKind kind() {
        return ModTypes.of(entity.getType());
    }

    @Override
    public GameWorld world() {
        return ModWorld.of(entity.level());
    }

    @Override
    public Vec3 position() {
        return ModConvert.vec(entity.position());
    }

    @Override
    public float yaw() {
        return entity.getYRot();
    }

    @Override
    public float pitch() {
        return entity.getXRot();
    }

    @Override
    public Vec3 velocity() {
        return ModConvert.vec(entity.getDeltaMovement());
    }

    @Override
    public void setVelocity(Vec3 velocity) {
        entity.setDeltaMovement(ModConvert.vanilla(velocity));
        entity.syncVelocity = true;
    }

    @Override
    public boolean isValid() {
        return ModEntities.isValid(entity);
    }

    @Override
    public boolean isDead() {
        return !entity.isAlive();
    }

    @Override
    public double height() {
        return entity.getBbHeight();
    }

    @Override
    public double width() {
        return entity.getBbWidth();
    }

    @Override
    public Box boundingBox() {
        return ModConvert.box(entity.getBoundingBox());
    }

    @Override
    public boolean isOnGround() {
        return entity.onGround();
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
        return entity.getRemainingFireTicks();
    }

    @Override
    public void setFireTicks(int ticks) {
        entity.setRemainingFireTicks(ticks);
    }

    @Override
    public float fallDistance() {
        return (float) entity.fallDistance;
    }

    @Override
    public void setFallDistance(float distance) {
        entity.fallDistance = distance;
    }

    @Override
    public List<GameEntity> passengers() {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : entity.getPassengers()) {
            out.add(ModEntities.of(e));
        }
        return out;
    }

    @Override
    public GameEntity vehicle() {
        return ModEntities.of(entity.getVehicle());
    }

    @Override
    public boolean addPassenger(GameEntity passenger) {
        Entity p = ModEntities.vanilla(passenger);
        if (p == entity) {
            throw new IllegalArgumentException("Entity cannot ride itself.");
        }
        return p.startRiding(entity, true, true);
    }

    @Override
    public boolean removePassenger(GameEntity passenger) {
        ModEntities.vanilla(passenger).stopRiding();
        return true;
    }

    @Override
    public boolean teleport(GameWorld world, Vec3 position, float yaw, float pitch) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        ServerLevel level = ModWorld.vanilla(world);
        ModHooks.MOD_TELEPORT.set(true);
        try {
            return entity.teleport(new TeleportTransition(level, ModConvert.vanilla(position), net.minecraft.world.phys.Vec3.ZERO, yaw, pitch,
                    Set.of(), TeleportTransition.DO_NOTHING)) != null;
        } finally {
            ModHooks.MOD_TELEPORT.set(false);
        }
    }

    @Override
    public Place place() {
        return new Place(world(), entity.getX(), entity.getY(), entity.getZ(), yaw(), pitch());
    }

    @Override
    public void remove() {
        entity.discard();
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("rotation not finite");
        }
        entity.forceSetRotation(ModConvert.normalizeYaw(yaw), false, ModConvert.normalizePitch(pitch), false);
    }

    @Override
    public int ticksLived() {
        return entity.tickCount;
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
        return !entity.isNoGravity();
    }

    @Override
    public void setGravity(boolean gravity) {
        entity.setNoGravity(!gravity);
    }

    @Override
    public boolean isInvulnerable() {
        return entity.isInvulnerable();
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        entity.setPermanentlyInvulnerable(invulnerable);
    }

    private String markTag(String key) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(MARK_PREFIX + key + "=")) {
                return tag;
            }
        }
        return null;
    }

    @Override
    public boolean hasMark(String key) {
        return markTag(key) != null;
    }

    @Override
    public void setMark(String key, String value) {
        removeMark(key);
        entity.addTag(MARK_PREFIX + key + "=" + value);
    }

    @Override
    public void removeMark(String key) {
        String tag;
        while ((tag = markTag(key)) != null) {
            entity.removeTag(tag);
        }
    }

    @Override
    public void setCustomName(Component name) {
        entity.setCustomName(name == null ? null : ModServer.get().vanilla(name));
    }

    @Override
    public void setCustomNameVisible(boolean visible) {
        entity.setCustomNameVisible(visible);
    }

    @Override
    public List<GameEntity> nearbyEntities(double x, double y, double z) {
        return ModWorld.wrap(entity.level().getEntities(entity, entity.getBoundingBox().inflate(x, y, z), e -> true));
    }

    /** Not saved with its chunk (Bukkit's {@code setPersistent(false)}). */
    void setPersistent(boolean persistent) {
        ((EntityExt) entity).manhuntstar$setTransient(!persistent);
    }

    @Override
    public String toString() {
        return entity.toString();
    }
}
