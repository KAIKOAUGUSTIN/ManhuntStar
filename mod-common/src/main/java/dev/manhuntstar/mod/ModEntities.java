package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * Wraps Minecraft entities as core ones. An entity keeps its wrapper in a field the mod adds to it, so the
 * same entity always gives the same object and {@code ==} works on wrappers. When the game replaces an
 * entity with a copy (a player who respawns, an entity that changes dimension), the wrapper moves to the
 * copy, like Bukkit's entity objects do.
 */
public final class ModEntities {

    private ModEntities() {
    }

    public static GameEntity of(Entity entity) {
        if (entity == null) {
            return null;
        }
        EntityExt ext = (EntityExt) entity;
        if (ext.manhuntstar$wrapper() instanceof ModEntity cached) {
            return cached;
        }
        synchronized (ModEntities.class) {
            if (ext.manhuntstar$wrapper() instanceof ModEntity cached) {
                return cached;
            }
            ModEntity fresh = create(entity);
            ext.manhuntstar$setWrapper(fresh);
            return fresh;
        }
    }

    private static ModEntity create(Entity entity) {
        if (entity instanceof ServerPlayer p) {
            return new ModPlayer(p);
        } else if (entity instanceof Mannequin m) {
            return new ModBotBody(m);
        } else if (entity instanceof AbstractHorse h) {
            return new ModHorse(h);
        } else if (entity instanceof Slime s) {
            return new ModSlime(s);
        } else if (entity instanceof Warden w) {
            return new ModWarden(w);
        } else if (entity instanceof Mob m) {
            return new ModMob(m);
        } else if (entity instanceof LivingEntity l) {
            return new ModLiving(l);
        } else if (entity instanceof AbstractBoat b) {
            return new ModBoat(b);
        } else if (entity instanceof ItemEntity i) {
            return new ModItemEntity(i);
        } else if (entity instanceof AbstractArrow a) {
            return new ModArrow(a);
        } else if (entity instanceof Projectile p) {
            return new ModProjectile(p);
        }
        return new ModEntity(entity);
    }

    public static GameLiving of(LivingEntity entity) {
        return (GameLiving) of((Entity) entity);
    }

    public static GamePlayer of(ServerPlayer player) {
        return (GamePlayer) of((Entity) player);
    }

    public static BotBody of(Mannequin body) {
        return (BotBody) of((Entity) body);
    }

    /** The entity behind a core entity. */
    public static Entity vanilla(GameEntity entity) {
        return entity == null ? null : ((ModEntity) entity).vanilla();
    }

    public static LivingEntity vanilla(GameLiving entity) {
        return entity == null ? null : ((ModLiving) entity).vanilla();
    }

    public static ServerPlayer vanilla(GamePlayer player) {
        return player == null ? null : ((ModPlayer) player).vanilla();
    }

    /** Alive and in its world (Bukkit's {@code isValid}). */
    static boolean isValid(Entity e) {
        return e.isAlive() && !e.isRemoved();
    }

    /** The game replaced {@code old} with {@code copy}: the core object (and the mod's flags) go to the copy. */
    public static void moved(Entity old, Entity copy) {
        EntityExt from = (EntityExt) old;
        EntityExt to = (EntityExt) copy;
        to.manhuntstar$setTransient(from.manhuntstar$transient());
        to.manhuntstar$setFixedPose(from.manhuntstar$fixedPose());
        if (old instanceof MobExt a && copy instanceof MobExt b) {
            b.manhuntstar$setAware(a.manhuntstar$aware());
        }
        synchronized (ModEntities.class) {
            if (from.manhuntstar$wrapper() instanceof ModEntity w && w.accepts(copy)) {
                w.setVanilla(copy);
                to.manhuntstar$setWrapper(w);
                from.manhuntstar$setWrapper(null);
            }
        }
    }
}
