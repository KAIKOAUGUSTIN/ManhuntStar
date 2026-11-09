package dev.manhuntstar.bot;

import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.platform.GameRule;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/**
 * What the bots do when the game reports something (damage, death of a player, a pearl landing, blocks
 * changing, players joining...). Each platform's listeners translate their events into these calls and
 * apply the answer (cancel, change the damage).
 */
public final class GameEvents {

    /** Damage to let through ({@code damage}) or to cancel. */
    public record DamageDecision(boolean cancelled, double damage) {

        static final DamageDecision CANCEL = new DamageDecision(true, 0);
    }

    private final ManhuntHost host;

    public GameEvents(ManhuntHost host) {
        this.host = host;
    }

    private BotManager manager() {
        return host.botManager();
    }

    private Settings settings() {
        return host.settings();
    }

    // ------------------------------------------------------------------ the bots' bodies

    /** A bot's body is about to take damage: invulnerability, the API events, the shield and the difficulty. */
    public DamageDecision botDamaged(Bot bot, DamageInfo src, double damage) {
        DamageKind kind = src.kind();
        boolean bypass = kind == DamageKind.OUT_OF_WORLD || kind == DamageKind.GENERIC_KILL;
        if (!bypass && (manager().pause().isPaused() || settings().countdownInvulnerable.on() && manager().rings().holds(bot))) {
            return DamageDecision.CANCEL;
        }
        if (!bypass && settings().botInvincible.on()) {
            return DamageDecision.CANCEL;
        }
        GameEntity attacker = src.causingEntity();
        if (attacker instanceof GameProjectile proj && proj.shooter() != null) {
            attacker = proj.shooter();
        }
        if (attacker instanceof GamePlayer p) {
            OptionalDouble api = host.events().botDamagedByPlayer(bot, p, damage);
            if (api.isEmpty()) {
                return DamageDecision.CANCEL;
            }
            damage = api.getAsDouble();
        }
        if (kind == DamageKind.FALL && !host.events().botFallDamage(bot, damage)) {
            return DamageDecision.CANCEL;
        }
        if (attacker instanceof GameLiving le && le != bot.entity()) {
            bot.rememberFireSource(le.position());
            Vec3 dir = le.position().subtract(bot.pos());
            bot.setYawNow(dir.yaw());
            if (!(le instanceof GamePlayer) && !le.is(EntityTrait.ENDER_DRAGON) && manager().byEntity(le) == null) {
                bot.combat().setMobAttacker(le);
            }
        }
        if (bot.combat().isBlocking() && bot.combat().canBlockDamage(src)) {
            BotBody e = bot.entity();
            e.world().playSound(e.position(), Sounds.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 0.8f + (float) Math.random() * 0.4f);
            return DamageDecision.CANCEL;
        }
        return new DamageDecision(false, damage * bot.difficulty().damageTakenMultiplier());
    }

    /**
     * Spear charge of a bot on horseback (the game's own damage): doesn't hit allies, respects PvP being off
     * and takes the difficulty's attack multiplier, like the sword hit.
     */
    public DamageDecision botSpearHits(Bot bot, GameEntity victim, double damage) {
        if (manager().isAlly(bot, victim) || (victim instanceof GamePlayer && settings().respectPvp.on()
                && Boolean.FALSE.equals(victim.world().gameRule(GameRule.PVP)))) {
            return DamageDecision.CANCEL;
        }
        return new DamageDecision(false, damage * bot.difficulty().attackMultiplier());
    }

    /** The spear charge hit (after every listener let it through). */
    public void botSpearLanded(Bot bot) {
        bot.brain().horse().onLanceHit();
    }

    /** The bot took damage from an entity (it gets knocked back). */
    public void botKnockedBack(Bot bot) {
        bot.brain().onKnockback();
    }

    /** A player died; {@code killer} is who caused it (may be a bot's body, or null). */
    public void playerDied(GameEntity killer) {
        Bot bot = manager().byEntity(killer);
        if (bot != null) {
            bot.incrementKills();
        }
    }

    /** A totem saved the bot: it goes back to its usual offhand in the next tick. */
    public void botUsedTotem(Bot bot) {
        host.server().runNextTick(() -> {
            if (bot.isAlive()) {
                bot.onTotemConsumed();
            }
        });
    }

    /** The bot's body was teleported (by the game or another plugin). */
    public void botTeleported(Bot bot) {
        host.server().runNextTick(() -> {
            if (bot.isAlive()) {
                bot.onEntityTeleported();
            }
        });
    }

