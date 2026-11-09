package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameItemEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameMob;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameProjectile;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Warden;

/**
 * Wraps Bukkit entities as core ones. The same Bukkit entity always gives the same wrapper (while
 * something holds it), so {@code ==} on wrappers behaves like {@code ==} on Bukkit entities.
 */
public final class PaperEntities {

    private static final Map<Entity, WeakReference<PaperEntity>> CACHE = new WeakHashMap<>();

    private PaperEntities() {
    }

    public static synchronized GameEntity of(Entity entity) {
        if (entity == null) {
            return null;
        }
        WeakReference<PaperEntity> ref = CACHE.get(entity);
        PaperEntity cached = ref == null ? null : ref.get();
        if (cached != null && cached.entity == entity) {
            return cached;
        }
        PaperEntity fresh;
        if (entity instanceof Player p) {
            fresh = new PaperPlayer(p);
        } else if (entity instanceof Mannequin m) {
            fresh = new PaperBotBody(m);
        } else if (entity instanceof AbstractHorse h) {
            fresh = new PaperHorse(h);
        } else if (entity instanceof Slime s) {
            fresh = new PaperSlime(s);
        } else if (entity instanceof Warden w) {
            fresh = new PaperWarden(w);
        } else if (entity instanceof Mob m) {
            fresh = new PaperMob(m);
        } else if (entity instanceof LivingEntity l) {
            fresh = new PaperLiving(l);
        } else if (entity instanceof Boat b) {
            fresh = new PaperBoat(b);
        } else if (entity instanceof Item i) {
            fresh = new PaperItemEntity(i);
        } else if (entity instanceof AbstractArrow a) {
            fresh = new PaperArrow(a);
        } else if (entity instanceof Projectile p) {
            fresh = new PaperProjectile(p);
        } else {
            fresh = new PaperEntity(entity);
        }
        CACHE.put(entity, new WeakReference<>(fresh));
        return fresh;
    }

    public static GameLiving of(LivingEntity entity) {
        return (GameLiving) of((Entity) entity);
    }

    public static GamePlayer of(Player player) {
        return (GamePlayer) of((Entity) player);
    }

    public static BotBody of(Mannequin body) {
        return (BotBody) of((Entity) body);
    }

    public static GameMob of(Mob mob) {
        return (GameMob) of((Entity) mob);
    }

    public static GameHorse of(AbstractHorse horse) {
        return (GameHorse) of((Entity) horse);
    }

    public static GameItemEntity of(Item item) {
        return (GameItemEntity) of((Entity) item);
    }

    /** The Bukkit entity behind a core entity. */
    public static Entity bukkit(GameEntity entity) {
        return entity == null ? null : ((PaperEntity) entity).bukkit();
    }

    public static LivingEntity bukkit(GameLiving entity) {
        return entity == null ? null : ((PaperLiving) entity).bukkit();
    }

    public static Player bukkit(GamePlayer player) {
        return player == null ? null : ((PaperPlayer) player).bukkit();
    }

    public static Mannequin bukkit(BotBody body) {
        return body == null ? null : ((PaperBotBody) body).bukkit();
    }

    public static Mob bukkit(GameMob mob) {
        return mob == null ? null : ((PaperMob) mob).bukkit();
    }

    public static AbstractHorse bukkit(GameHorse horse) {
        return horse == null ? null : ((PaperHorse) horse).bukkit();
    }

    public static Projectile bukkit(GameProjectile projectile) {
        return projectile == null ? null : ((PaperProjectile) projectile).bukkit();
    }

    public static Item bukkit(GameItemEntity item) {
        return item == null ? null : ((PaperItemEntity) item).bukkit();
    }
}
