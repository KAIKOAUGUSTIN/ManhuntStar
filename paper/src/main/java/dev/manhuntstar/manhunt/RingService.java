package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.ai.IdleBehavior;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.GameMode;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Vec3;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Bot rings before the hunt. A ring has no owner: the first player who was inside it and
 * leaves starts the head-start countdown (bar at the top, 3-2-1 titles and sound), and at the end that
 * ring's bots are released. A ring created far from everyone waits for someone to go in and out, or for
 * /manhunt start. With {@code manhunt.countdown-on-ring-leave} off the countdown starts as soon as the bots spawn; with 0
 * seconds the bots are released as soon as someone leaves. Each ring has its own countdown; bots outside a
 * ring keep hunting.
 */
public final class RingService {

    /** Margin beyond the ring's radius to count as "inside" and as "left". */
    private static final double LEAVE_MARGIN = 1.5;

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, Ring> rings = new LinkedHashMap<>();

    public RingService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private BotManager manager() {
        return plugin.botManager();
    }

    /** There is some ring waiting or counting down. */
    public boolean isActive() {
        return !rings.isEmpty();
    }

    public int count() {
        return rings.size();
    }

    public Ring ring(UUID id) {
        return id == null ? null : rings.get(id);
    }

    /** The bot is held in a ring (waiting or in the countdown). */
    public boolean holds(Bot bot) {
        return bot.idle() != null && bot.idle().isActive();
    }

    /** This bot's ring is already counting down. */
    public boolean isCounting(Bot bot) {
        Ring r = bot.idle() == null ? null : rings.get(bot.idle().ringId());
        return r != null && r.isCounting();
    }

    /** Can set off the ring: online, not a spectator and allowed by the player list. */
    private boolean canTrigger(GamePlayer p) {
        return p.isOnline() && p.gameMode() != GameMode.SPECTATOR && manager().policy().listAllows(p);
    }

    /**
     * Holds the newly created bots in a new ring.
     *
     * @return false if there is no ring (countdown 0 with {@code manhunt.countdown-on-ring-leave} off: they hunt right away)
     */
    public boolean create(Location center, double radius, List<Bot> bots, int seconds) {
        boolean onLeave = plugin.settings().countdownOnLeave.on();
        if (bots.isEmpty() || (!onLeave && seconds <= 0)) {
            return false;
        }
        Ring r = new Ring(UUID.randomUUID(), center.getWorld().getUID(), Locations.vec(center), radius, seconds, radius + LEAVE_MARGIN);
        for (Bot b : bots) {
            b.setIdle(new IdleBehavior(b, r.id));
            r.bots.add(b.id());
        }
        rings.put(r.id, r);
        Messages m = plugin.messages();
        if (onLeave) {
            // players already inside count as inside (leaving starts the countdown)
            for (GamePlayer p : PaperWorld.of(center.getWorld()).players()) {
                if (canTrigger(p) && !p.isDead()) {
                    r.exit.observe(p.uuid(), distance(p, r));
                }
            }
            Bukkit.broadcast(seconds > 0
                    ? m.prefixed("countdown.ring-created", Messages.p("seconds", seconds))
                    : m.prefixed("countdown.ring-created-no-countdown"));
        } else {
            r.phase = Ring.Phase.COUNTING;
            Bukkit.broadcast(m.prefixed("countdown.started", Messages.p("seconds", seconds)));
        }
        updateBar(r);
        manager().markDirty();
        return true;
    }

    /** /manhunt start: releases every ring right away. @return how many bots were released */
    public int releaseAll() {
        int n = 0;
        for (Ring r : new ArrayList<>(rings.values())) {
            n += r.bots.size();
            release(r);
        }
        return n;
    }

    /** Clearing everything or shutting down the server: gets rid of the rings without releasing anyone. */
    public void cancelAll() {
        for (Ring r : rings.values()) {
            hideBar(r);
        }
        rings.clear();
    }

