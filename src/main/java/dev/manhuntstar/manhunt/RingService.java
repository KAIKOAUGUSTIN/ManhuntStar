package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.ai.IdleBehavior;
import dev.manhuntstar.config.Messages;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Rodas de bots antes da caçada. A roda não tem dono: o primeiro jogador que estava dentro dela e
 * sai começa a contagem de vantagem (barra no topo, títulos 3-2-1 e som), e no fim os bots dessa roda
 * são soltos. Roda criada longe de todos espera alguém entrar e sair, ou o /manhunt start. Com
 * "contagem-ao-sair-da-roda" desligado a contagem começa assim que os bots nascem; com 0 segundos
 * os bots soltam assim que alguém sai. Cada roda tem a sua contagem; bots fora de roda seguem caçando.
 */
public final class RingService {

    /** Margem além do raio da roda para contar como "dentro" e como "saiu". */
    private static final double LEAVE_MARGIN = 1.5;

    private final ManhuntStarPlugin plugin;
    private final Map<UUID, Ring> rings = new LinkedHashMap<>();

    public RingService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private BotManager manager() {
        return plugin.botManager();
    }

    /** Há alguma roda esperando ou contando. */
    public boolean isActive() {
        return !rings.isEmpty();
    }

    public int count() {
        return rings.size();
    }

    public Ring ring(UUID id) {
        return id == null ? null : rings.get(id);
    }

    /** O bot está preso numa roda (esperando ou na contagem). */
    public boolean holds(Bot bot) {
        return bot.idle() != null && bot.idle().isActive();
    }

    /** A roda deste bot já está na contagem. */
    public boolean isCounting(Bot bot) {
        Ring r = bot.idle() == null ? null : rings.get(bot.idle().ringId());
        return r != null && r.isCounting();
    }

    /** Pode disparar a roda: online, fora do espectador e liberado pela lista de jogadores. */
    private boolean canTrigger(Player p) {
        return p.isOnline() && p.getGameMode() != GameMode.SPECTATOR && manager().policy().listAllows(p);
    }

    /**
     * Prende os bots recém-criados numa roda nova.
     *
     * @return false se não há roda (contagem 0 com "contagem-ao-sair-da-roda" desligado: já caçam)
     */
    public boolean create(Location center, double radius, List<Bot> bots, int seconds) {
        boolean onLeave = plugin.settings().countdownOnLeave.on();
        if (bots.isEmpty() || (!onLeave && seconds <= 0)) {
            return false;
        }
        Ring r = new Ring(UUID.randomUUID(), center.getWorld().getUID(), Vec3.of(center), radius, seconds, radius + LEAVE_MARGIN);
        for (Bot b : bots) {
            b.setIdle(new IdleBehavior(b, r.id));
            r.bots.add(b.id());
        }
        rings.put(r.id, r);
        Messages m = plugin.messages();
        if (onLeave) {
            // quem já está dentro conta como dentro (sair dispara a contagem)
            for (Player p : center.getWorld().getPlayers()) {
                if (canTrigger(p) && !p.isDead()) {
                    r.exit.observe(p.getUniqueId(), distance(p, r));
                }
            }
            Bukkit.broadcast(seconds > 0
                    ? m.prefixed("contagem.roda-criada", Messages.p("segundos", seconds))
                    : m.prefixed("contagem.roda-criada-sem-contagem"));
        } else {
            r.phase = Ring.Phase.COUNTING;
            Bukkit.broadcast(m.prefixed("contagem.iniciada", Messages.p("segundos", seconds)));
        }
        updateBar(r);
        manager().markDirty();
        return true;
    }

    /** /manhunt start: solta todas as rodas na hora. @return quantos bots foram soltos */
    public int releaseAll() {
        int n = 0;
        for (Ring r : new ArrayList<>(rings.values())) {
            n += r.bots.size();
            release(r);
        }
        return n;
    }

    /** Apagar tudo ou desligar o servidor: some com as rodas sem soltar ninguém. */
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
                // os bots da roda foram removidos (ou renasceram): não há o que soltar
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

    /** Tira da roda os bots que não esperam mais nela. @return true se não sobrou nenhum */
    private boolean prune(Ring r) {
        r.bots.removeIf(id -> {
            Bot b = manager().bot(id);
            return b == null || b.idle() == null || !b.idle().isActive() || !r.id.equals(b.idle().ringId());
        });
        return r.bots.isEmpty();
    }