    /** The bot's pearl landed at {@code hit}: teleports (if the game didn't) and deals the pearl's 5 damage, as with the player. */
    public void botPearlLanded(Bot bot, GameProjectile pearl, Place hit) {
        host.server().runNextTick(() -> {
            if (!bot.isAlive()) {
                return;
            }
            BotBody e = bot.entity();
            if (e.world() != hit.world() || squaredDistance(e.position(), hit.pos()) > 4.0) {
                manager().teleportBot(bot, new Place(hit.world(), hit.pos(), e.yaw(), e.pitch()));
            } else {
                bot.onEntityTeleported();
            }
            bot.motor().resetFallTracking();
            BotBody body = bot.entity();
            body.damage(5.0, DamageKind.ENDER_PEARL, body, pearl);
            hit.world().playSound(hit.pos(), Sounds.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        });
    }

    private static double squaredDistance(Vec3 a, Vec3 b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** The bot's body was removed without dying (a command, another plugin): it comes back in the same place. */
    public void botBodyRemoved(Bot bot) {
        host.server().runNextTick(() -> manager().onEntityLost(bot));
    }

    // ------------------------------------------------------------------ blocks

    /** A player placed a block (towers, skybases): the bots know it isn't natural. */
    public void playerPlacedBlock(GameWorld w, Pos p) {
        manager().blockTracker().markPlaced(w, p);
        host.pathService().invalidate(w, p.x(), p.z());
    }

    /** A player broke a block (it was {@code type}). */
    public void playerBrokeBlock(GameWorld w, Pos p, BlockType type) {
        manager().blockTracker().unmark(w, p);
        host.pathService().invalidate(w, p.x(), p.z());
        if (type.name().endsWith("_BED")) {
            manager().caveAmbush().onBlockBroken(w, p);
        }
    }

    /** A block changed some other way (explosion, flowing water, fire): the paths through its column are redone. */
    public void blockChanged(GameWorld w, int x, int z) {
        host.pathService().invalidate(w, x, z);
    }

    // ------------------------------------------------------------------ players

    /** A player opened a crafting table (the bow ambush waits for it). */
    public void playerUsedCraftingTable(GamePlayer p) {
        manager().tactics().recordCraftingTableUse(p);
    }

    /** A player joined: the hunt rule for late joiners, then the bots in their player list. */
    public void playerJoined(GamePlayer p) {
        // hunt in progress: hunted right away, protected or left out, depending on the option
        manager().policy().admit(p);
        host.server().runLater(() -> {
            if (p.isOnline()) {
                host.tabList().sendAll(p);
            }
        }, 2L);
    }

    /** The player respawned far away: bots more than 150 blocks away reappear near them after a while. */
    public void playerRespawned(GamePlayer p) {
        host.server().runNextTick(() -> {
            for (Bot b : manager().aliveBots()) {
                if (b.world() != p.world()) {
                    continue;
                }
                double d = Math.hypot(b.pos().x() - p.position().x(), b.pos().z() - p.position().z());
                if (d > 150.0) {
                    b.brain().forceRedistributeWithDelay((int) (d / 10.0 * 20.0));
                }
            }
        });
    }

    // ------------------------------------------------------------------ /list and the server list

    /** The bots to count as online players in the server list (none when the option is off). */
    public List<Bot> botsForServerList() {
        return settings().countInServerList.on() ? manager().aliveBots() : List.of();
    }

    /** {@code raw} (without the slash) is /list and the bots should show up in it. */
    public boolean replacesListCommand(String raw) {
        if (!settings().replaceListCommand.on() || manager().aliveCount() == 0) {
            return false;
        }
        String cmd = raw.trim().toLowerCase(Locale.ROOT);
        return cmd.equals("list") || cmd.equals("minecraft:list");
    }

    /** Same text as the game's /list (translated by the client), with the bots among the players. */
    public Component listText() {
        List<Component> names = new ArrayList<>();
        for (GamePlayer p : host.server().onlinePlayers()) {
            names.add(p.displayName());
        }
        for (Bot b : manager().aliveBots()) {
            names.add(Component.text(b.profileName()));
        }
        int count = names.size();
        int max = host.server().maxPlayers();
        return Component.translatable("commands.list.players", Component.text(count), Component.text(max),
                Component.join(JoinConfiguration.commas(true), names));
    }
}
