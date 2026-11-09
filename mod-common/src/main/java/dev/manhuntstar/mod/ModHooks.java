package dev.manhuntstar.mod;

import dev.manhuntstar.ManhuntCore;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.GameEvents;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.platform.GameWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Where the mixins hand the game's events to core, doing what the plugin's listeners do with Bukkit's
 * events. Each does nothing while Manhunt* isn't running, and an error in one is logged instead of
 * breaking the game (as Bukkit does with a listener that fails).
 */
public final class ModHooks {

    /** Set while the mod itself teleports an entity (Bukkit's teleport by a plugin fires no teleport event). */
    static final ThreadLocal<Boolean> MOD_TELEPORT = ThreadLocal.withInitial(() -> false);
    /** Set while an entity goes through a portal (Bukkit fires a portal event there, not a teleport one). */
    public static final ThreadLocal<Boolean> PORTAL = ThreadLocal.withInitial(() -> false);

    private ModHooks() {
    }

    private static ManhuntCore core() {
        ModPlatform p = ModPlatform.running();
        return p == null ? null : p.core();
    }

    private static void failed(String what, Throwable t) {
        ModPlatform p = ModPlatform.running();
        if (p != null) {
            p.logger().log(java.util.logging.Level.SEVERE, "Manhunt* failed handling " + what, t);
        }
    }

    private static Bot bot(ManhuntCore core, Entity e) {
        return e instanceof Mannequin ? core.botManager().byEntity(ModEntities.of(e)) : null;
    }

    // ------------------------------------------------------------------ server

    public static void serverTick() {
        ModPlatform p = ModPlatform.running();
        if (p != null) {
            p.serverTick();
        }
    }

    // ------------------------------------------------------------------ damage, death, totem

    /**
     * An entity is about to take {@code damage} (the game's damage before armor): returns the damage to
     * take, or a negative number to cancel it. The spear charge of a bot first, then the bot being hit,
     * then what the plugin's monitors do with a hit that goes through.
     */
    public static float hurt(LivingEntity victim, DamageSource source, float damage) {
        ManhuntCore core = core();
        if (core == null) {
            return damage;
        }
        try {
            GameEvents events = core.gameEvents();
            double d = damage;
            Bot spearBot = source.typeHolder().is(DamageTypes.SPEAR) ? bot(core, source.getEntity()) : null;
            if (spearBot != null) {
                GameEvents.DamageDecision dec = events.botSpearHits(spearBot, ModEntities.of(victim), d);
                if (dec.cancelled()) {
                    return -1;
                }
                d = dec.damage();
            }
            Bot victimBot = bot(core, victim);
            if (victimBot != null) {
                GameEvents.DamageDecision dec = events.botDamaged(victimBot, new ModDamageInfo(source), d);
                if (dec.cancelled()) {
                    return -1;
                }
                d = dec.damage();
            }
            if (spearBot != null) {
                events.botSpearLanded(spearBot);
            }
            if (victimBot != null && source.getDirectEntity() != null && d > 0) {
                events.botKnockedBack(victimBot);
            }
            return (float) d;
        } catch (Throwable t) {
            failed("damage", t);
            return damage;
        }
    }

    /**
     * A living entity died and is about to drop its loot. A bot's body drops what core decides instead
     * (true: the game's drops are skipped).
     */
    public static boolean botDrops(LivingEntity entity, ServerLevel level, DamageSource source) {
        ManhuntCore core = core();
        if (core == null) {
            return false;
        }
        try {
            Bot bot = bot(core, entity);
            if (bot == null) {
                return false;
            }
            ModBotDeath death = new ModBotDeath(source);
            core.botManager().handleDeath(bot, death);
            for (ItemStack s : death.drops) {
                entity.spawnAtLocation(level, s);
            }
            if (death.exp > 0) {
                ExperienceOrb.award(level, entity.position(), death.exp);
            }
            return true;
        } catch (Throwable t) {
            failed("a bot's death", t);
            return false;
        }
    }

