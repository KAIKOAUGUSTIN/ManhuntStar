package dev.manhuntstar.fidelity;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.host.BotGlow;
import dev.manhuntstar.paper.PaperEntities;
import java.util.Collection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mannequin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Glow (an outline visible through walls) on the hunters and, separately, on the runners.
 * The color comes from a scoreboard team that holds only the bots' entities.
 */
public final class GlowService implements BotGlow {

    private static final String HUNTER_TEAM = "mhs_cacadores";
    private static final String ESCAPE_TEAM = "mhs_fugitivos";

    private final Settings settings;

    public GlowService(Settings settings) {
        this.settings = settings;
    }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private Team team(String name) {
        Scoreboard b = board();
        Team t = b.getTeam(name);
        if (t == null) {
            t = b.registerNewTeam(name);
        }
        return t;
    }

    @Override
    public void apply(Bot bot) {
        Mannequin e = PaperEntities.bukkit(bot.entity());
        if (e == null) {
            return;
        }
        BotGroup g = bot.group();
        boolean escape = g != null && g.role() == BotRole.ESCAPE;
        boolean glow = escape ? settings.glowEscapers.on() : settings.glowHunters.on();
        Team hunters = team(HUNTER_TEAM);
        Team escapers = team(ESCAPE_TEAM);
        hunters.color(settings.glowHuntersColor.get().color());
        escapers.color(settings.glowEscapersColor.get().color());
        String entry = e.getUniqueId().toString();
        if (glow) {
            Team want = escape ? escapers : hunters;
            Team other = escape ? hunters : escapers;
            if (other.hasEntry(entry)) {
                other.removeEntry(entry);
            }
            if (!want.hasEntry(entry)) {
                want.addEntry(entry);
            }
        } else {
            hunters.removeEntry(entry);
            escapers.removeEntry(entry);
        }
        e.setGlowing(glow);
    }

    @Override
    public void remove(Bot bot) {
        Mannequin e = PaperEntities.bukkit(bot.entity());
        if (e == null) {
            return;
        }
        String entry = e.getUniqueId().toString();
        Team h = board().getTeam(HUNTER_TEAM);
        Team s = board().getTeam(ESCAPE_TEAM);
        if (h != null) {
            h.removeEntry(entry);
        }
        if (s != null) {
            s.removeEntry(entry);
        }
    }

    @Override
    public void applyAll(Collection<Bot> bots) {
        for (Bot b : bots) {
            apply(b);
        }
    }

    /** On shutdown: removes the teams (the entities are already gone). */
    public void cleanup() {
        for (String name : new String[]{HUNTER_TEAM, ESCAPE_TEAM}) {
            Team t = board().getTeam(name);
            if (t != null) {
                t.unregister();
            }
        }
    }
}
