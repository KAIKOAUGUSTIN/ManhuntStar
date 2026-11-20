package dev.manhuntstar.manhunt;

import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.Modes.Defeat;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameMode;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Vec3;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * The end of the match. The hunted players win when the Ender Dragon dies during the hunt; they lose
 * depending on {@code manhunt.defeat}: once each of them died once, at the first death or never. A hunted
 * player who dies (when deaths count) is out: after the death screen they watch as spectators. At the end
 * every player gets the title, the chat gets a short summary (who decided it, hunt time, deaths on each
 * side, the bots' highest phase and the bot that killed the most) and the bots stop where they are, partying
 * or moping, until the next hunt. {@link #RETURN_SECONDS} after the end, whoever was out goes back to the
 * game mode they had, where they respawned. All of it is saved with the bots.
 */
public final class MatchService {

    /** How the match ended. */
    public enum Result {
        /** The Ender Dragon died: the hunted players won. */
        VICTORY,
        /** The hunted players died: the bots won. */
        DEFEAT
    }

    /** How long the players who were out keep watching after the end, before they get back in the game. */
    public static final int RETURN_SECONDS = 15;

    /**
     * A hunted player who is out of the match. Until they respawn {@code place} is null (they are on the death
     * screen); after that they are a spectator and {@code mode} is the game mode to give back.
     */
    private static final class Out {
        GameMode mode;
        Place place;
        /** The match is over: they go back as soon as they're online (or respawn normally if still dead). */
        boolean due;
    }

    private final ManhuntHost plugin;
    private Result result;
    private long startClock;
    private long endedTicks;
    private int playerDeaths;
    private int botDeaths;
    private int maxPhase;
    private String lastDead;
    private String dragonKiller;
    private final Map<UUID, Integer> botKills = new HashMap<>();
    private final Map<UUID, Out> out = new LinkedHashMap<>();
    private int returnTicks = -1;

    public MatchService(ManhuntHost plugin) {
        this.plugin = plugin;
    }

    private HuntPolicy policy() {
        return plugin.botManager().policy();
    }

    private long clock() {
        return plugin.botManager().pause().clock();
    }

    // ------------------------------------------------------------------ state

    /** The match ended (until the next hunt): the bots don't hunt, they only party or mope. */
    public boolean isEnded() {
        return result != null;
    }

    public Result result() {
        return result;
    }

    /** The bots won the match that ended (they party). */
    public boolean botsWon() {
        return result == Result.DEFEAT;
    }

    /** Deaths and the dragon count now: a hunt is going on and it hasn't ended. */
    private boolean counting() {
        return result == null && policy().isActive();
    }

    /**
     * A new hunt begins (a ring released the bots, or bots created without a ring): the stats start over
     * and whoever is still out of the last match gets back in the game.
     */
    public void begin() {
        result = null;
        startClock = clock();
        endedTicks = 0;
        playerDeaths = 0;
        botDeaths = 0;
        maxPhase = 0;
        lastDead = null;
        dragonKiller = null;
        botKills.clear();
        returnTicks = -1;
        sendEveryoneBack();
        for (Bot b : plugin.botManager().allBots()) {
            plugin.botManager().resumeHunting(b);
        }
        plugin.botManager().markDirty();
    }

    /** Delete all: no match anymore; whoever is out gets back in the game. */
    public void reset() {
        result = null;
        returnTicks = -1;
        botKills.clear();
        sendEveryoneBack();
        plugin.botManager().markDirty();
    }

    // ------------------------------------------------------------------ what happens in the game

    /** A player died; {@code killer} is who caused it (a bot's body, a player, a mob or null). */
    public void playerDied(GamePlayer victim, GameEntity killer) {
        if (!counting() || victim == null || !hunted(victim)) {
            return;
        }
        playerDeaths++;
        lastDead = victim.name();
        Bot killerBot = plugin.botManager().byEntity(killer);
        if (killerBot != null) {
            botKills.merge(killerBot.id(), 1, Integer::sum);
        }
        Defeat defeat = plugin.settings().defeat.get();
        if (defeat.eliminates()) {
            Out old = out.get(victim.uuid());
            if (old == null || old.due) {
                out.put(victim.uuid(), new Out());
            }
        }
        plugin.botManager().markDirty();
        if (deathEndsMatch(defeat, everyoneOut(huntedOnline(), outOfThisMatch()))) {
            end(Result.DEFEAT);
        } else if (defeat == Defeat.ALL_DIED) {
            Platform.server().broadcast(plugin.messages().prefixed("match.player-out",
                    Messages.p("player", victim.name()), Messages.p("left", huntedAlive())));
        }
    }

    /** A bot died; {@code killer} is who caused it. */
    public void botDied(GameEntity killer) {
        if (!counting()) {
            return;
        }
        botDeaths++;
        Bot killerBot = plugin.botManager().byEntity(killer);
        if (killerBot != null) {
            botKills.merge(killerBot.id(), 1, Integer::sum);
        }
        plugin.botManager().markDirty();
    }

    /** The Ender Dragon died: the hunted players win, whoever (or whatever) killed it. */
    public void dragonDied(GameEntity killer) {
        if (!counting()) {
            return;
        }
        dragonKiller = nameOf(killer);
        end(Result.VICTORY);
    }

    /** The player respawned: someone out of the match watches as a spectator. */
    public void playerRespawned(GamePlayer p) {
        Out o = out.get(p.uuid());
        if (o == null) {
            return;
        }
        if (o.due) {
            // the match ended while they were on the death screen: a normal respawn
            out.remove(p.uuid());
            plugin.botManager().markDirty();
            return;
        }
        if (o.place == null) {
            o.mode = p.gameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : p.gameMode();
            o.place = p.place();
            p.setGameMode(GameMode.SPECTATOR);
            plugin.messages().send(p, "match.spectating");
            plugin.botManager().markDirty();
        }
    }

    /** The player joined: someone whose match ended while they were offline gets back in the game. */
    public void playerJoined(GamePlayer p) {
        Out o = out.get(p.uuid());
        if (o != null && o.due) {
            sendBack(p, o);
        }
    }

    // ------------------------------------------------------------------ end

    /** Every tick: the countdown to send the players who were out back, and those who left the hunt. */
    public void tick() {
        if (returnTicks > 0 && --returnTicks == 0) {
            returnTicks = -1;
            sendEveryoneBack();
        }
        if (Platform.server().currentTick() % 20 == 0 && counting()
                && plugin.settings().defeat.get() == Defeat.ALL_DIED && everyoneOut(huntedOnline(), outOfThisMatch())) {
            // the last ones still alive left the server
            end(Result.DEFEAT);
        }
    }

    private void end(Result r) {
        result = r;
        endedTicks = clock() - startClock;
        for (Bot b : plugin.botManager().allBots()) {
            maxPhase = Math.max(maxPhase, b.progression().phase());
        }
        plugin.botManager().pause().reset();
        plugin.botManager().rings().cancelAll();
        policy().end();
        for (Bot b : plugin.botManager().aliveBots()) {
            plugin.botManager().stopHunting(b, botsWon());
        }
        plugin.botManager().cancelPortalArrivals();
        showTitle();
        broadcastSummary();
        if (!out.isEmpty()) {
            returnTicks = RETURN_SECONDS * 20;
            for (UUID id : out.keySet()) {
                GamePlayer p = Platform.server().player(id);
                if (p != null) {
                    plugin.messages().send(p, "match.returning", Messages.p("seconds", RETURN_SECONDS));
                }
            }
        }
        plugin.botManager().markDirty();
    }

    private void showTitle() {
        Messages m = plugin.messages();
        Component title;
        Component subtitle;
        GameSound sound;
        if (result == Result.VICTORY) {
            title = m.get("match.victory-title");
            subtitle = dragonKiller == null ? m.get("match.victory-subtitle-unknown")
                    : m.get("match.victory-subtitle", Messages.p("player", dragonKiller));
            sound = Sounds.UI_TOAST_CHALLENGE_COMPLETE;
        } else {
            title = m.get("match.defeat-title");
            subtitle = m.get("match.defeat-subtitle");
            sound = Sounds.ENTITY_WITHER_DEATH;
        }
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            p.showTitle(t);
            p.playSound(p.position(), sound, SoundCategory.MASTER, 1f, 1f);
        }
    }

    private void broadcastSummary() {
        Messages m = plugin.messages();
        Component first;
        if (result == Result.VICTORY) {
            first = dragonKiller == null ? m.prefixed("match.summary-victory-unknown")
                    : m.prefixed("match.summary-victory", Messages.p("player", dragonKiller));
        } else {
            first = m.prefixed("match.summary-defeat", Messages.p("player", lastDead == null ? "-" : lastDead));
        }
        Platform.server().broadcast(first);
        Platform.server().broadcast(m.get("match.summary-stats", Messages.p("time", formatTime(endedTicks / 20)),
                Messages.p("playerdeaths", playerDeaths), Messages.p("botdeaths", botDeaths)));
        UUID top = topKiller(botKills);
        Bot topBot = top == null ? null : plugin.botManager().bot(top);
        if (topBot == null) {
            Platform.server().broadcast(m.get("match.summary-bots-no-kills", Messages.p("phase", maxPhase)));
        } else {
            Platform.server().broadcast(m.get("match.summary-bots", Messages.p("phase", maxPhase),
                    Messages.p("bot", topBot.profileName()), Messages.p("kills", botKills.get(top))));
        }
    }

    /** Whoever is out of the match gets back in the game, now or as soon as they're online. */
    private void sendEveryoneBack() {
        Iterator<Map.Entry<UUID, Out>> it = out.entrySet().iterator();
        List<Runnable> now = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Out> e = it.next();
            Out o = e.getValue();
            o.due = true;
            GamePlayer p = Platform.server().player(e.getKey());
            if (p != null && o.place != null) {
                now.add(() -> sendBack(p, o));
            } else if (p != null && !p.isDead()) {
                // out, but never got as far as respawning in this match: nothing to undo
                it.remove();
            }
        }
        now.forEach(Runnable::run);
        plugin.botManager().markDirty();
    }

    private void sendBack(GamePlayer p, Out o) {
        out.remove(p.uuid());
        if (o.place == null) {
            return;
        }
        if (o.place.world() != null) {
            p.teleport(o.place);
        }
        p.setGameMode(o.mode == null ? GameMode.SURVIVAL : o.mode);
        plugin.messages().send(p, "match.back");
        plugin.botManager().markDirty();
    }

    /** Who is out of the match going on (not those of the last match still waiting to get back). */
    private List<UUID> outOfThisMatch() {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, Out> e : out.entrySet()) {
            if (!e.getValue().due) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /**
     * The hunted players online now: those playing and those out of the match. Someone who was already a
     * spectator (watching, not hunted) isn't one, or they would hold up the defeat.
     */
    private List<UUID> huntedOnline() {
        List<UUID> outNow = outOfThisMatch();
        List<UUID> ids = new ArrayList<>();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            if (hunted(p) && (p.gameMode() != GameMode.SPECTATOR || outNow.contains(p.uuid()))) {
                ids.add(p.uuid());
            }
        }
        return ids;
    }

    /**
     * The player is hunted in this match: a participant the player list allows, or the target of
     * /manhunt target, whom the bots chase whatever the list says.
     */
    private boolean hunted(GamePlayer p) {
        return policy().isParticipant(p) || p.uuid().equals(plugin.botManager().forcedTarget());
    }

    /** Hunted players online and still in the match. */
    private int huntedAlive() {
        int n = 0;
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            Out o = out.get(p.uuid());
            if (hunted(p) && p.gameMode() != GameMode.SPECTATOR && (o == null || o.due)) {
                n++;
            }
        }
        return n;
    }

    private String nameOf(GameEntity e) {
        if (e instanceof GameProjectile proj && proj.shooter() != null) {
            e = proj.shooter();
        }
        if (e instanceof GamePlayer p) {
            return p.name();
        }
        Bot b = plugin.botManager().byEntity(e);
        return b == null ? null : b.profileName();
    }

    // ------------------------------------------------------------------ pure rules (tested without a server)

    /** Does a hunted player's death end the match? {@code everyoneOut}: every hunted player online is out now. */
    public static boolean deathEndsMatch(Defeat defeat, boolean everyoneOut) {
        return switch (defeat) {
            case FIRST_DEATH -> true;
            case ALL_DIED -> everyoneOut;
            case NEVER -> false;
        };
    }

    /** Every hunted player online is out of the match (someone offline doesn't hold it up; nobody online: no). */
    public static boolean everyoneOut(Collection<UUID> huntedOnline, Collection<UUID> out) {
        return !huntedOnline.isEmpty() && out.containsAll(huntedOnline);
    }

    /** Hunt time as 1:02:03, or 2:03 under an hour. */
    public static String formatTime(long seconds) {
        long s = Math.max(0, seconds);
        long h = s / 3600;
        long min = s / 60 % 60;
        long sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, min, sec) : String.format(Locale.ROOT, "%d:%02d", min, sec);
    }

    /** The bot with the most kills (the first one reached on a tie), or null when nobody killed anyone. */
    public static UUID topKiller(Map<UUID, Integer> kills) {
        UUID best = null;
        int bestKills = 0;
        for (Map.Entry<UUID, Integer> e : kills.entrySet()) {
            if (e.getValue() > bestKills) {
                best = e.getKey();
                bestKills = e.getValue();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ saving

    public void save(ConfigNode s) {
        if (result != null) {
            s.set("resultado", result.name());
            s.set("tempo", endedTicks);
            s.set("volta-ticks", returnTicks);
        } else {
            s.set("tempo", clock() - startClock);
        }
        s.set("mortes-jogadores", playerDeaths);
        s.set("mortes-bots", botDeaths);
        s.set("fase-maxima", maxPhase);
        if (lastDead != null) {
            s.set("ultimo-morto", lastDead);
        }
        if (dragonKiller != null) {
            s.set("matou-dragao", dragonKiller);
        }
        for (Map.Entry<UUID, Integer> e : botKills.entrySet()) {
            s.set("abates." + e.getKey(), e.getValue());
        }
        saveOut(s);
    }

    /** Only who still has to get back in the game: saved even when the bots aren't (persistent-bots off). */
    public void saveReturns(ConfigNode s) {
        saveOut(s);
    }

    /** Server start without saved bots: there's no match, so whoever was out of the last one gets back in the game. */
    public void loadReturns(ConfigNode s) {
        out.clear();
        if (s != null) {
            readOut(s.section("fora"));
        }
        sendEveryoneBack();
    }

    private void saveOut(ConfigNode s) {
        for (Map.Entry<UUID, Out> e : out.entrySet()) {
            Out o = e.getValue();
            String k = "fora." + e.getKey();
            s.set(k + ".volta", o.due);
            if (o.place != null) {
                s.set(k + ".modo", (o.mode == null ? GameMode.SURVIVAL : o.mode).name());
                s.set(k + ".mundo", o.place.world() == null ? "" : o.place.world().name());
                s.set(k + ".x", o.place.x());
                s.set(k + ".y", o.place.y());
                s.set(k + ".z", o.place.z());
                s.set(k + ".yaw", (double) o.place.yaw());
                s.set(k + ".pitch", (double) o.place.pitch());
            }
        }
    }

    public void load(ConfigNode s) {
        result = null;
        botKills.clear();
        out.clear();
        returnTicks = -1;
        if (s == null) {
            startClock = clock();
            return;
        }
        String r = s.getString("resultado");
        try {
            result = r == null ? null : Result.valueOf(r);
        } catch (IllegalArgumentException ignored) {
            // broken line in bots.yml
        }
        long time = s.getLong("tempo", 0);
        endedTicks = result != null ? time : 0;
        startClock = clock() - (result != null ? 0 : time);
        returnTicks = result != null ? s.getInt("volta-ticks", -1) : -1;
        playerDeaths = s.getInt("mortes-jogadores", 0);
        botDeaths = s.getInt("mortes-bots", 0);
        maxPhase = s.getInt("fase-maxima", 0);
        lastDead = s.getString("ultimo-morto");
        dragonKiller = s.getString("matou-dragao");
        readKills(s.section("abates"));
        readOut(s.section("fora"));
    }

    private void readKills(ConfigNode k) {
        if (k == null) {
            return;
        }
        for (String id : k.keys(false)) {
            try {
                botKills.put(UUID.fromString(id), k.getInt(id, 0));
            } catch (IllegalArgumentException ignored) {
                // broken line in bots.yml
            }
        }
    }

    private void readOut(ConfigNode f) {
        if (f == null) {
            return;
        }
        for (String id : f.keys(false)) {
            ConfigNode n = f.section(id);
            if (n == null) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(id);
                Out o = new Out();
                o.due = n.getBoolean("volta", false);
                if (n.contains("modo")) {
                    o.mode = GameMode.valueOf(n.getString("modo", "SURVIVAL"));
                    GameWorld w = Platform.server().world(n.getString("mundo", ""));
                    if (w == null) {
                        w = Platform.server().defaultWorld();
                    }
                    o.place = new Place(w, new Vec3(n.getDouble("x", 0), n.getDouble("y", 0), n.getDouble("z", 0)),
                            (float) n.getDouble("yaw", 0), (float) n.getDouble("pitch", 0));
                }
                out.put(uuid, o);
            } catch (IllegalArgumentException ignored) {
                // broken line in bots.yml
            }
        }
    }
}