    /** A player died (for the bots' kill count). */
    public static void playerDied(ServerPlayer player, DamageSource source) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                core.gameEvents().playerDied(ModEntities.of(source.getEntity()));
            } catch (Throwable t) {
                failed("a player's death", t);
            }
        }
    }

    /** A totem saved an entity. */
    public static void totemUsed(LivingEntity entity) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                Bot bot = bot(core, entity);
                if (bot != null) {
                    core.gameEvents().botUsedTotem(bot);
                }
            } catch (Throwable t) {
                failed("a totem", t);
            }
        }
    }

    // ------------------------------------------------------------------ entities

    /** The game teleported an entity (a command, a chorus fruit, a pearl...), not the mod and not a portal. */
    public static void teleported(Entity entity) {
        if (MOD_TELEPORT.get() || PORTAL.get()) {
            return;
        }
        ManhuntCore core = core();
        if (core != null) {
            try {
                Bot bot = bot(core, entity);
                if (bot != null) {
                    core.gameEvents().botTeleported(bot);
                }
            } catch (Throwable t) {
                failed("a teleport", t);
            }
        }
    }

    /** A projectile hit something: the aim diagnostics, and the landing of a bot's ender pearl. */
    public static void projectileHit(Projectile projectile, HitResult result) {
        ModPlatform p = ModPlatform.running();
        ManhuntCore core = core();
        if (core == null) {
            return;
        }
        try {
            p.aim().hit(projectile, result);
            if (projectile instanceof ThrownEnderpearl pearl && pearl.getOwner() instanceof Mannequin m) {
                Bot bot = core.botManager().byEntity(ModEntities.of(m));
                if (bot != null) {
                    core.gameEvents().botPearlLanded(bot, (GameProjectile) ModEntities.of(pearl), ModEntities.of(pearl).place());
                }
            }
        } catch (Throwable t) {
            failed("a projectile hit", t);
        }
    }

    /** An entity entered a world (spawned or loaded with its chunk). */
    public static void entityAdded(Entity entity) {
        ModPlatform p = ModPlatform.running();
        ManhuntCore core = core();
        if (core == null) {
            return;
        }
        try {
            p.mobGoals().added(entity);
            if (entity instanceof Mannequin || entity instanceof AbstractHorse) {
                // a bot's body left from before (a crash) is removed; one just spawned is the bot's by the next tick
                GameEntity e = ModEntities.of(entity);
                BotManager manager = core.botManager();
                core.server().runNextTick(() -> {
                    if (e.isValid()) {
                        manager.removeOrphan(e);
                    }
                });
            }
        } catch (Throwable t) {
            failed("an entity added to the world", t);
        }
    }

    /** An entity left its world (removed, unloaded, dead or moved to another dimension). */
    public static void entityRemoved(Entity entity) {
        ManhuntCore core = core();
        if (core == null) {
            return;
        }
        try {
            Bot bot = bot(core, entity);
            if (bot != null) {
                core.gameEvents().botBodyRemoved(bot);
            }
        } catch (Throwable t) {
            failed("an entity removed from the world", t);
        }
    }

    // ------------------------------------------------------------------ blocks

    public static void playerPlacedBlock(Level level, BlockPos pos) {
        ManhuntCore core = core();
        if (core != null && level instanceof ServerLevel s) {
            try {
                core.gameEvents().playerPlacedBlock(ModWorld.of(s), ModConvert.pos(pos));
            } catch (Throwable t) {
                failed("a placed block", t);
            }
        }
    }

    public static void playerBrokeBlock(Level level, BlockPos pos, BlockState was) {
        ManhuntCore core = core();
        if (core != null && level instanceof ServerLevel s) {
            try {
                core.gameEvents().playerBrokeBlock(ModWorld.of(s), ModConvert.pos(pos), ModTypes.of(was.getBlock()));
            } catch (Throwable t) {
                failed("a broken block", t);
            }
        }
    }

    /** A block changed some other way (explosion, flowing water or lava, fire). */
    public static void blockChanged(Object level, BlockPos pos) {
        ManhuntCore core = core();
        if (core != null && level instanceof ServerLevel s) {
            try {
                GameWorld w = ModWorld.of(s);
                core.gameEvents().blockChanged(w, pos.getX(), pos.getZ());
            } catch (Throwable t) {
                failed("a changed block", t);
            }
        }
    }

    public static void blocksExploded(Level level, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            blockChanged(level, p);
        }
    }

    /** A player opened (or clicked) a crafting table. */
    public static void craftingTable(ServerPlayer player) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                core.gameEvents().playerUsedCraftingTable(ModEntities.of(player));
            } catch (Throwable t) {
                failed("a crafting table", t);
            }
        }
    }

    // ------------------------------------------------------------------ players

    public static void playerJoined(ServerPlayer player) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                GamePlayer p = ModEntities.of(player);
                core.gameEvents().playerJoined(p);
                core.updates().onJoin(p);
            } catch (Throwable t) {
                failed("a player joining", t);
            }
        }
    }

    public static void playerRespawned(ServerPlayer player) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                core.gameEvents().playerRespawned(ModEntities.of(player));
            } catch (Throwable t) {
                failed("a respawn", t);
            }
        }
    }

    public static void playerQuit(ServerPlayer player) {
        ManhuntCore core = core();
        if (core != null) {
            try {
                core.menus().forget(player.getUUID());
            } catch (Throwable t) {
                failed("a player leaving", t);
            }
        }
    }

    /** A chat message: true when a menu was waiting for it (it isn't shown to anyone). */
    public static boolean chat(ServerPlayer player, String text) {
        ManhuntCore core = core();
        if (core == null) {
            return false;
        }
        try {
            return core.menus().chatTyped(ModEntities.of(player), text.trim());
        } catch (Throwable t) {
            failed("a chat message", t);
            return false;
        }
    }

    /** A command is about to run: true when it was /list and the list with the bots was sent instead. */
    public static boolean command(net.minecraft.commands.CommandSourceStack source, String command) {
        ManhuntCore core = core();
        if (core == null) {
            return false;
        }
        try {
            GameEvents events = core.gameEvents();
            if (!events.replacesListCommand(command)) {
                return false;
            }
            ModServer.get().audiences().audience(source).sendMessage(events.listText());
            return true;
        } catch (Throwable t) {
            failed("a command", t);
            return false;
        }
    }

    /** The server list: the bots count as online players (number and names). */
    public static ServerStatus serverStatus(ServerStatus status) {
        ManhuntCore core = core();
        if (core == null || status == null || status.players().isEmpty()) {
            return status;
        }
        try {
            List<Bot> bots = core.gameEvents().botsForServerList();
            if (bots.isEmpty()) {
                return status;
            }
            ServerStatus.Players players = status.players().get();
            List<NameAndId> sample = new ArrayList<>(players.sample());
            for (Bot b : bots) {
                if (sample.size() >= 12) {
                    break;
                }
                sample.add(new NameAndId(b.id(), b.profileName()));
            }
            ServerStatus.Players more = new ServerStatus.Players(players.max(), players.online() + bots.size(), sample);
            return new ServerStatus(status.description(), Optional.of(more), status.version(), status.favicon(), status.enforcesSecureChat());
        } catch (Throwable t) {
            failed("the server list", t);
            return status;
        }
    }
}
