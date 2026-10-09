package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.util.Freeze;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Pausa da caçada (/manhunt pause): cada bot fica parado onde está, até no ar, montado ou de barco,
 * invencível e sem pensar, até o /manhunt resume. Junto param a contagem das rodas, o tempo das fases
 * e o relógio da caçada (renascimento e proteção de quem entrou no meio). A pausa é salva com os bots.
 */
public final class PauseService {

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private boolean paused;
    private int pausedAt;
    private long pausedTicks;

    /** O bot e, se ele estiver montado, o cavalo ou barco embaixo dele. */
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

    public PauseService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPaused() {
        return paused;
    }

    /** Relógio da caçada: os ticks do servidor sem contar o tempo pausado. */
    public long clock() {
        return (paused ? pausedAt : Bukkit.getCurrentTick()) - pausedTicks;
    }

    /** @return false se já estava pausada */
    public boolean pause() {
        if (paused) {
            return false;
        }
        paused = true;
        pausedAt = Bukkit.getCurrentTick();
        updateBar();
        plugin.botManager().markDirty();
        return true;
    }

    /** @return false se não estava pausada */
    public boolean resume() {
        if (!paused) {
            return false;
        }
        pausedTicks += Bukkit.getCurrentTick() - pausedAt;
        paused = false;
        releaseAll();
        hideBar();
        plugin.botManager().markDirty();
        return true;
    }

    /** A cada tick pausado: prende o bot (e o que ele monta) onde ele estava quando a pausa o pegou. */
    public void hold(Bot bot) {
        Entity body = bot.entity();
        Entity root = body;
        while (root.getVehicle() != null) {
            root = root.getVehicle();
        }
        Frozen f = frozen.get(bot.id());
        Entity mount = root == body ? null : root;
        if (f == null || f.body().entity() != body || (f.mount() == null ? mount != null : f.mount().entity() != mount)) {
            if (f != null) {
                f.release();
            }
            f = new Frozen(Freeze.capture(body, false), mount == null ? null : Freeze.capture(mount, true));
            frozen.put(bot.id(), f);
        }
        f.hold();
    }

    /** Fim do tick pausado: solta quem deixou de ser bot vivo (removido ou /kill) e atualiza a barra. */
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
        if (Bukkit.getCurrentTick() % 20 == 0) {
            updateBar();
        }
    }

    /** Solta todos (fim da pausa, apagar tudo ou desligar o servidor). */
    public void releaseAll() {
        for (Frozen f : frozen.values()) {
            f.release();
        }
        frozen.clear();
    }

    /** Desligar o servidor: solta as entidades e tira a barra, mas a pausa continua (fica salva). */
    public void shutdown() {
        releaseAll();
        hideBar();
    }

    /** Apagar tudo: a pausa acaba junto com a caçada, sem aviso. */
    public void reset() {
        if (paused) {
            pausedTicks += Bukkit.getCurrentTick() - pausedAt;
            paused = false;
        }
        releaseAll();
        hideBar();
    }

    // ------------------------------------------------------------------ barra

    private void updateBar() {
        bar.name(plugin.messages().get("pausa.barra"));
        Set<UUID> now = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            now.add(p.getUniqueId());
            if (viewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
        viewers.retainAll(now);
    }

    private void hideBar() {
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
    }
}