    private void tickWaiting(Ring r, boolean second) {
        World w = Bukkit.getWorld(r.worldId);
        // quem estava dentro e foi para outro mundo saiu da roda; quem saiu do servidor, virou
        // espectador ou foi barrado pela lista deixa de contar
        for (UUID id : r.exit.inside()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !canTrigger(p)) {
                r.exit.forget(id);
            } else if (w == null || !p.getWorld().equals(w)) {
                trigger(r, p);
                return;
            }
        }
        if (w == null) {
            return;
        }
        for (Player p : w.getPlayers()) {
            // morto não conta; se renascer longe da roda, conta como saída
            if (p.isDead() || !canTrigger(p)) {
                continue;
            }
            if (r.exit.observe(p.getUniqueId(), distance(p, r))) {
                trigger(r, p);
                return;
            }
        }
        if (second) {
            // atualiza quem vê a barra (jogadores que entraram ou trocaram de mundo)
            updateBar(r);
        }
    }

    private void trigger(Ring r, Player who) {
        Messages m = plugin.messages();
        if (r.seconds <= 0) {
            Bukkit.broadcast(m.prefixed("contagem.saiu", Messages.p("jogador", who.getName())));
            release(r);
            return;
        }
        r.phase = Ring.Phase.COUNTING;
        r.ticksLeft = r.seconds * 20;
        updateBar(r);
        Bukkit.broadcast(m.prefixed("contagem.saiu-e-contando", Messages.p("jogador", who.getName()), Messages.p("segundos", r.seconds)));
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
                    showTitle(r, m.get("contagem.titulo-numero", Messages.p("segundos", s)), m.get("contagem.subtitulo"), Sound.BLOCK_NOTE_BLOCK_PLING);
                } else if (s == 10) {
                    for (Player p : audience(r)) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1f, 1f);
                    }
                }
            }
        }
        if (r.ticksLeft <= 0) {
            release(r);
        }
    }

    /** Fim da contagem: solta os bots da roda e começa uma caçada nova. */
    private void release(Ring r) {
        rings.remove(r.id);
        hideBar(r);
        Messages m = plugin.messages();
        showTitle(r, m.get("contagem.comecou-titulo"), m.get("contagem.comecou-subtitulo"), Sound.ENTITY_ENDER_DRAGON_GROWL);
        Bukkit.broadcast(m.prefixed("contagem.comecou"));
        for (UUID id : r.bots) {
            Bot b = manager().bot(id);
            if (b != null && b.idle() != null) {
                b.idle().release();
            }
        }
        manager().policy().begin(r.seconds);
        manager().markDirty();
    }

    private static double distance(Player p, Ring r) {
        return Vec3.of(p.getLocation()).distanceTo(r.center);
    }

    // ------------------------------------------------------------------ barra e títulos

    private Iterable<Player> audience(Ring r) {
        World w = Bukkit.getWorld(r.worldId);
        return w == null ? List.copyOf(Bukkit.getOnlinePlayers()) : w.getPlayers();
    }

    private void showTitle(Ring r, Component title, Component subtitle, Sound sound) {
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(300)));
        for (Player p : audience(r)) {
            p.showTitle(t);
            p.playSound(p.getLocation(), sound, SoundCategory.MASTER, 1f, 1f);
        }
    }

    private void updateBar(Ring r) {
        Messages m = plugin.messages();
        if (r.phase == Ring.Phase.WAITING) {
            r.bar.name(r.seconds > 0 ? m.get("contagem.barra-esperando", Messages.p("segundos", r.seconds)) : m.get("contagem.barra-esperando-sem-contagem"));
            r.bar.color(BossBar.Color.YELLOW);
            r.bar.progress(1f);
        } else {
            r.bar.name(m.get("contagem.barra", Messages.p("segundos", r.secondsLeft())));
            r.bar.color(BossBar.Color.RED);
            r.bar.progress(Math.max(0f, Math.min(1f, r.ticksLeft / (float) Math.max(1, r.seconds * 20))));
        }
        Set<UUID> now = new HashSet<>();
        for (Player p : audience(r)) {
            now.add(p.getUniqueId());
            if (r.viewers.add(p.getUniqueId())) {
                p.showBossBar(r.bar);
            }
        }
        r.viewers.removeIf(id -> {
            if (now.contains(id)) {
                return false;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(r.bar);
            }
            return true;
        });
    }

    private void hideBar(Ring r) {
        for (UUID id : r.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(r.bar);
            }
        }
        r.viewers.clear();
    }

    // ------------------------------------------------------------------ salvamento

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

    /** Recria as rodas salvas e prende de novo os bots delas (depois de os bots serem restaurados). */
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
                if (b != null && b.isAlive()) {
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
                // linha estragada no bots.yml
            }
        }
        return out;
    }
}
