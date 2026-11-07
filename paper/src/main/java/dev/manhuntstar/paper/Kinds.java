package dev.manhuntstar.paper;

import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.EntityTrait;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zoglin;

/**
 * Conversions between Bukkit's {@link EntityType} and core's {@link EntityKind}, whose traits come from
 * the Bukkit interfaces of each type's entity class (an {@code Enemy} is {@link EntityTrait#ENEMY}...).
 */
public final class Kinds {

    private static final EntityKind[] ENTITIES;

    static {
        EntityType[] all = EntityType.values();
        ENTITIES = new EntityKind[all.length];
        for (EntityType type : all) {
            ENTITIES[type.ordinal()] = EntityKind.register(type.name(), withTags(type, traits(type.getEntityClass())));
        }
    }

    private Kinds() {
    }

    /** Registers the game's entity types in core (before reading config.yml and bots.yml). */
    public static void init() {
        // the work is done by the static block, once
    }

    private static EntityTrait[] traits(Class<? extends Entity> c) {
        List<EntityTrait> out = new ArrayList<>();
        if (c == null) {
            return new EntityTrait[0];
        }
        add(out, c, LivingEntity.class, EntityTrait.LIVING);
        add(out, c, Player.class, EntityTrait.PLAYER);
        add(out, c, Mob.class, EntityTrait.MOB);
        add(out, c, Enemy.class, EntityTrait.ENEMY);
        add(out, c, Projectile.class, EntityTrait.PROJECTILE);
        add(out, c, AbstractArrow.class, EntityTrait.ARROW);
        add(out, c, Trident.class, EntityTrait.TRIDENT);
        add(out, c, EnderPearl.class, EntityTrait.ENDER_PEARL);
        add(out, c, Firework.class, EntityTrait.FIREWORK);
        add(out, c, TNTPrimed.class, EntityTrait.TNT);
        add(out, c, Item.class, EntityTrait.ITEM);
        add(out, c, ExperienceOrb.class, EntityTrait.EXPERIENCE_ORB);
        add(out, c, Vehicle.class, EntityTrait.VEHICLE);
        add(out, c, Boat.class, EntityTrait.BOAT);
        add(out, c, AbstractHorse.class, EntityTrait.HORSE);
        add(out, c, EnderDragon.class, EntityTrait.ENDER_DRAGON);
        add(out, c, Enderman.class, EntityTrait.ENDERMAN);
        add(out, c, Spider.class, EntityTrait.SPIDER);
        add(out, c, Slime.class, EntityTrait.SLIME);
        add(out, c, Warden.class, EntityTrait.WARDEN);
        add(out, c, PiglinAbstract.class, EntityTrait.PIGLIN_FAMILY);
        add(out, c, Piglin.class, EntityTrait.PIGLIN);
        add(out, c, PigZombie.class, EntityTrait.ZOMBIFIED_PIGLIN);
        add(out, c, Hoglin.class, EntityTrait.HOGLIN);
        add(out, c, Zoglin.class, EntityTrait.ZOGLIN);
        add(out, c, Breeze.class, EntityTrait.BREEZE);
        add(out, c, Creaking.class, EntityTrait.CREAKING);
        add(out, c, MagmaCube.class, EntityTrait.MAGMA_CUBE);
        add(out, c, Mannequin.class, EntityTrait.MANNEQUIN);
        add(out, c, ArmorStand.class, EntityTrait.ARMOR_STAND);
        return out.toArray(EntityTrait[]::new);
    }

    private static EntityTrait[] withTags(EntityType type, EntityTrait[] traits) {
        if (type == EntityType.UNKNOWN) {
            // UNKNOWN has no key, and the tags can't be asked about it
            return traits;
        }
        List<EntityTrait> out = new ArrayList<>(List.of(traits));
        if (Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(type)) {
            out.add(EntityTrait.SENSITIVE_TO_SMITE);
        }
        if (Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(type)) {
            out.add(EntityTrait.SENSITIVE_TO_BANE);
        }
        return out.toArray(EntityTrait[]::new);
    }

    private static void add(List<EntityTrait> out, Class<?> c, Class<?> iface, EntityTrait trait) {
        if (iface.isAssignableFrom(c)) {
            out.add(trait);
        }
    }

    public static EntityKind of(EntityType type) {
        return ENTITIES[type.ordinal()];
    }

    public static EntityType type(EntityKind kind) {
        return EntityType.valueOf(kind.name());
    }
}
