package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.config.Messages;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Contagem antes da caçada (novo): os bots ficam na roda até o tempo acabar, com barra no topo,
 * títulos 3-2-1 e som; "/bot start" começa na hora. Com 0 segundos vale o comportamento do mod
 * (os bots soltam quando o jogador começa a correr).
 */
public final class CountdownService {

    private final ManhuntStarPlugin plugin;
    private int ticksLeft;
    private int totalTicks;
    private UUID worldId;
    private BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();

    public CountdownService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return ticksLeft > 0;
    }

    /** Durante a contagem nenhum bot persegue ou ataca. */
    public boolean holds(Bot bot) {
        return isRunning();
    }

    public int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }

    /** Começa (ou reinicia) a contagem no mundo do jogador. */
    public void start(Player creator, int seconds) {
        if (seconds <= 0) {
            return;
        }
        totalTicks = seconds * 20;
        ticksLeft = totalTicks;
        worldId = creator.getWorld().getUID();
        Messages m = plugin.messages();
        if (bar == null) {
            bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        }
        updateBar(m);
        Bukkit.broadcast(m.prefixed("contagem.iniciada", Messages.p("segundos", seconds)));
    }

    /** O fugitivo saiu da roda (com "liberar-ao-sair-da-roda" ligado). */
    public void finishEarly(Player who) {
        if (!isRunning()) {
            return;
        }
        Bukkit.broadcast(plugin.messages().prefixed("contagem.saiu-da-roda", Messages.p("jogador", who.getName())));
        finish();
    }

    /** /bot start */
    public boolean finishNow() {
        if (!isRunning()) {
            return false;
        }
        finish();
        return true;
    }

    public void tick() {
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        Messages m = plugin.messages();
        if (ticksLeft % 20 == 0) {
            int s = ticksLeft / 20;
            if (s > 0) {
                updateBar(m);
                if (s <= 3) {
                    showTitle(m.get("contagem.titulo-numero", Messages.p("segundos", s)), m.get("contagem.subtitulo"), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
                } else if (s == 10) {
                    for (Player p : audience()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1f, 1f);
                    }
                }
            }
        }
        if (ticksLeft == 0) {
            finish();
        }
    }

    private void finish() {
        ticksLeft = 0;
        hideBar();
        Messages m = plugin.messages();
        showTitle(m.get("contagem.comecou-titulo"), m.get("contagem.comecou-subtitulo"), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f);
        Bukkit.broadcast(m.prefixed("contagem.comecou"));
        for (Bot bot : plugin.botManager().aliveBots()) {
            if (bot.idle() != null) {
                bot.idle().release();
            }
        }
    }

    public void cancel() {
        ticksLeft = 0;
        hideBar();
    }

    private Iterable<Player> audience() {
        World w = worldId == null ? null : Bukkit.getWorld(worldId);
        return w == null ? java.util.List.copyOf(Bukkit.getOnlinePlayers()) : w.getPlayers();
    }

    private void showTitle(Component title, Component subtitle, Sound sound, float pitch) {
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(300)));
        for (Player p : audience()) {
            p.showTitle(t);
            p.playSound(p.getLocation(), sound, SoundCategory.MASTER, 1f, pitch);
        }
    }

    private void updateBar(Messages m) {
        if (bar == null) {
            return;
        }
        bar.name(m.get("contagem.barra", Messages.p("segundos", secondsLeft())));
        bar.progress(Math.max(0f, Math.min(1f, ticksLeft / (float) Math.max(1, totalTicks))));
        Set<UUID> now = new HashSet<>();
        for (Player p : audience()) {
            now.add(p.getUniqueId());
            if (viewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
        viewers.removeIf(id -> {
            if (now.contains(id)) {
                return false;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
            return true;
        });
    }

    private void hideBar() {
        if (bar == null) {
            return;
        }
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
    }
}