    public void tick() {
        if (rings.isEmpty()) {
            return;
        }
        boolean second = Bukkit.getCurrentTick() % 20 == 0;
        for (Ring r : new ArrayList<>(rings.values())) {
            if (second && prune(r)) {
                // the ring's bots were removed (or respawned): there's nothing to release
                rings.remove(r.id);
                hideBar(r);
                manager().markDirty();
                continue;
            }
            if (r.phase == Ring.Phase.WAITING) {
                tickWaiting(r, second);
            } else {
                tickCounting(r);
            }
        }
    }

    /** Takes out of the ring the bots that no longer wait in it. @return true if none are left */
    private boolean prune(Ring r) {
        r.bots.removeIf(id -> {
            Bot b = manager().bot(id);
            return b == null || b.idle() == null || !b.idle().isActive() || !r.id.equals(b.idle().ringId());
        });
        return r.bots.isEmpty();
    }

    private void tickWaiting(Ring r, boolean second) {
        GameWorld w = Platform.server().world(r.worldId);
        // whoever was inside and went to another world left the ring; whoever left the server, became a
        // spectator or was blocked by the list stops counting
        for (UUID id : r.exit.inside()) {
            GamePlayer p = Platform.server().player(id);
            if (p == null || !canTrigger(p)) {
                r.exit.forget(id);
            } else if (w == null || !p.world().equals(w)) {
                trigger(r, p);
                return;
            }
        }
        if (w == null) {
            return;
        }
        for (GamePlayer p : w.players()) {
            // dead doesn't count; if they respawn far from the ring, it counts as leaving
            if (p.isDead() || !canTrigger(p)) {
                continue;
            }
            if (r.exit.observe(p.uuid(), distance(p, r))) {
                trigger(r, p);
                return;
            }
        }
        if (second) {
            // updates who sees the bar (players who joined or changed worlds)
            updateBar(r);
        }
    }

    private void trigger(Ring r, GamePlayer who) {
        Messages m = plugin.messages();
        if (r.seconds <= 0) {
            Bukkit.broadcast(m.prefixed("countdown.left", Messages.p("player", who.name())));
            release(r);
            return;
        }
        r.phase = Ring.Phase.COUNTING;
        r.ticksLeft = r.seconds * 20;
        updateBar(r);
        Bukkit.broadcast(m.prefixed("countdown.left-and-counting", Messages.p("player", who.name()), Messages.p("seconds", r.seconds)));
        manager().markDirty();
    }

    private void tickCounting(Ring r) {
        r.ticksLeft--;
        Messages m = plugin.messages();
        if (r.ticksLeft % 20 == 0) {
            int s = r.ticksLeft / 20;
            if (s > 0) {
                updateBar(r);
                if (s <= 3) {
                    showTitle(r, m.get("countdown.title-number", Messages.p("seconds", s)), m.get("countdown.subtitle"), Sounds.BLOCK_NOTE_BLOCK_PLING);
                } else if (s == 10) {
                    for (GamePlayer p : audience(r)) {
                        p.playSound(p.position(), Sounds.BLOCK_NOTE_BLOCK_HAT, dev.manhuntstar.platform.SoundCategory.MASTER, 1f, 1f);
                    }
                }
            }
        }
        if (r.ticksLeft <= 0) {
            release(r);
        }
    }

    /** End of the countdown: releases the ring's bots and starts a new hunt. */
    private void release(Ring r) {
        rings.remove(r.id);
        hideBar(r);
        Messages m = plugin.messages();
        showTitle(r, m.get("countdown.begun-title"), m.get("countdown.begun-subtitle"), Sounds.ENTITY_ENDER_DRAGON_GROWL);
        Bukkit.broadcast(m.prefixed("countdown.begun"));
        for (UUID id : r.bots) {
            Bot b = manager().bot(id);
            if (b != null && b.idle() != null) {
                b.idle().release();
            }
        }
        manager().policy().begin(r.seconds);
        manager().markDirty();
    }

    private static double distance(GamePlayer p, Ring r) {
        return p.position().distanceTo(r.center);
    }

    // ------------------------------------------------------------------ bar and titles

    private Iterable<? extends GamePlayer> audience(Ring r) {
        GameWorld w = Platform.server().world(r.worldId);
        return w == null ? List.copyOf(Platform.server().onlinePlayers()) : w.players();
    }

