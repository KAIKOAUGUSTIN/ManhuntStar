package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.util.Freeze;
import dev.manhuntstar.util.Vec3;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * Hunt pause (/manhunt pause): each bot stays still where it is, even mid-air, mounted or in a boat,
 * invulnerable and not thinking, until /manhunt resume. The ring countdowns, the phase timers and the
 * hunt clock (respawn and late-joiner protection) stop with it. The pause is saved with the bots.
 */
public final class PauseService {

    private final ManhuntHost plugin;
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private boolean paused;
    private int pausedAt;
    private long pausedTicks;

    /** The bot and, if it is mounted, the horse or boat under it. */
    private record Frozen(Freeze body, Freeze mount) {
        void hold() {
            if (mount != null) {
                mount.hold();
            }
            body.hold();
        }

        void release() {
            if (mount != null) {
                mount.release();
            }
            body.release();
        }
    }

    public PauseService(ManhuntHost plugin) {
        this.plugin = plugin;
    }

    public boolean isPaused() {
        return paused;
    }

    /** Hunt clock: the server ticks without the paused time. */
    public long clock() {
        return (paused ? pausedAt : Platform.server().currentTick()) - pausedTicks;
    }

    /** @return false if it was already paused */
    public boolean pause() {
        if (paused) {
            return false;
        }
        paused = true;
        pausedAt = Platform.server().currentTick();
        updateBar();
        plugin.botManager().markDirty();
        return true;
    }

    /** @return false if it was not paused */
    public boolean resume() {
        if (!paused) {
            return false;
        }
        pausedTicks += Platform.server().currentTick() - pausedAt;
        paused = false;
        releaseAll();
        hideBar();
        plugin.botManager().markDirty();
        return true;
    }

    /** Every paused tick: holds the bot (and what it rides) where it was when the pause caught it. */
    public void hold(Bot bot) {
        GameEntity body = bot.entity();
        GameEntity root = body;
        while (root.vehicle() != null) {
            root = root.vehicle();
        }
        Frozen f = frozen.get(bot.id());
        GameEntity mount = root == body ? null : root;
        if (f == null || f.body().entity() != body || (f.mount() == null ? mount != null : f.mount().entity() != mount)) {
            if (f != null) {
                f.release();
            }
            f = new Frozen(Freeze.capture(body, false), mount == null ? null : Freeze.capture(mount, true));
            frozen.put(bot.id(), f);
        }
        f.hold();
    }

    /**
     * The bot was moved on purpose during the pause (/manhunt tp, menu): freezes it again where it is.
     * Without this the freeze would put it back in the old place on the next tick.
     */
    public void refreeze(Bot bot) {
        Frozen f = frozen.remove(bot.id());
        if (f != null) {
            f.release();
        }
        if (paused && bot.isAlive()) {
            // arrives standing still, as after any teleport
            bot.entity().setVelocity(Vec3.ZERO);
            bot.entity().setFallDistance(0);
            hold(bot);
        }
    }

    /** End of the paused tick: releases whoever stopped being a living bot (removed or /kill) and updates the bar. */
    public void tick() {
        Iterator<Map.Entry<UUID, Frozen>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> e = it.next();
            Bot b = plugin.botManager().bot(e.getKey());
            if (b == null || !b.isAlive() || b.entity() != e.getValue().body().entity()) {
                e.getValue().release();
                it.remove();
            }
        }
        if (Platform.server().currentTick() % 20 == 0) {
            updateBar();
        }
    }

    /** Releases everyone (end of the pause, delete all or server shutdown). */
    public void releaseAll() {
        for (Frozen f : frozen.values()) {
            f.release();
        }
        frozen.clear();
    }

    /** Server shutdown: releases the entities and removes the bar, but the pause goes on (it stays saved). */
    public void shutdown() {
        releaseAll();
        hideBar();
    }

    /** Delete all: the pause ends together with the hunt, without a message. */
    public void reset() {
        if (paused) {
            pausedTicks += Platform.server().currentTick() - pausedAt;
            paused = false;
        }
        releaseAll();
        hideBar();
    }

    // ------------------------------------------------------------------ bar

    private void updateBar() {
        bar.name(plugin.messages().get("pause.bar"));
        Set<UUID> now = new HashSet<>();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            now.add(p.uuid());
            if (viewers.add(p.uuid())) {
                p.showBossBar(bar);
            }
        }
        viewers.retainAll(now);
    }

    private void hideBar() {
        for (UUID id : viewers) {
            GamePlayer p = Platform.server().player(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
    }
}
