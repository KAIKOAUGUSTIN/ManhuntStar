package dev.manhuntstar.bot;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.api.BotEvents;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.bot.Modes.SpawnMode;
import dev.manhuntstar.bot.Modes.TargetMode;
import dev.manhuntstar.bot.ai.IdleBehavior;
import dev.manhuntstar.bot.mobility.PortalEntrance;
import dev.manhuntstar.bot.world.PlayerBlockTracker;
import dev.manhuntstar.bot.world.TemporaryBlocks;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.manhunt.HuntPolicy;
import dev.manhuntstar.manhunt.PauseService;
import dev.manhuntstar.manhunt.RingService;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperItems;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameMode;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.tactics.CaveAmbush;
import dev.manhuntstar.tactics.TacticsManager;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Registry and life cycle of every bot: creation (with skin), choosing the target player, death,
 * respawn, silent deaths of bots that are too far away, dimension changes, saving and groups.
 */
public final class BotManager {

    private static final int RESPAWN_DELAY_TICKS = 100;
    private static final double MIN_SILENT_DEATH_DISTANCE = 200.0;
    private static final double MAX_SILENT_DEATH_DISTANCE = 1000.0;
    private static final int PERSIST_INTERVAL_TICKS = 100;

    private final ManhuntStarPlugin plugin;
    private final NamespacedKey botKey;
    private final Map<UUID, Bot> bots = new LinkedHashMap<>();
    private final Map<UUID, Bot> byEntity = new HashMap<>();
    private final Map<String, BotGroup> groups = new LinkedHashMap<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingSilentDeaths = new HashMap<>();
    private final Map<UUID, Location> customSpawnpoints = new HashMap<>();
    private final Map<UUID, Long> inaccessibleHorses = new HashMap<>();
    private final NamespacedKey horseKey;
    private final Map<UUID, UUID> controlledHorses = new HashMap<>();
    private final List<PortalEntrance> entrances = new ArrayList<>();
    private final PlayerBlockTracker blockTracker = new PlayerBlockTracker();
    private final TemporaryBlocks temporaryBlocks;
    private final RingService rings;
    private final HuntPolicy policy;
    private final PauseService pause;
    private final TacticsManager tactics;
    private final CaveAmbush caveAmbush;
    private final Map<UUID, Long> tickErrorLog = new HashMap<>();
    private UUID forcedTarget;
    private int globalMaxPhase;
    private boolean dirty;

    private record PendingRespawn(UUID botId, long respawnAt, String world, Vec3 deathPos) {
    }

    public BotManager(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
        this.botKey = new NamespacedKey(plugin, "bot");
        this.horseKey = new NamespacedKey(plugin, "cavalo_controlado");
        this.temporaryBlocks = new TemporaryBlocks(plugin.server(), plugin::pathService);
        this.rings = new RingService(plugin);
        this.policy = new HuntPolicy(plugin);
        this.pause = new PauseService(plugin);
        this.tactics = new TacticsManager(this);
        this.caveAmbush = new CaveAmbush(this);
    }

    // ------------------------------------------------------------------ access