    private void showTitle(Ring r, Component title, Component subtitle, GameSound sound) {
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(300)));
        for (GamePlayer p : audience(r)) {
            p.showTitle(t);
            p.playSound(p.position(), sound, dev.manhuntstar.platform.SoundCategory.MASTER, 1f, 1f);
        }
    }

    private void updateBar(Ring r) {
        Messages m = plugin.messages();
        if (r.phase == Ring.Phase.WAITING) {
            r.bar.name(r.seconds > 0 ? m.get("countdown.bar-waiting", Messages.p("seconds", r.seconds)) : m.get("countdown.bar-waiting-no-countdown"));
            r.bar.color(BossBar.Color.YELLOW);
            r.bar.progress(1f);
        } else {
            r.bar.name(m.get("countdown.bar", Messages.p("seconds", r.secondsLeft())));
            r.bar.color(BossBar.Color.RED);
            r.bar.progress(Math.max(0f, Math.min(1f, r.ticksLeft / (float) Math.max(1, r.seconds * 20))));
        }
        Set<UUID> now = new HashSet<>();
        for (GamePlayer p : audience(r)) {
            now.add(p.uuid());
            if (r.viewers.add(p.uuid())) {
                p.showBossBar(r.bar);
            }
        }
        r.viewers.removeIf(id -> {
            if (now.contains(id)) {
                return false;
            }
            GamePlayer p = Platform.server().player(id);
            if (p != null) {
                p.hideBossBar(r.bar);
            }
            return true;
        });
    }

    private void hideBar(Ring r) {
        for (UUID id : r.viewers) {
            GamePlayer p = Platform.server().player(id);
            if (p != null) {
                p.hideBossBar(r.bar);
            }
        }
        r.viewers.clear();
    }

    // ------------------------------------------------------------------ saving

    public void save(ConfigurationSection s) {
        for (Ring r : rings.values()) {
            World w = Bukkit.getWorld(r.worldId);
            if (w == null) {
                continue;
            }
            ConfigurationSection c = s.createSection(r.id.toString());
            c.set("mundo", w.getName());
            c.set("x", r.center.x());
            c.set("y", r.center.y());
            c.set("z", r.center.z());
            c.set("raio", r.radius);
            c.set("segundos", r.seconds);
            c.set("contando", r.isCounting());
            c.set("ticks-restantes", r.ticksLeft);
            c.set("bots", ids(r.bots));
            c.set("dentro", ids(r.exit.inside()));
        }
    }

    /** Recreates the saved rings and holds their bots again (after the bots are restored). */
    public void load(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            World w = c == null ? null : Bukkit.getWorld(c.getString("mundo", ""));
            if (w == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            double radius = c.getDouble("raio", 5.0);
            Ring r = new Ring(id, w.getUID(), new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")), radius,
                    c.getInt("segundos"), radius + LEAVE_MARGIN);
            for (UUID botId : readIds(c.getStringList("bots"))) {
                Bot b = manager().bot(botId);
                // dead at shutdown also goes back to the ring when it respawns
                if (b != null && (b.isAlive() || manager().isPendingRespawn(b))) {
                    b.setIdle(new IdleBehavior(b, r.id));
                    r.bots.add(botId);
                }
            }
            if (r.bots.isEmpty()) {
                continue;
            }
            r.exit.restoreInside(readIds(c.getStringList("dentro")));
            if (c.getBoolean("contando")) {
                r.phase = Ring.Phase.COUNTING;
                r.ticksLeft = Math.max(1, c.getInt("ticks-restantes"));
            }
            rings.put(r.id, r);
            updateBar(r);
        }
    }

    private static List<String> ids(Set<UUID> set) {
        List<String> out = new ArrayList<>(set.size());
        for (UUID id : set) {
            out.add(id.toString());
        }
        return out;
    }

    private static List<UUID> readIds(List<String> raw) {
        List<UUID> out = new ArrayList<>(raw.size());
        for (String r : raw) {
            try {
                out.add(UUID.fromString(r));
            } catch (IllegalArgumentException ignored) {
                // broken line in bots.yml
            }
        }
        return out;
    }
}
