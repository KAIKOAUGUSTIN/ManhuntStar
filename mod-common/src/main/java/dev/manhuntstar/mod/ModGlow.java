package dev.manhuntstar.mod;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.host.BotGlow;
import java.util.Collection;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/**
 * Glow (an outline visible through walls) on the hunters and, separately, on the runners. The color comes
 * from a scoreboard team that holds only the bots' entities (the same teams as the plugin's).
 */
public final class ModGlow implements BotGlow {

    private static final String HUNTER_TEAM = "mhs_cacadores";
    private static final String ESCAPE_TEAM = "mhs_fugitivos";

    private final Settings settings;

    public ModGlow(Settings settings) {
        this.settings = settings;
    }

    private static ServerScoreboard board() {
        return ModServer.get().vanilla().getScoreboard();
    }

    private static PlayerTeam team(String name) {
        PlayerTeam t = board().getPlayerTeam(name);
        return t != null ? t : board().addPlayerTeam(name);
    }

    private static void color(PlayerTeam team, NamedTextColor color) {
        team.setColor(Optional.ofNullable(TeamColor.byName(NamedTextColor.NAMES.key(color))));
    }

    private static void removeEntry(PlayerTeam team, String entry) {
        if (team != null && team.getPlayers().contains(entry)) {
            board().removePlayerFromTeam(entry, team);
        }
    }

    @Override
    public void apply(Bot bot) {
        Entity e = ModEntities.vanilla(bot.entity());
        if (e == null) {
            return;
        }
        BotGroup g = bot.group();
        boolean escape = g != null && g.role() == BotRole.ESCAPE;
        boolean glow = escape ? settings.glowEscapers.on() : settings.glowHunters.on();
        PlayerTeam hunters = team(HUNTER_TEAM);
        PlayerTeam escapers = team(ESCAPE_TEAM);
        color(hunters, settings.glowHuntersColor.get().color());
        color(escapers, settings.glowEscapersColor.get().color());
        String entry = e.getUUID().toString();
        if (glow) {
            PlayerTeam want = escape ? escapers : hunters;
            removeEntry(escape ? hunters : escapers, entry);
            if (!want.getPlayers().contains(entry)) {
                board().addPlayerToTeam(entry, want);
            }
        } else {
            removeEntry(hunters, entry);
            removeEntry(escapers, entry);
        }
        e.setGlowingTag(glow);
    }

    @Override
    public void remove(Bot bot) {
        Entity e = ModEntities.vanilla(bot.entity());
        if (e == null) {
            return;
        }
        String entry = e.getUUID().toString();
        removeEntry(board().getPlayerTeam(HUNTER_TEAM), entry);
        removeEntry(board().getPlayerTeam(ESCAPE_TEAM), entry);
    }

    @Override
    public void applyAll(Collection<Bot> bots) {
        for (Bot b : bots) {
            apply(b);
        }
    }

    /** On shutdown: removes the teams (the entities are already gone). */
    @Override
    public void cleanup() {
        if (ModServer.get() == null) {
            return;
        }
        for (String name : new String[]{HUNTER_TEAM, ESCAPE_TEAM}) {
            PlayerTeam t = board().getPlayerTeam(name);
            if (t != null) {
                board().removePlayerTeam(t);
            }
        }
    }
}
