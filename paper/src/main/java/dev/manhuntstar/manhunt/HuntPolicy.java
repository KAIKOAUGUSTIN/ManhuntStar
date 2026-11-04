package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Quem os bots podem caçar: a lista de jogadores (permitidos ou bloqueados) e quem entrou no meio.
 * A caçada começa quando uma roda solta os bots (ou quando bots nascem sem roda) e acaba quando não
 * sobra nenhum bot. Quem está online nessa hora participa; quem entra depois é caçado na hora, fica
 * protegido pelo tempo da contagem da roda (no mínimo 5 s) ou fica de fora até a próxima caçada,
 * conforme a opção "quem-entra-no-meio". Quem participa, cai e volta continua sendo caçado. Caçada
 * que começou sem ninguém online trata o primeiro a entrar como participante. A proteção conta no
 * relógio da caçada, que para durante a pausa.
 */
public final class HuntPolicy {

    /** Proteção mínima de quem acabou de entrar, mesmo com contagem 0. */
    public static final int MIN_PROTECTION_SECONDS = 5;

    private final ManhuntStarPlugin plugin;
    private boolean active;
    private int protectionSeconds = MIN_PROTECTION_SECONDS;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> excluded = new HashSet<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    public HuntPolicy(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    private long clock() {
        return plugin.botManager().pause().clock();
    }

    /** Tempo de proteção de quem entra no meio: o da contagem da roda, no mínimo 5 s. */
    public static int protectionFor(int countdownSeconds) {
        return Math.max(MIN_PROTECTION_SECONDS, countdownSeconds);
    }

    public boolean isActive() {
        return active;
    }

    /** Começa uma caçada nova (uma roda soltou os bots): quem está online participa. */
    public void begin(int countdownSeconds) {
        active = true;
        protectionSeconds = protectionFor(countdownSeconds);
        participants.clear();
        excluded.clear();
        protectedUntil.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            participants.add(p.getUniqueId());
        }
        plugin.botManager().markDirty();
    }

    /** Bots criados sem roda: começa uma caçada só se ainda não houver uma. */
    public void beginIfIdle() {
        if (!active) {
            begin(0);
        }
    }

    public void end() {
        active = false;
        participants.clear();
        excluded.clear();
        protectedUntil.clear();
        plugin.botManager().markDirty();
    }

    /** A lista de jogadores deixa caçar este jogador (e ele pode disparar a roda)? */
    public boolean listAllows(Player p) {
        return plugin.settings().listAllows(p.getName());
    }

    /** Pode ser alvo pela lista e pela regra de quem entra no meio (vivo e espectador o BotManager confere). */
    public boolean canBeHunted(Player p) {
        if (!listAllows(p)) {
            return false;
        }
        if (!active) {
            return true;
        }
        UUID id = p.getUniqueId();
        // de fora até a próxima caçada, ou ainda não admitido
        if (!participants.contains(id)) {
            return false;
        }
        Long until = protectedUntil.get(id);
        return until == null || until <= clock();
    }

    /** Jogador que entrou (ou passou a valer pela lista) com a caçada em andamento. */
    public void admit(Player p) {
        UUID id = p.getUniqueId();
        if (!active || participants.contains(id) || excluded.contains(id) || !listAllows(p)) {
            return;
        }
        if (participants.isEmpty()) {
            // a caçada começou sem ninguém online (bots criados pelo console): quem chega é o primeiro alvo
            participants.add(id);
            plugin.botManager().markDirty();
            return;
        }
        Messages m = plugin.messages();
        switch (plugin.settings().lateJoin.get()) {
            case HUNTED -> {
                participants.add(id);
                m.send(p, "hunt.joined-hunted");
            }
            case PROTECTED -> {
                participants.add(id);
                protectedUntil.put(id, clock() + protectionSeconds * 20L);
                m.send(p, "hunt.joined-protected", Messages.p("seconds", protectionSeconds));
            }
            case OUT -> {
                excluded.add(id);
                m.send(p, "hunt.joined-out");
            }
        }
        plugin.botManager().markDirty();
    }

    /** A cada segundo: encerra a caçada sem bots, admite quem passou a valer e mostra a proteção. */
    public void tick() {
        if (Bukkit.getCurrentTick() % 20 != 0 || !active) {
            return;
        }
        long now = clock();
        if (plugin.botManager().allBots().isEmpty()) {
            end();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            admit(p);
        }
        Messages m = plugin.messages();
        Iterator<Map.Entry<UUID, Long>> it = protectedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            long left = (e.getValue() - now + 19) / 20;
            if (left <= 0) {
                it.remove();
                if (p != null) {
                    p.sendActionBar(m.get("hunt.protection-ended-bar"));
                    m.send(p, "hunt.protection-ended");
                }
            } else if (p != null) {
                p.sendActionBar(m.get("hunt.protection-bar", Messages.p("seconds", left)));
            }
        }
    }

    // ------------------------------------------------------------------ salvamento

    public void save(ConfigurationSection s) {
        s.set("ativa", active);
        if (!active) {
            return;
        }
        s.set("protecao-segundos", protectionSeconds);
        s.set("participantes", ids(participants));
        s.set("fora", ids(excluded));
        long now = clock();
        for (Map.Entry<UUID, Long> e : protectedUntil.entrySet()) {
            long left = e.getValue() - now;
            if (left > 0) {
                s.set("protegidos." + e.getKey(), left);
            }
        }
    }

    public void load(ConfigurationSection s) {
        participants.clear();
        excluded.clear();
        protectedUntil.clear();
        active = s != null && s.getBoolean("ativa");
        if (!active) {
            return;
        }
        protectionSeconds = protectionFor(s.getInt("protecao-segundos"));
        readIds(s.getStringList("participantes"), participants);
        readIds(s.getStringList("fora"), excluded);
        ConfigurationSection prot = s.getConfigurationSection("protegidos");
        if (prot != null) {
            long now = clock();
            for (String k : prot.getKeys(false)) {
                try {
                    protectedUntil.put(UUID.fromString(k), now + prot.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // linha estragada no bots.yml
                }
            }
        }
    }

    private static List<String> ids(Set<UUID> set) {
        List<String> out = new ArrayList<>(set.size());
        for (UUID id : set) {
            out.add(id.toString());
        }
        return out;
    }

    private static void readIds(List<String> raw, Set<UUID> into) {
        for (String r : raw) {
            try {
                into.add(UUID.fromString(r));
            } catch (IllegalArgumentException ignored) {
                // linha estragada no bots.yml
            }
        }
    }
}