    public ManhuntStarPlugin plugin() {
        return plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    public NamespacedKey botKey() {
        return botKey;
    }

    public RingService rings() {
        return rings;
    }

    /** Who can be hunted (player list and late joiners). */
    public HuntPolicy policy() {
        return policy;
    }

    /** The hunt pause and the clock that stops along with it. */
    public PauseService pause() {
        return pause;
    }

    public TacticsManager tactics() {
        return tactics;
    }

    public CaveAmbush caveAmbush() {
        return caveAmbush;
    }

    public PlayerBlockTracker blockTracker() {
        return blockTracker;
    }

    public TemporaryBlocks temporaryBlocks() {
        return temporaryBlocks;
    }

    public Collection<Bot> allBots() {
        return Collections.unmodifiableCollection(bots.values());
    }

    public List<Bot> aliveBots() {
        List<Bot> out = new ArrayList<>(bots.size());
        for (Bot b : bots.values()) {
            if (b.isAlive()) {
                out.add(b);
            }
        }
        return out;
    }

    public int aliveCount() {
        int n = 0;
        for (Bot b : bots.values()) {
            if (b.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public Bot bot(UUID id) {
        return id == null ? null : bots.get(id);
    }

    /** First bot with this internal or display name (case-insensitive). */
    public Bot bot(String name) {
        for (Bot b : bots.values()) {
            if (b.name().equalsIgnoreCase(name)) {
                return b;
            }
        }
        for (Bot b : bots.values()) {
            if (b.visualName().equalsIgnoreCase(name)) {
                return b;
            }
        }
        return null;
    }

    public List<Bot> botsNamed(String name) {
        List<Bot> out = new ArrayList<>();
        for (Bot b : bots.values()) {
            if (b.name().equalsIgnoreCase(name) || b.visualName().equalsIgnoreCase(name)) {
                out.add(b);
            }
        }
        return out;
    }

    /** The bot that owns this entity (Mannequin), or null. */
    public Bot byEntity(GameEntity e) {
        if (!(e instanceof BotBody)) {
            return null;
        }
        Bot b = byEntity.get(e.uuid());
        if (b != null && b.entity() != null && b.entity().uuid().equals(e.uuid())) {
            return b;
        }
        return null;
    }

    public boolean isBotEntity(GameEntity e) {
        return e instanceof BotBody && PaperEntities.bukkit(e).getPersistentDataContainer().has(botKey, PersistentDataType.STRING);
    }

    void registerEntity(Bot bot, BotBody m) {
        byEntity.values().remove(bot);
        if (m != null) {
            byEntity.put(m.uuid(), bot);
        }
    }

    // ------------------------------------------------------------------ groups

    public BotGroup group(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    public BotGroup getOrCreateGroup(String name) {
        return groups.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new BotGroup(name));
    }

    public Collection<BotGroup> groups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public boolean isTrackingPlayers(Bot bot) {
        BotGroup g = bot.group();
        return g == null || g.trackPlayers();
    }

    /** Another bot that isn't from an enemy group (the sweep attack doesn't hit allies). */
    public boolean isAlly(Bot bot, GameEntity other) {
        Bot o = byEntity(other);
        if (o == null || o == bot) {
            return o != null;
        }
        BotGroup mine = bot.group();
        BotGroup theirs = o.group();
        boolean enemies = (mine != null && mine.isEnemy(o.groupName())) || (theirs != null && theirs.isEnemy(bot.groupName()));
        return !enemies;
    }

    public Bot nearestEnemyBot(Bot self) {
        BotGroup g = self.group();
        if (g == null || g.enemies().isEmpty()) {
            return null;
        }
        Bot nearest = null;
        double best = Double.MAX_VALUE;
        for (Bot o : bots.values()) {
            if (o == self || !o.isAlive() || o.groupName() == null || !g.isEnemy(o.groupName()) || !o.world().equals(self.world())) {
                continue;
            }
            double d = self.pos().distanceToSqr(o.pos());
            if (d < best) {
                best = d;
                nearest = o;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------ target

    /**
     * A player the bots can hunt now: alive, not a spectator and allowed by the player list and by the
     * late-joiner rule. The forced target (/manhunt target) always passes.
     */
    public boolean isEligibleTarget(GamePlayer p) {
        return isActivePlayer(p) && (p.uuid().equals(forcedTarget) || policy.canBeHunted(p));
    }

    /** Online, alive and not in spectator mode. */
    public static boolean isActivePlayer(GamePlayer p) {
        return p != null && p.isOnline() && !p.isDead() && p.gameMode() != GameMode.SPECTATOR;
    }

    public UUID forcedTarget() {
        return forcedTarget;
    }

    public void setForcedTarget(UUID id) {
        forcedTarget = id;
    }

    /** The bot's target player: forced → the mode's rule (closest, split evenly, farthest). */
    public GamePlayer resolvePlayerTarget(Bot bot) {
        if (!isTrackingPlayers(bot)) {
            return null;
        }
        if (forcedTarget != null) {
            GamePlayer forced = Platform.server().player(forcedTarget);
            if (isEligibleTarget(forced)) {
                return forced;
            }
        }
        List<GamePlayer> candidates = new ArrayList<>();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            if (isEligibleTarget(p)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        List<GamePlayer> same = new ArrayList<>();
        for (GamePlayer p : candidates) {
            if (p.world().equals(bot.world())) {
                same.add(p);
            }
        }
        List<GamePlayer> pool = same.isEmpty() ? candidates : same;
        TargetMode mode = settings().targetMode.get();
        return switch (mode) {
            case CLOSEST_PLAYER -> byDistance(bot, pool, true);
            case FARTHEST_PLAYER -> byDistance(bot, pool, false);
            case EQUAL_BOTS_PER_PLAYER -> balanced(bot, pool);
        };
    }

    private static double dist(Bot bot, GamePlayer p) {
        return p.world().equals(bot.world()) ? bot.distanceTo(p) : 1.0E7;
    }

    private static GamePlayer byDistance(Bot bot, List<GamePlayer> pool, boolean closest) {
        GamePlayer chosen = null;
        double best = closest ? Double.MAX_VALUE : -1;
        for (GamePlayer p : pool) {
            double d = dist(bot, p);
            if (closest ? d < best : d > best) {
                best = d;
                chosen = p;
            }
        }
        return chosen;
    }

    /** Splits the bots evenly among the players, switching to a closer one only if it gains ≥ 2.5 blocks (≤ 10). */
    private GamePlayer balanced(Bot bot, List<GamePlayer> pool) {
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, GamePlayer> byId = new HashMap<>();
        for (GamePlayer p : pool) {
            counts.put(p.uuid(), 0);
            byId.put(p.uuid(), p);
        }
        for (Bot o : bots.values()) {
            if (o == bot || !o.isAlive() || o.targetPlayerId() == null) {
                continue;
            }
            counts.computeIfPresent(o.targetPlayerId(), (k, v) -> v + 1);
        }
        int min = Integer.MAX_VALUE;
        for (int c : counts.values()) {
            min = Math.min(min, c);
        }
        GamePlayer closest = byDistance(bot, pool, true);
        double closestDist = closest == null ? Double.MAX_VALUE : dist(bot, closest);
        UUID currentId = bot.targetPlayerId();
        if (currentId != null && counts.containsKey(currentId) && counts.get(currentId) <= min + 1) {
            GamePlayer current = byId.get(currentId);
            if (closest != null && !closest.uuid().equals(currentId)) {
                double gain = dist(bot, current) - closestDist;
                if (closestDist <= 10.0 && gain >= 2.5) {
                    return closest;
                }
            }
            return current;
        }
        GamePlayer best = null;
        int bestCount = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (GamePlayer p : pool) {
            int c = counts.getOrDefault(p.uuid(), 0);
            double d = dist(bot, p);
            if (c < bestCount || (c == bestCount && d < bestDist)) {
                best = p;
                bestCount = c;
                bestDist = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ phases

    /** @return true if it is the highest phase any bot has reached (announce in chat). */
    public boolean notePhaseReached(int phase) {
        if (phase > globalMaxPhase) {
            globalMaxPhase = phase;
            return true;
        }
        return false;
    }

    public void resetGlobalPhase() {
        globalMaxPhase = 0;
        for (Bot b : bots.values()) {
            globalMaxPhase = Math.max(globalMaxPhase, b.progression().phase());
        }
    }

    // ------------------------------------------------------------------ horses

    public void markHorseInaccessible(GameHorse horse, int ticks) {
        inaccessibleHorses.put(horse.uuid(), (long) Bukkit.getCurrentTick() + ticks);
    }

    public boolean isHorseInaccessible(GameHorse horse) {
        Long until = inaccessibleHorses.get(horse.uuid());
        return until != null && until > Bukkit.getCurrentTick();
    }

    /**
     * Turns off the horse's AI while the bot rides it. In the game only a player steers the horse; with
     * another rider it keeps wandering, grazing and turning on its own and fights the bot.
     * The mark on the horse makes sure the AI comes back even if the server goes down with the bot mounted.
     */
    public void takeHorseControl(Bot bot, GameHorse horse) {
        Bot.keepActive(horse);
        if (controlledHorses.containsKey(horse.uuid()) && !horse.isAware()) {
            return;
        }
        PaperEntities.bukkit(horse).getPathfinder().stopPathfinding();
        horse.setAware(false);
        PaperEntities.bukkit(horse).getPersistentDataContainer().set(horseKey, PersistentDataType.STRING, bot.id().toString());
        controlledHorses.put(horse.uuid(), bot.id());
    }

    /** Gives the horse back its own AI. */
    public void releaseHorse(GameHorse horse) {
        controlledHorses.remove(horse.uuid());
        if (PaperEntities.bukkit(horse).getPersistentDataContainer().has(horseKey, PersistentDataType.STRING)) {
            PaperEntities.bukkit(horse).getPersistentDataContainer().remove(horseKey);
            horse.setAware(true);
            horse.setPortalCooldown(0);
        }
    }

    /** Releases horses whose bot dismounted, died or was removed without going through the horse routine. */
    private void tickControlledHorses() {
        if (controlledHorses.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(controlledHorses.entrySet())) {
            GameEntity horse = Platform.server().entity(e.getKey());
            if (!(horse instanceof GameHorse h)) {
                // unloaded: the mark is handled when the chunk comes back (removeOrphan)
                controlledHorses.remove(e.getKey());
                continue;
            }
            Bot b = bots.get(e.getValue());
            boolean ridden = b != null && b.isAlive() && b.entity() != null && h.passengers().contains(b.entity());
            if (!ridden || !h.isValid()) {
                releaseHorse(h);
            }
        }
    }

    private void releaseAllHorses() {
        for (UUID id : new ArrayList<>(controlledHorses.keySet())) {
            if (Platform.server().entity(id) instanceof GameHorse h) {
                releaseHorse(h);
            }
        }
        controlledHorses.clear();
    }

    // ------------------------------------------------------------------ creation

    /**
     * Creates {@code count} bots at the location. Names: "name1, name2..." or "%" replaced by the number;
     * the visible name is the name without "%". In MANHUNT mode they spawn in a ring around the location
     * (which is the ring's center) and wait until someone leaves it.
     */
    public CompletableFuture<List<Bot>> createBots(Location loc, String name, String skinName, int count,
                                                   BotDifficulty difficulty, int startPhase, int aggression) {
        String skinTarget = skinName == null || skinName.isBlank() ? visualNameOf(name) : skinName;
        return plugin.skins().resolve(skinTarget).thenApplyAsync(skin -> spawnBots(loc, name, skin, count, difficulty, startPhase, aggression),
                r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    /**
     * Ring center for bots created at {@code given} (the creator's position): in manual mode, the
     * point set with /manhunt ring point; with no point (or with its world unloaded), the location itself.
     */
    public Location ringCenterFor(Location given) {
        if (!manualRingPointInUse()) {
            return given;
        }
        Settings.RingPoint p = settings().ringPoint();
        return new Location(Bukkit.getWorld(p.world()), p.x(), p.y(), p.z());
    }

    /** Manual mode is on but there is no usable ring point. */
    public boolean manualRingPointMissing() {
        return settings().spawnMode.get() == SpawnMode.MANHUNT && settings().ringCenter.get() == RingCenter.MANUAL && !manualRingPointInUse();
    }

    private boolean manualRingPointInUse() {
        if (settings().spawnMode.get() != SpawnMode.MANHUNT || settings().ringCenter.get() != RingCenter.MANUAL) {
            return false;
        }
        Settings.RingPoint p = settings().ringPoint();
        return p != null && Bukkit.getWorld(p.world()) != null;
    }

    /** Ring radius: the option's value, or 5 blocks + 1 for every 5 bots with the option at 0. */
    private double ringRadius(int count) {
        double fixed = settings().ringRadius.value();
        return fixed <= 0 ? 5.0 + count / 5.0 : Math.max(2.0, fixed);
    }

    private List<Bot> spawnBots(Location pos, String name, SkinData skin, int count, BotDifficulty difficulty, int startPhase, int aggression) {
        List<Bot> created = new ArrayList<>();
        GameWorld world = PaperWorld.of(pos.getWorld());
        String visual = visualNameOf(name);
        SpawnMode mode = settings().spawnMode.get();
        double radius = ringRadius(count);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BotGroup group = getOrCreateGroup(name);
        for (int i = 1; i <= count; i++) {
            double angle = i * 2 * Math.PI / count;
            Location spawn;
            if (mode == SpawnMode.NORMAL) {
                spawn = count == 1 ? pos.clone() : pos.clone().add(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
                spawn.setY(freeSpaceAbove(world, spawn));
            } else {
                spawn = pos.clone().add(Math.cos(angle) * radius + (r.nextDouble() - 0.5), 0, Math.sin(angle) * radius + (r.nextDouble() - 0.5));
                spawn.setY(surfaceNear(world, spawn, pos.getY()));
                // facing the ring's center
                spawn.setYaw(Locations.vec(pos).subtract(Locations.vec(spawn)).yaw());
            }
            String botName = name.contains("%") ? name.replace("%", String.valueOf(i)) : name + i;
            Bot bot = new Bot(this, UUID.randomUUID(), botName, visual, skin, difficulty);
            bot.setAggression(aggression);
            bot.setGroupName(group.name());
            register(bot);
            bot.spawnEntity(spawn);
            if (startPhase > 0) {
                bot.progression().setInitialPhase(startPhase);
            }
            bot.progression().updateGear();
            bot.restoreDefaultOffhand();
            announceJoin(bot);
            created.add(bot);
        }
        boolean inRing = mode == SpawnMode.MANHUNT && rings.create(pos, radius, created, settings().startCountdown.value());
        if (!inRing && !created.isEmpty()) {
            // without a ring the bots hunt right away
            policy.beginIfIdle();
        }
        dirty = true;
        return created;
    }

    /** Ground height near a ring point (so it doesn't spawn inside blocks or in the air). */
    private static double surfaceNear(GameWorld w, Location spawn, double refY) {
        Pos p = Locations.pos(spawn);
        for (int dy = 0; dy <= 4; dy++) {
            for (int sign : new int[]{1, -1}) {
                Pos c = new Pos(p.x(), (int) Math.floor(refY) + dy * sign, p.z());
                if (WorldUtil.isSafeStand(w, c)) {
                    return c.y();
                }
            }
        }
        return refY;
    }

    /** If the point falls inside blocks, moves up to the first free space (up to 16 blocks). */
    private static double freeSpaceAbove(GameWorld w, Location spawn) {
        Pos p = Locations.pos(spawn);
        for (int dy = 0; dy <= 16; dy++) {
            Pos c = p.above(dy);
            if (!WorldUtil.hasCollision(w, c) && !WorldUtil.hasCollision(w, c.above())) {
                return dy == 0 ? spawn.getY() : c.y();
            }
        }
        return spawn.getY();
    }

    private static String visualNameOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Bot";
        }
        String t = raw.trim();
        if (!t.contains("%")) {
            return t;
        }
        String without = t.replace("%", "").trim();
        return without.isEmpty() ? "Bot" : without;
    }

    private void register(Bot bot) {
        bots.put(bot.id(), bot);
        plugin.tabList().add(bot);
    }

    void onEntitySpawned(Bot bot) {
        registerEntity(bot, bot.entity());
        plugin.glow().apply(bot);
    }

    private void announceJoin(Bot bot) {
        if (settings().joinLeaveMessages.on()) {
            Bukkit.broadcast(Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text(bot.visualName())));
        }
    }

    private void announceLeave(Bot bot) {
        if (settings().joinLeaveMessages.on()) {
            Bukkit.broadcast(Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, Component.text(bot.visualName())));
        }
    }

    // ------------------------------------------------------------------ teleport

    /** Teleports the bot (in the same world or to another, recreating the entity) and settles the AI. */
    public void teleportBot(Bot bot, Location dest) {
        if (!bot.isAlive()) {
            return;
        }
        if (!bot.bukkitWorld().equals(dest.getWorld())) {
            plugin.glow().remove(bot);
        }
        bot.relocate(dest);
        registerEntity(bot, bot.entity());
        plugin.glow().apply(bot);
        bot.brain().startPostTeleportStabilization();
        if (pause.isPaused()) {
            pause.refreeze(bot);
        }
    }

    /** Brings the living bots with this name to the player (like remove and skin, applies to all). @return how many went */
    public int teleportBotsToPlayer(String name, GamePlayer target) {
        List<Bot> alive = new ArrayList<>();
        for (Bot b : botsNamed(name)) {
            if (b.isAlive()) {
                alive.add(b);
            }
        }
        for (Bot b : alive) {
            teleportBot(b, alive.size() == 1 ? PaperEntities.bukkit(target).getLocation() : nearPlayer(target));
        }
        return alive.size();
    }

    public int teleportAllTo(GamePlayer target) {
        int n = 0;
        for (Bot b : aliveBots()) {
            teleportBot(b, nearPlayer(target));
            n++;
        }
        return n;
    }

    /** Near the player, spread out a little so the bots don't end up inside each other. */
    private static Location nearPlayer(GamePlayer target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return PaperEntities.bukkit(target).getLocation().add(r.nextDouble(-2, 2), 0, r.nextDouble(-2, 2));
    }

    /** The target went to another dimension: opens a portal entrance for the bots following them. */
    public void requestDimensionChange(GamePlayer target, World from) {
        for (PortalEntrance e : entrances) {
            if (e.playerId().equals(target.uuid()) && e.worldId().equals(target.world().id())) {
                return;
            }
        }
        List<Bot> stuck = new ArrayList<>();
        for (Bot b : aliveBots()) {
            // bots waiting in a ring stay there until the start
            if (b.bukkitWorld().equals(from) && target.uuid().equals(b.targetPlayerId()) && b.idle() == null) {
                stuck.add(b);
                b.brain().setTeleporting(true);
            }
        }
        if (!stuck.isEmpty()) {
            entrances.add(new PortalEntrance(this, target, stuck));
        }
    }

    // ------------------------------------------------------------------ death and respawn

    /** Bot death (the entity's death event): drops, message, API and respawn. */
    public void handleDeath(Bot bot, EntityDeathEvent event) {
        BotBody e = bot.entity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        List<GameItem> drops = bot.inventory().collectDeathDrops(settings().dropInventory.on(), settings().gearPersistence.get());
        for (GameItem d : drops) {
            event.getDrops().add(PaperItems.stack(d));
        }
        if (settings().deathMessages.on() && e != null) {
            Boolean show = e.world().gameRule(dev.manhuntstar.platform.GameRule.SHOW_DEATH_MESSAGES);
            if (!Boolean.FALSE.equals(show)) {
                Bukkit.broadcast(plugin.deathMessages().build(bot, event.getDamageSource()));
            }
        }
        GameEntity killer = PaperEntities.of(event.getDamageSource().getCausingEntity());
        Bukkit.getPluginManager().callEvent(new BotEvents.BotDeathEvent(bot, event.getDamageSource()));
        if (killer instanceof GamePlayer p) {
            Bukkit.getPluginManager().callEvent(new BotEvents.BotKilledByPlayerEvent(bot, PaperEntities.bukkit(p)));
        } else {
            Bot killerBot = byEntity(killer);
            if (killerBot != null) {
                killerBot.incrementKills();
            }
        }
        Location deathLoc = e == null ? bot.location() : PaperEntities.bukkit(e).getLocation();
        if (deathLoc != null && deathLoc.getWorld() != null) {
            deathLoc.getWorld().spawnParticle(Particle.CLOUD, deathLoc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        }
        tactics.forget(bot);
        byEntity.values().remove(bot);
        plugin.glow().remove(bot);
        bot.markDead();
        queueRespawn(bot, deathLoc);
        dirty = true;
    }

    /** Silent death: disappears with no message or drops and respawns as in a normal death. */
    public void killSilently(Bot bot) {
        if (!bot.isAlive()) {
            return;
        }
        Location loc = bot.location();
        tactics.forget(bot);
        byEntity.values().remove(bot);
        plugin.glow().remove(bot);
        bot.despawnEntity();
        queueRespawn(bot, loc);
        dirty = true;
    }

    private void queueRespawn(Bot bot, Location deathLoc) {
        pendingSilentDeaths.remove(bot.id());
        if (!settings().respawn.on() || !bot.shouldRespawn()) {
            return;
        }
        Vec3 pos = deathLoc == null ? bot.lastKnownPos() : Locations.vec(deathLoc);
        String world = deathLoc == null || deathLoc.getWorld() == null ? bot.lastKnownWorld() : deathLoc.getWorld().getName();
        pendingRespawns.put(bot.id(), new PendingRespawn(bot.id(), pause.clock() + RESPAWN_DELAY_TICKS, world, pos));
    }

    public boolean isPendingRespawn(Bot bot) {
        return pendingRespawns.containsKey(bot.id());
    }

    private void tickRespawns() {
        if (pendingRespawns.isEmpty()) {
            return;
        }
        long now = pause.clock();
        Iterator<PendingRespawn> it = pendingRespawns.values().iterator();
        List<Runnable> actions = new ArrayList<>();
        while (it.hasNext()) {
            PendingRespawn p = it.next();
            if (now < p.respawnAt()) {
                continue;
            }
            it.remove();
            Bot bot = bots.get(p.botId());
            if (bot == null || bot.isAlive() || !bot.shouldRespawn()) {
                continue;
            }
            actions.add(() -> respawn(bot, p));
        }
        actions.forEach(Runnable::run);
    }

    private void respawn(Bot bot, PendingRespawn p) {
        // a ring bot that died before the start goes back to the ring, not straight to hunting
        UUID ringId = waitingRing(bot);
        Location caveLoc = caveAmbush.respawnLocation(bot);
        Location spawn;
        boolean caveBed = caveLoc != null;
        if (caveBed) {
            spawn = caveLoc;
        } else if (ringId != null) {
            spawn = deathSpot(p);
        } else {
            spawn = normalRespawnLocation(bot, p);
        }
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }
        int phase = caveBed ? caveAmbush.restorePhase(bot) : bot.progression().phase();
        bot.resetRuntime();
        bot.spawnEntity(spawn);
        if (ringId != null) {
            bot.setIdle(new IdleBehavior(bot, ringId));
        }
        if (caveBed && bot.progression().progressionEnabled()) {
            bot.progression().startRespawnRegeneration(phase);
        } else {
            bot.progression().updateGear();
        }
        bot.inventory().applyPersistentGear(bot.inventory().snapshotPersistentGear());
        bot.restoreDefaultOffhand();
        bot.hunger().set(20, 5f);
        dirty = true;
    }

    /** The ring the bot is still waiting in (also when dead, before respawning), or null. */
    private UUID waitingRing(Bot bot) {
        IdleBehavior idle = bot.idle();
        return idle != null && idle.isActive() && rings.ring(idle.ringId()) != null ? idle.ringId() : null;
    }

    private static World deathWorld(PendingRespawn p) {
        World w = p.world() == null ? null : Bukkit.getWorld(p.world());
        return w == null ? Bukkit.getWorlds().getFirst() : w;
    }

    /** On the ground, where the bot died (for a ring bot, the ring itself). */
    private static Location deathSpot(PendingRespawn p) {
        World w = deathWorld(p);
        Vec3 pos = p.deathPos() == null ? Locations.vec(w.getSpawnLocation()) : p.deathPos();
        return resolveSurface(Locations.location(pos, w));
    }

    private Location normalRespawnLocation(Bot bot, PendingRespawn p) {
        World deathWorld = deathWorld(p);
        Location custom = customSpawnpoints.get(bot.id());
        if (custom != null && custom.getWorld() != null) {
            return resolveSurface(custom);
        }
        BotGroup g = bot.group();
        if (g != null && g.spawnpoint() != null) {
            World gw = g.spawnWorld() == null ? deathWorld : Bukkit.getWorld(g.spawnWorld());
            if (gw != null) {
                return resolveSurface(Locations.location(g.spawnpoint(), gw));
            }
        }
        GamePlayer target = bot.target();
        if (target != null && isEligibleTarget(target)) {
            Location hidden = findHiddenLocation(target);
            if (hidden != null) {
                return resolveSurface(hidden);
            }
        }
        return deathSpot(p);
    }

    /** A spot 100–200 blocks from the target, out of their line of sight (only in loaded chunks). */
    private static Location findHiddenLocation(GamePlayer target) {
        GameWorld w = PaperWorld.of(PaperWorld.bukkit(target.world()));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double distance = i < 20 ? 100 + r.nextInt(100) : 40 + r.nextInt(60);
            int x = (int) (target.position().x() + Math.cos(angle) * distance);
            int z = (int) (target.position().z() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = w.dimension() == Dimension.NETHER ? (int) target.position().y() : WorldUtil.surfaceY(w, x, z);
            Vec3 candidate = new Vec3(x + 0.5, y + 1, z + 0.5);
            if (!WorldUtil.clearLine(w, target.eyePosition(), candidate)) {
                return Locations.location(candidate, w);
            }
        }
        return null;
    }

    /** Safe ground near the point (rings of up to 24 blocks); in the Nether it also searches vertically. */
    public static Location resolveSurface(Location anchor) {
        GameWorld w = PaperWorld.of(anchor.getWorld());
        int bx = anchor.getBlockX();
        int bz = anchor.getBlockZ();
        boolean nether = w.dimension() == Dimension.NETHER;
        for (int radius = 0; radius <= 24; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = bx + dx;
                    int z = bz + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    if (nether) {
                        int baseY = anchor.getBlockY();
                        for (int y = Math.min(w.maxHeight() - 4, Math.min(118, baseY + 20)); y >= Math.max(w.minHeight() + 3, baseY - 20); y--) {
                            Pos feet = new Pos(x, y, z);
                            if (WorldUtil.isSafeStand(w, feet) && WorldUtil.countOpenSides(w, feet) >= 2) {
                                return new Location(PaperWorld.bukkit(w), x + 0.5, y + 0.1, z + 0.5, anchor.getYaw(), 0);
                            }
                        }
                    } else {
                        int y = w.dimension() == Dimension.NORMAL ? WorldUtil.surfaceYNoLeaves(w, x, z) : WorldUtil.surfaceY(w, x, z);
                        Pos feet = new Pos(x, y, z);
                        if (WorldUtil.isSafeStand(w, feet) && WorldUtil.countOpenSides(w, feet) >= 1) {
                            return new Location(PaperWorld.bukkit(w), x + 0.5, y + 0.1, z + 0.5, anchor.getYaw(), 0);
                        }
                    }
                }
            }
        }
        return anchor;
    }

    public void setCustomSpawnpoint(Bot bot, Location loc) {
        if (loc == null) {
            customSpawnpoints.remove(bot.id());
        } else {
            customSpawnpoints.put(bot.id(), loc.clone());
        }
    }

    // ------------------------------------------------------------------ removal

    /** Removes the bot for good (no respawn), like a player leaving the server. */
    public void removeBot(Bot bot) {
        bot.setShouldRespawn(false);
        pendingRespawns.remove(bot.id());
        pendingSilentDeaths.remove(bot.id());
        caveAmbush.forget(bot.id());
        tactics.forget(bot);
        customSpawnpoints.remove(bot.id());
        byEntity.values().remove(bot);
        if (bot.idle() != null) {
            bot.idle().release();
        }
        plugin.glow().remove(bot);
        bot.despawnEntity();
        bots.remove(bot.id());
        plugin.tabList().remove(bot);
        announceLeave(bot);
        dirty = true;
    }

    public int deleteAll() {
        int n = bots.size();
        for (Bot b : new ArrayList<>(bots.values())) {
            removeBot(b);
        }
        pendingRespawns.clear();
        pendingSilentDeaths.clear();
        caveAmbush.clear();
        tactics.clear();
        rings.cancelAll();
        policy.end();
        pause.reset();
        globalMaxPhase = 0;
        dirty = true;
        flush(false);
        return n;
    }

    public int deleteGroup(String name) {
        int n = 0;
        for (Bot b : new ArrayList<>(bots.values())) {
            if (name.equalsIgnoreCase(b.groupName())) {
                removeBot(b);
                n++;
            }
        }
        groups.remove(name.toLowerCase(Locale.ROOT));
        dirty = true;
        return n;
    }

    public int setPhaseProgressionForGroup(String name, boolean enabled) {
        int n = 0;
        for (Bot b : bots.values()) {
            if (name.equalsIgnoreCase(b.groupName())) {
                b.progression().setProgressionEnabled(enabled);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (pause.isPaused()) {
            tickPaused();
            return;
        }
        rings.tick();
        policy.tick();
        for (Bot bot : new ArrayList<>(bots.values())) {
            if (!bot.isAlive()) {
                continue;
            }
            try {
                bot.tick();
            } catch (RuntimeException ex) {
                long now = System.currentTimeMillis();
                Long last = tickErrorLog.get(bot.id());
                if (last == null || now - last > 60_000) {
                    tickErrorLog.put(bot.id(), now);
                    plugin.getLogger().log(Level.WARNING, "Error in the tick of bot " + bot.name(), ex);
                }
            }
        }
        tactics.tick();
        caveAmbush.tick();
        temporaryBlocks.tick();
        tickEntrances();
        tickRespawns();
        tickDistanceManagement();
        int now = Bukkit.getCurrentTick();
        if (now % 10 == 0) {
            tickControlledHorses();
        }
        if (now % 20 == 0) {
            checkStrandedBots();
        }
        if (now % 1200 == 0) {
            blockTracker.purge();
            inaccessibleHorses.values().removeIf(t -> t <= now);
        }
        if (now % PERSIST_INTERVAL_TICKS == 0) {
            flush(true);
        }
    }

    /**
     * Hunt paused: each bot stays still where it was and doesn't think; rings, hunt, tactics,
     * respawns and portal arrivals wait. Only temporary blocks, the horse check and saving
     * keep going.
     */
    private void tickPaused() {
        for (Bot bot : bots.values()) {
            if (bot.isAlive()) {
                bot.tickPaused();
                pause.hold(bot);
            }
        }
        pause.tick();
        temporaryBlocks.tick();
        int now = Bukkit.getCurrentTick();
        if (now % 10 == 0) {
            tickControlledHorses();
        }
        if (now % PERSIST_INTERVAL_TICKS == 0) {
            flush(true);
        }
    }

    private void tickEntrances() {
        if (entrances.isEmpty()) {
            return;
        }
        for (PortalEntrance e : new ArrayList<>(entrances)) {
            e.tick();
        }
        entrances.removeIf(PortalEntrance::isFinished);
    }

    private void checkStrandedBots() {
        for (Bot b : aliveBots()) {
            GamePlayer t = b.target();
            if (t != null && isEligibleTarget(t) && !t.world().equals(b.world()) && b.idle() == null) {
                requestDimensionChange(t, b.bukkitWorld());
            }
        }
    }

    /** Silent deaths: the farthest bot from each player (≥ 200 blocks) disappears and respawns closer. */
    private void tickDistanceManagement() {
        long now = pause.clock();
        if (!pendingSilentDeaths.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = pendingSilentDeaths.entrySet().iterator();
            List<Bot> toKill = new ArrayList<>();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (now < e.getValue()) {
                    continue;
                }
                it.remove();
                Bot b = bots.get(e.getKey());
                if (b != null && b.isAlive()) {
                    toKill.add(b);
                }
            }
            toKill.forEach(this::killSilently);
        }
        // ring bots are left out (b.idle() != null); the others go on with silent deaths
        if (Bukkit.getCurrentTick() % 20 != 0 || !settings().silentDeaths.on()) {
            return;
        }
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            if (!isEligibleTarget(p)) {
                continue;
            }
            Bot farthest = null;
            double max = 0;
            for (Bot b : bots.values()) {
                if (!b.isAlive() || !b.world().equals(p.world()) || pendingSilentDeaths.containsKey(b.id()) || b.idle() != null) {
                    continue;
                }
                double d = b.distanceTo(p);
                if (d > max) {
                    max = d;
                    farthest = b;
                }
            }
            if (farthest != null && max >= MIN_SILENT_DEATH_DISTANCE) {
                double clamped = Math.min(Math.max(max, MIN_SILENT_DEATH_DISTANCE), MAX_SILENT_DEATH_DISTANCE);
                long delay = (long) (10.0 * (MIN_SILENT_DEATH_DISTANCE / clamped) * 20.0);
                pendingSilentDeaths.put(farthest.id(), now + delay);
            }
        }
    }

    // ------------------------------------------------------------------ entity gone without dying

    /** The bot's entity was removed from outside (command, another plugin): the bot comes back in the same place. */
    public void onEntityLost(Bot bot) {
        if (bot.entity() == null || bot.entity().isValid() || bot.entity().isDead()) {
            return;
        }
        Location last = bot.location();
        byEntity.values().remove(bot);
        bot.markDead();
        if (last != null && last.getWorld() != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (bots.containsKey(bot.id()) && !bot.isAlive() && !pendingRespawns.containsKey(bot.id())) {
                    UUID ringId = waitingRing(bot);
                    bot.resetRuntime();
                    bot.spawnEntity(resolveSurface(last));
                    if (ringId != null) {
                        bot.setIdle(new IdleBehavior(bot, ringId));
                    }
                    bot.progression().updateGear();
                    bot.restoreDefaultOffhand();
                }
            });
        }
    }

    /** Removes Mannequins marked as bots that don't belong to any registered bot (orphans). */
    public void removeOrphan(GameEntity e) {
        if (isBotEntity(e) && byEntity(e) == null) {
            e.remove();
        } else if (e instanceof GameHorse h && !controlledHorses.containsKey(h.uuid())
                && PaperEntities.bukkit(h).getPersistentDataContainer().has(horseKey, PersistentDataType.STRING)) {
            // a horse left without AI (the server went down with the bot mounted)
            releaseHorse(h);
        }
    }

    // ------------------------------------------------------------------ persistence

    public void markDirty() {
        dirty = true;
    }

    /** Saves the state of every bot (asynchronously, except on shutdown). */
    public void flush(boolean async) {
        if (!settings().persistentBots.on()) {
            if (dirty) {
                plugin.storage().save(List.of(), new ArrayList<>(groups.values()), null, async);
                dirty = false;
            }
            return;
        }
        if (!dirty && bots.isEmpty()) {
            return;
        }
        List<BotSnapshot> snaps = new ArrayList<>(bots.size());
        for (Bot b : bots.values()) {
            if (b.shouldRespawn() || b.isAlive()) {
                snaps.add(b.snapshot());
            }
        }
        plugin.storage().save(snaps, new ArrayList<>(groups.values()), this::saveManhunt, async);
        dirty = false;
    }

    /** Pause, rings waiting and the hunt in progress (participants, protected players, who was left out). */
    private void saveManhunt(ConfigurationSection s) {
        s.set("pausada", pause.isPaused());
        policy.save(s.createSection("cacada"));
        rings.save(s.createSection("rodas"));
    }

    /** Recreates the saved pause, rings and hunt; called after {@link #restore}. */
    public void restoreManhunt(ConfigurationSection s) {
        if (s == null || !settings().persistentBots.on()) {
            return;
        }
        if (s.getBoolean("pausada")) {
            // before the hunt: the stopped clock applies to the protection it recreates
            pause.pause();
        }
        policy.load(s.getConfigurationSection("cacada"));
        rings.load(s.getConfigurationSection("rodas"));
    }

    public void clearPersistentSnapshots() {
        plugin.storage().save(List.of(), new ArrayList<>(groups.values()), null, true);
    }

    /** Recreates the saved bots when the server starts. */
    public int restore(List<BotSnapshot> snapshots, List<BotGroup> savedGroups) {
        for (BotGroup g : savedGroups) {
            groups.put(g.name().toLowerCase(Locale.ROOT), g);
        }
        if (!settings().persistentBots.on()) {
            return 0;
        }
        int n = 0;
        for (BotSnapshot s : snapshots) {
            if (bots.containsKey(s.id())) {
                continue;
            }
            try {
                Bot bot = new Bot(this, s.id(), s.name(), s.visualName(), s.skin(), s.difficulty());
                bot.applySnapshotState(s);
                if (s.group() != null) {
                    getOrCreateGroup(s.group());
                }
                register(bot);
                World w = s.world() == null ? null : Bukkit.getWorld(s.world());
                if (w == null) {
                    w = Bukkit.getWorlds().getFirst();
                }
                Location loc = new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
                if (s.alive()) {
                    bot.spawnEntity(loc);
                    bot.applySnapshotEquipment(s);
                    bot.progression().updateGear();
                } else if (s.shouldRespawn() && settings().respawn.on()) {
                    // dead at shutdown: only comes back if respawn is on, as in a normal death
                    pendingRespawns.put(bot.id(), new PendingRespawn(bot.id(), pause.clock() + 40, w.getName(), Locations.vec(loc)));
                }
                notePhaseReached(bot.progression().phase());
                n++;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not restore bot " + s.name(), ex);
            }
        }
        return n;
    }

    /** Shutdown: saves everything and takes the entities out of the world (they aren't saved with the world). */
    public void shutdown() {
        flush(false);
        pause.shutdown();
        releaseAllHorses();
        for (Bot b : bots.values()) {
            plugin.glow().remove(b);
            b.despawnEntity();
            plugin.tabList().remove(b);
        }
        rings.cancelAll();
        caveAmbush.clear();
        tactics.clear();
        temporaryBlocks.revertAll();
        entrances.clear();
    }
}
