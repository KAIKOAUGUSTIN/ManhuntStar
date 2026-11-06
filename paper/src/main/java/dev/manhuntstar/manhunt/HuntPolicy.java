package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Platform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Who the bots may hunt: the player list (allowed or blocked) and late joiners.
 * The hunt starts when a ring releases the bots (or when bots spawn without a ring) and ends when no
 * bot is left. Whoever is online at that moment takes part; whoever joins later is hunted right away,
 * protected for the ring's countdown time (at least 5 s) or left out until the next hunt, depending
 * on the "late-join" option. A participant who leaves and comes back is still hunted. A hunt that
 * started with nobody online treats the first one to join as a participant. The protection counts on
 * the hunt clock, which stops during the pause.
 */
public final class HuntPolicy {

    /** Minimum protection for someone who just joined, even with a countdown of 0. */
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

    /** Protection time for late joiners: the ring's countdown, at least 5 s. */
    public static int protectionFor(int countdownSeconds) {
        return Math.max(MIN_PROTECTION_SECONDS, countdownSeconds);
    }

    public boolean isActive() {
        return active;
    }

    /** Starts a new hunt (a ring released the bots): whoever is online takes part. */
    public void begin(int countdownSeconds) {
        active = true;
        protectionSeconds = protectionFor(countdownSeconds);
        participants.clear();
        excluded.clear();
        protectedUntil.clear();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            participants.add(p.uuid());
        }
        plugin.botManager().markDirty();
    }

    /** Bots created without a ring: starts a hunt only if there is none yet. */
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

    /** Does the player list allow hunting this player (and can they trigger the ring)? */
    public boolean listAllows(GamePlayer p) {
        return plugin.settings().listAllows(p.name());
    }

    /** Can be a target per the list and the late-join rule (alive and spectator are checked by BotManager). */
    public boolean canBeHunted(GamePlayer p) {
        if (!listAllows(p)) {
            return false;
        }
        if (!active) {
            return true;
        }
        UUID id = p.uuid();
        // left out until the next hunt, or not admitted yet
        if (!participants.contains(id)) {
            return false;
        }
        Long until = protectedUntil.get(id);
        return until == null || until <= clock();
    }

    /** A player who joined (or became eligible through the list) while the hunt is running. */
    public void admit(GamePlayer p) {
        UUID id = p.uuid();
        if (!active || participants.contains(id) || excluded.contains(id) || !listAllows(p)) {
            return;
        }
        if (participants.isEmpty()) {
            // the hunt started with nobody online (bots created from the console): whoever arrives is the first target
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

    /** Every second: ends the hunt with no bots, admits whoever became eligible and shows the protection. */
    public void tick() {
        if (Platform.server().currentTick() % 20 != 0 || !active) {
            return;
        }
        long now = clock();
        if (plugin.botManager().allBots().isEmpty()) {
            end();
            return;
        }
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            admit(p);
        }
        Messages m = plugin.messages();
        Iterator<Map.Entry<UUID, Long>> it = protectedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            GamePlayer p = Platform.server().player(e.getKey());
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

    // ------------------------------------------------------------------ saving

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
                    // broken line in bots.yml
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
                // broken line in bots.yml
            }
        }
    }
}
