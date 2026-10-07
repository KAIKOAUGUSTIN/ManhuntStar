package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.util.Vec3;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Contagem antes da caçada (novo): os bots ficam na roda esperando o jogador sair dela e, a partir
 * daí, contam o tempo de vantagem, com barra no topo, títulos 3-2-1 e som; "/bot start" começa na
 * hora. Com "contagem-ao-sair-da-roda" desligado a contagem começa assim que os bots são criados.
 * Com 0 segundos vale o comportamento do mod (os bots soltam quando o jogador começa a correr).
 */
public final class CountdownService {

    /** Margem além do raio da roda para contar como "saiu". */
    private static final double LEAVE_MARGIN = 1.5;
    /** Ticks seguidos fora da roda antes de a contagem começar (evita disparo por um empurrão). */
    private static final int OUTSIDE_TICKS = 4;

    private enum Phase { OFF, WAITING, COUNTING }

    private final ManhuntStarPlugin plugin;
    private Phase phase = Phase.OFF;
    private int ticksLeft;
    private int totalTicks;
    private UUID worldId;
    private UUID creatorId;
    private Vec3 center;
    private double leaveRadius;
    private int outsideTicks;
    private BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();

    public CountdownService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    /** A caçada ainda não começou: os bots da roda esperam o jogador sair ou o tempo acabar. */
    public boolean isActive() {
        return phase != Phase.OFF;
    }

    /** O tempo de vantagem está correndo. */
    public boolean isCounting() {
        return phase == Phase.COUNTING;
    }

    /** Só os bots da roda ficam presos; bots que já estavam caçando seguem normalmente. */
    public boolean holds(Bot bot) {
        return isActive() && bot.idle() != null && bot.idle().isActive();
    }

    public int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }

    /**
     * Prepara a contagem para uma roda nova (substitui a anterior).
     *
     * @param center centro da roda
     * @param ringRadius raio em que os bots nasceram
     */
    public void start(Player creator, Location center, double ringRadius, int seconds) {
        if (seconds <= 0) {
            return;
        }
        totalTicks = seconds * 20;
        ticksLeft = totalTicks;
        worldId = center.getWorld().getUID();
        creatorId = creator.getUniqueId();
        this.center = Vec3.of(center);
        leaveRadius = ringRadius + LEAVE_MARGIN;
        outsideTicks = 0;
        if (bar == null) {
            bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        }
        Messages m = plugin.messages();
        if (plugin.settings().countdownOnLeave.on()) {
            phase = Phase.WAITING;
            updateBar(m);
            Bukkit.broadcast(m.prefixed("contagem.esperando", Messages.p("jogador", creator.getName()), Messages.p("segundos", seconds)));
        } else {
            phase = Phase.COUNTING;
            updateBar(m);
            Bukkit.broadcast(m.prefixed("contagem.iniciada", Messages.p("segundos", seconds)));
        }
    }

    /** /bot start */
    public boolean finishNow() {
        if (!isActive()) {
            return false;
        }
        finish();
        return true;
    }

    public void tick() {
        if (phase == Phase.OFF) {
            return;
        }
        if (Bukkit.getCurrentTick() % 20 == 0 && !hasRingBots()) {
            // os bots da roda foram removidos: não há o que soltar
            cancel();
            return;
        }
        if (phase == Phase.WAITING) {
            tickWaiting();
        } else {
            tickCounting();
        }
    }

    private void tickWaiting() {
        Player creator = Bukkit.getPlayer(creatorId);
        if (creator == null) {
            return;
        }
        boolean outside = !creator.getWorld().getUID().equals(worldId)
                || (!creator.isDead() && Vec3.of(creator.getLocation()).distanceTo(center) > leaveRadius);
        outsideTicks = outside ? outsideTicks + 1 : 0;
        if (outsideTicks >= OUTSIDE_TICKS) {
            phase = Phase.COUNTING;
            Messages m = plugin.messages();
            updateBar(m);
            Bukkit.broadcast(m.prefixed("contagem.saiu-e-contando", Messages.p("jogador", creator.getName()), Messages.p("segundos", secondsLeft())));
        } else if (Bukkit.getCurrentTick() % 20 == 0) {
            // atualiza quem vê a barra (jogadores que entraram ou trocaram de mundo)
            updateBar(plugin.messages());
        }
    }

    private void tickCounting() {
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
        if (ticksLeft <= 0) {
            finish();
        }
    }

    private boolean hasRingBots() {
        for (Bot bot : plugin.botManager().allBots()) {
            if (bot.idle() != null && bot.idle().isActive()) {
                return true;
            }
        }
        return false;
    }

    private void finish() {
        phase = Phase.OFF;
        ticksLeft = 0;
        hideBar();
        Messages m = plugin.messages();
        showTitle(m.get("contagem.comecou-titulo"), m.get("contagem.comecou-subtitulo"), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f);
        Bukkit.broadcast(m.prefixed("contagem.comecou"));
        for (Bot bot : plugin.botManager().allBots()) {
            if (bot.idle() != null) {
                bot.idle().release();
            }
        }
    }

    public void cancel() {
        phase = Phase.OFF;
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
        if (phase == Phase.WAITING) {
            bar.name(m.get("contagem.barra-esperando", Messages.p("segundos", secondsLeft())));
            bar.color(BossBar.Color.YELLOW);
            bar.progress(1f);
        } else {
            bar.name(m.get("contagem.barra", Messages.p("segundos", secondsLeft())));
            bar.color(BossBar.Color.RED);
            bar.progress(Math.max(0f, Math.min(1f, ticksLeft / (float) Math.max(1, totalTicks))));
        }
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
