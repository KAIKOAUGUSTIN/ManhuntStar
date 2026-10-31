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
import dev.manhuntstar.tactics.CaveAmbush;
import dev.manhuntstar.tactics.TacticsManager;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Registro e ciclo de vida de todos os bots: criação (com skin), escolha do jogador alvo, morte,
 * respawn, mortes silenciosas dos bots muito longe, troca de dimensão, salvamento e grupos.
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
        this.temporaryBlocks = new TemporaryBlocks(plugin::pathService);
        this.rings = new RingService(plugin);
        this.policy = new HuntPolicy(plugin);
        this.pause = new PauseService(plugin);
        this.tactics = new TacticsManager(this);
        this.caveAmbush = new CaveAmbush(this);
    }

    // ------------------------------------------------------------------ acesso

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

    /** Quem pode ser caçado (lista de jogadores e quem entra no meio). */
    public HuntPolicy policy() {
        return policy;
    }

    /** Pausa da caçada e o relógio que para junto com ela. */
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

    /** Primeiro bot com esse nome interno ou visual (sem diferenciar maiúsculas). */
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

    /** O bot dono desta entidade (Mannequin), ou null. */
    public Bot byEntity(Entity e) {
        if (!(e instanceof Mannequin)) {
            return null;
        }
        Bot b = byEntity.get(e.getUniqueId());
        if (b != null && b.entity() != null && b.entity().getUniqueId().equals(e.getUniqueId())) {
            return b;
        }
        return null;
    }

    public boolean isBotEntity(Entity e) {
        return e instanceof Mannequin && e.getPersistentDataContainer().has(botKey, PersistentDataType.STRING);
    }

    void registerEntity(Bot bot, Mannequin m) {
        byEntity.values().remove(bot);
        if (m != null) {
            byEntity.put(m.getUniqueId(), bot);
        }
    }

    // ------------------------------------------------------------------ grupos

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

    /** Outro bot que não é de um grupo inimigo (o golpe de varredura não acerta aliados). */
    public boolean isAlly(Bot bot, Entity other) {
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

    // ------------------------------------------------------------------ alvo

    /**
     * Jogador que os bots podem caçar agora: vivo, fora do espectador e liberado pela lista de
     * jogadores e pela regra de quem entra no meio. O alvo forçado (/manhunt target) passa sempre.
     */
    public boolean isEligibleTarget(Player p) {
        return isActivePlayer(p) && (p.getUniqueId().equals(forcedTarget) || policy.canBeHunted(p));
    }

    /** Online, vivo e fora do modo espectador. */
    public static boolean isActivePlayer(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    public UUID forcedTarget() {
        return forcedTarget;
    }

    public void setForcedTarget(UUID id) {
        forcedTarget = id;
    }

    /** Jogador alvo do bot: forçado → regra do modo (mais perto, dividido igualmente, mais longe). */
    public Player resolvePlayerTarget(Bot bot) {
        if (!isTrackingPlayers(bot)) {
            return null;
        }
        if (forcedTarget != null) {
            Player forced = Bukkit.getPlayer(forcedTarget);
            if (isEligibleTarget(forced)) {
                return forced;
            }
        }
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEligibleTarget(p)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        List<Player> same = new ArrayList<>();
        for (Player p : candidates) {
            if (p.getWorld().equals(bot.world())) {
                same.add(p);
            }
        }
        List<Player> pool = same.isEmpty() ? candidates : same;
        TargetMode mode = settings().targetMode.get();
        return switch (mode) {
            case CLOSEST_PLAYER -> byDistance(bot, pool, true);
            case FARTHEST_PLAYER -> byDistance(bot, pool, false);
            case EQUAL_BOTS_PER_PLAYER -> balanced(bot, pool);
        };
    }

    private static double dist(Bot bot, Player p) {
        return p.getWorld().equals(bot.world()) ? bot.distanceTo(p) : 1.0E7;
    }

    private static Player byDistance(Bot bot, List<Player> pool, boolean closest) {
        Player chosen = null;
        double best = closest ? Double.MAX_VALUE : -1;
        for (Player p : pool) {
            double d = dist(bot, p);
            if (closest ? d < best : d > best) {
                best = d;
                chosen = p;
            }
        }
        return chosen;
    }

    /** Divide os bots igualmente entre os jogadores, trocando para um mais perto só se ganhar ≥ 2,5 blocos (≤ 10). */
    private Player balanced(Bot bot, List<Player> pool) {
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, Player> byId = new HashMap<>();
        for (Player p : pool) {
            counts.put(p.getUniqueId(), 0);
            byId.put(p.getUniqueId(), p);
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
        Player closest = byDistance(bot, pool, true);
        double closestDist = closest == null ? Double.MAX_VALUE : dist(bot, closest);
        UUID currentId = bot.targetPlayerId();
        if (currentId != null && counts.containsKey(currentId) && counts.get(currentId) <= min + 1) {
            Player current = byId.get(currentId);
            if (closest != null && !closest.getUniqueId().equals(currentId)) {
                double gain = dist(bot, current) - closestDist;
                if (closestDist <= 10.0 && gain >= 2.5) {
                    return closest;
                }
            }
            return current;
        }
        Player best = null;
        int bestCount = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (Player p : pool) {
            int c = counts.getOrDefault(p.getUniqueId(), 0);
            double d = dist(bot, p);
            if (c < bestCount || (c == bestCount && d < bestDist)) {
                best = p;
                bestCount = c;
                bestDist = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ fases

    /** @return true se for a maior fase já alcançada por um bot (anunciar no chat). */
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

    // ------------------------------------------------------------------ cavalos

    public void markHorseInaccessible(Horse horse, int ticks) {
        inaccessibleHorses.put(horse.getUniqueId(), (long) Bukkit.getCurrentTick() + ticks);
    }

    public boolean isHorseInaccessible(Horse horse) {
        Long until = inaccessibleHorses.get(horse.getUniqueId());
        return until != null && until > Bukkit.getCurrentTick();
    }

    /**
     * Desliga a IA do cavalo enquanto o bot monta. No jogo só um jogador comanda o cavalo; com
     * outro cavaleiro ele continua passeando, pastando e virando sozinho e briga com o bot.
     * A marca no cavalo garante que a IA volte mesmo se o servidor cair com o bot montado.
     */
    public void takeHorseControl(Bot bot, AbstractHorse horse) {
        Bot.keepActive(horse);
        if (controlledHorses.containsKey(horse.getUniqueId()) && !horse.isAware()) {
            return;
        }
        horse.getPathfinder().stopPathfinding();
        horse.setAware(false);
        horse.getPersistentDataContainer().set(horseKey, PersistentDataType.STRING, bot.id().toString());
        controlledHorses.put(horse.getUniqueId(), bot.id());
    }

    /** Devolve o cavalo à própria IA. */
    public void releaseHorse(AbstractHorse horse) {
        controlledHorses.remove(horse.getUniqueId());
        if (horse.getPersistentDataContainer().has(horseKey, PersistentDataType.STRING)) {
            horse.getPersistentDataContainer().remove(horseKey);
            horse.setAware(true);
            horse.setPortalCooldown(0);
        }
    }

    /** Solta cavalos cujo bot desmontou, morreu ou foi removido sem passar pela rotina do cavalo. */
    private void tickControlledHorses() {
        if (controlledHorses.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(controlledHorses.entrySet())) {
            Entity horse = Bukkit.getEntity(e.getKey());
            if (!(horse instanceof AbstractHorse h)) {
                // descarregado: a marca é tratada quando o chunk voltar (removeOrphan)
                controlledHorses.remove(e.getKey());
                continue;
            }
            Bot b = bots.get(e.getValue());
            boolean ridden = b != null && b.isAlive() && b.entity() != null && h.getPassengers().contains(b.entity());
            if (!ridden || !h.isValid()) {
                releaseHorse(h);
            }
        }
    }

    private void releaseAllHorses() {
        for (UUID id : new ArrayList<>(controlledHorses.keySet())) {
            if (Bukkit.getEntity(id) instanceof AbstractHorse h) {
                releaseHorse(h);
            }
        }
        controlledHorses.clear();
    }

    // ------------------------------------------------------------------ criação

    /**
     * Cria {@code count} bots no local. Nomes: "nome1, nome2..." ou "%" trocado pelo número;
     * o nome visível é o nome sem "%". Com o modo MANHUNT eles nascem em roda em volta do local
     * (que é o centro da roda) e esperam até alguém sair dela.
     */
    public CompletableFuture<List<Bot>> createBots(Location loc, String name, String skinName, int count,
                                                   BotDifficulty difficulty, int startPhase, int aggression) {
        String skinTarget = skinName == null || skinName.isBlank() ? visualNameOf(name) : skinName;
        return plugin.skins().resolve(skinTarget).thenApplyAsync(skin -> spawnBots(loc, name, skin, count, difficulty, startPhase, aggression),
                r -> Bukkit.getScheduler().runTask(plugin, r));
    }

    /**
     * Centro da roda para bots criados em {@code given} (posição de quem criou): no modo manual, o
     * ponto marcado com /manhunt ring point; sem ponto (ou com o mundo dele descarregado), o próprio local.
     */
    public Location ringCenterFor(Location given) {
        if (!manualRingPointInUse()) {
            return given;
        }
        Settings.RingPoint p = settings().ringPoint();
        return new Location(Bukkit.getWorld(p.world()), p.x(), p.y(), p.z());
    }

    /** O modo manual está ligado mas não há ponto da roda utilizável. */
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

    /** Raio da roda: o da opção, ou 5 blocos + 1 a cada 5 bots com a opção em 0. */
    private double ringRadius(int count) {
        double fixed = settings().ringRadius.value();
        return fixed <= 0 ? 5.0 + count / 5.0 : Math.max(2.0, fixed);
    }

    private List<Bot> spawnBots(Location pos, String name, SkinData skin, int count, BotDifficulty difficulty, int startPhase, int aggression) {
        List<Bot> created = new ArrayList<>();
        World world = pos.getWorld();
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
                // de frente para o centro da roda
                spawn.setYaw(Vec3.of(pos).subtract(Vec3.of(spawn)).yaw());
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
            // sem roda os bots já caçam
            policy.beginIfIdle();
        }
        dirty = true;
        return created;
    }

    /** Altura do chão perto de um ponto da roda (para não nascer dentro de blocos nem no ar). */
    private static double surfaceNear(World w, Location spawn, double refY) {
        Pos p = Pos.of(spawn);
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

    /** Se o ponto cair dentro de blocos, sobe até o primeiro espaço livre (até 16 blocos). */
    private static double freeSpaceAbove(World w, Location spawn) {
        Pos p = Pos.of(spawn);
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

    // ------------------------------------------------------------------ teleporte

    /** Teleporta o bot (no mesmo mundo ou para outro, recriando a entidade) e estabiliza a IA. */
    public void teleportBot(Bot bot, Location dest) {
        if (!bot.isAlive()) {
            return;
        }
        if (!bot.world().equals(dest.getWorld())) {
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

    /** Leva os bots vivos com esse nome até o jogador (como remove e skin, vale para todos). @return quantos foram */
    public int teleportBotsToPlayer(String name, Player target) {
        List<Bot> alive = new ArrayList<>();
        for (Bot b : botsNamed(name)) {
            if (b.isAlive()) {
                alive.add(b);
            }
        }
        for (Bot b : alive) {
            teleportBot(b, alive.size() == 1 ? target.getLocation() : nearPlayer(target));
        }
        return alive.size();
    }

    public int teleportAllTo(Player target) {
        int n = 0;
        for (Bot b : aliveBots()) {
            teleportBot(b, nearPlayer(target));
            n++;
        }
        return n;
    }

    /** Perto do jogador, espalhado um pouco para os bots não ficarem um dentro do outro. */
    private static Location nearPlayer(Player target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return target.getLocation().add(r.nextDouble(-2, 2), 0, r.nextDouble(-2, 2));
    }

    /** Alvo foi para outra dimensão: abre uma entrada por portal para os bots que o seguem. */
    public void requestDimensionChange(Player target, World from) {
        for (PortalEntrance e : entrances) {
            if (e.playerId().equals(target.getUniqueId()) && e.worldId().equals(target.getWorld().getUID())) {
                return;
            }
        }
        List<Bot> stuck = new ArrayList<>();
        for (Bot b : aliveBots()) {
            // bots esperando numa roda ficam nela até a largada
            if (b.world().equals(from) && target.getUniqueId().equals(b.targetPlayerId()) && b.idle() == null) {
                stuck.add(b);
                b.brain().setTeleporting(true);
            }
        }
        if (!stuck.isEmpty()) {
            entrances.add(new PortalEntrance(this, target, stuck));
        }
    }

    // ------------------------------------------------------------------ morte e respawn

    /** Morte do bot (evento de morte da entidade): drops, mensagem, API e respawn. */
    public void handleDeath(Bot bot, EntityDeathEvent event) {
        Mannequin e = bot.entity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        List<ItemStack> drops = bot.inventory().collectDeathDrops(settings().dropInventory.on(), settings().gearPersistence.get());
        event.getDrops().addAll(drops);
        if (settings().deathMessages.on() && e != null) {
            Boolean show = e.getWorld().getGameRuleValue(org.bukkit.GameRules.SHOW_DEATH_MESSAGES);
            if (!Boolean.FALSE.equals(show)) {
                Bukkit.broadcast(plugin.deathMessages().build(bot, event.getDamageSource()));
            }
        }
        Entity killer = event.getDamageSource().getCausingEntity();
        Bukkit.getPluginManager().callEvent(new BotEvents.BotDeathEvent(bot, event.getDamageSource()));
        if (killer instanceof Player p) {
            Bukkit.getPluginManager().callEvent(new BotEvents.BotKilledByPlayerEvent(bot, p));
        } else {
            Bot killerBot = byEntity(killer);
            if (killerBot != null) {
                killerBot.incrementKills();
            }
        }
        Location deathLoc = e == null ? bot.location() : e.getLocation();
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

    /** Morte silenciosa: some sem mensagem nem drops e renasce como numa morte normal. */
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
        Vec3 pos = deathLoc == null ? bot.lastKnownPos() : Vec3.of(deathLoc);
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
        // bot da roda que morreu antes da largada volta para a roda, e não já caçando
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

    /** A roda em que o bot ainda espera (também morto, antes de renascer), ou null. */
    private UUID waitingRing(Bot bot) {
        IdleBehavior idle = bot.idle();
        return idle != null && idle.isActive() && rings.ring(idle.ringId()) != null ? idle.ringId() : null;
    }

    private static World deathWorld(PendingRespawn p) {
        World w = p.world() == null ? null : Bukkit.getWorld(p.world());
        return w == null ? Bukkit.getWorlds().getFirst() : w;
    }

    /** No chão, onde o bot morreu (para o bot da roda, a própria roda). */
    private static Location deathSpot(PendingRespawn p) {
        World w = deathWorld(p);
        Vec3 pos = p.deathPos() == null ? Vec3.of(w.getSpawnLocation()) : p.deathPos();
        return resolveSurface(pos.toLocation(w));
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
                return resolveSurface(g.spawnpoint().toLocation(gw));
            }
        }
        Player target = bot.target();
        if (target != null && isEligibleTarget(target)) {
            Location hidden = findHiddenLocation(target);
            if (hidden != null) {
                return resolveSurface(hidden);
            }
        }
        return deathSpot(p);
    }

    /** Lugar de 100–200 blocos do alvo, fora da linha de visão dele (só em chunks carregados). */
    private static Location findHiddenLocation(Player target) {
        World w = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double distance = i < 20 ? 100 + r.nextInt(100) : 40 + r.nextInt(60);
            int x = (int) (target.getX() + Math.cos(angle) * distance);
            int z = (int) (target.getZ() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = w.getEnvironment() == World.Environment.NETHER ? (int) target.getY() : WorldUtil.surfaceY(w, x, z);
            Vec3 candidate = new Vec3(x + 0.5, y + 1, z + 0.5);
            if (!WorldUtil.clearLine(w, Vec3.of(target.getEyeLocation()), candidate)) {
                return candidate.toLocation(w);
            }
        }
        return null;
    }

    /** Chão seguro perto do ponto (anéis de até 24 blocos); no Nether procura também em altura. */
    public static Location resolveSurface(Location anchor) {
        World w = anchor.getWorld();
        int bx = anchor.getBlockX();
        int bz = anchor.getBlockZ();
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
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
                        for (int y = Math.min(w.getMaxHeight() - 4, Math.min(118, baseY + 20)); y >= Math.max(w.getMinHeight() + 3, baseY - 20); y--) {
                            Pos feet = new Pos(x, y, z);
                            if (WorldUtil.isSafeStand(w, feet) && WorldUtil.countOpenSides(w, feet) >= 2) {
                                return new Location(w, x + 0.5, y + 0.1, z + 0.5, anchor.getYaw(), 0);
                            }
                        }
                    } else {
                        int y = w.getEnvironment() == World.Environment.NORMAL ? WorldUtil.surfaceYNoLeaves(w, x, z) : WorldUtil.surfaceY(w, x, z);
                        Pos feet = new Pos(x, y, z);
                        if (WorldUtil.isSafeStand(w, feet) && WorldUtil.countOpenSides(w, feet) >= 1) {
                            return new Location(w, x + 0.5, y + 0.1, z + 0.5, anchor.getYaw(), 0);
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

    // ------------------------------------------------------------------ remoção

    /** Remove o bot de vez (sem respawn), como um jogador saindo do servidor. */
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
     * Caçada pausada: cada bot fica parado onde estava e não pensa; rodas, caçada, táticas,
     * renascimentos e chegadas por portal esperam. Seguem só os blocos temporários, a checagem dos
     * cavalos e o salvamento.
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
            Player t = b.target();
            if (t != null && isEligibleTarget(t) && !t.getWorld().equals(b.world()) && b.idle() == null) {
                requestDimensionChange(t, b.world());
            }
        }
    }

    /** Mortes silenciosas: o bot mais longe de cada jogador (≥ 200 blocos) some e renasce mais perto. */
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
        // bots da roda ficam de fora (b.idle() != null); os outros seguem com as mortes silenciosas
        if (Bukkit.getCurrentTick() % 20 != 0 || !settings().silentDeaths.on()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isEligibleTarget(p)) {
                continue;
            }
            Bot farthest = null;
            double max = 0;
            for (Bot b : bots.values()) {
                if (!b.isAlive() || !b.world().equals(p.getWorld()) || pendingSilentDeaths.containsKey(b.id()) || b.idle() != null) {
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

    // ------------------------------------------------------------------ entidade sumiu sem morrer

    /** A entidade do bot foi removida por fora (comando, outro plugin): o bot volta no mesmo lugar. */
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

    /** Remove Mannequins marcados como bot que não pertencem a nenhum bot registrado (órfãos). */
    public void removeOrphan(Entity e) {
        if (isBotEntity(e) && byEntity(e) == null) {
            e.remove();
        } else if (e instanceof AbstractHorse h && !controlledHorses.containsKey(h.getUniqueId())
                && h.getPersistentDataContainer().has(horseKey, PersistentDataType.STRING)) {
            // cavalo que ficou sem IA (servidor caiu com o bot montado)
            releaseHorse(h);
        }
    }

    // ------------------------------------------------------------------ persistência

    public void markDirty() {
        dirty = true;
    }

    /** Salva o estado de todos os bots (assíncrono, a não ser no desligamento). */
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

    /** Pausa, rodas esperando e a caçada em andamento (participantes, protegidos, quem ficou de fora). */
    private void saveManhunt(ConfigurationSection s) {
        s.set("pausada", pause.isPaused());
        policy.save(s.createSection("cacada"));
        rings.save(s.createSection("rodas"));
    }

    /** Recria a pausa, as rodas e a caçada salvas; chamado depois de {@link #restore}. */
    public void restoreManhunt(ConfigurationSection s) {
        if (s == null || !settings().persistentBots.on()) {
            return;
        }
        if (s.getBoolean("pausada")) {
            // antes da caçada: o relógio parado vale para a proteção que ela recria
            pause.pause();
        }
        policy.load(s.getConfigurationSection("cacada"));
        rings.load(s.getConfigurationSection("rodas"));
    }

    public void clearPersistentSnapshots() {
        plugin.storage().save(List.of(), new ArrayList<>(groups.values()), null, true);
    }

    /** Recria os bots salvos ao ligar o servidor. */
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
                    // morto ao desligar: só volta se o respawn estiver ligado, como numa morte normal
                    pendingRespawns.put(bot.id(), new PendingRespawn(bot.id(), pause.clock() + 40, w.getName(), Vec3.of(loc)));
                }
                notePhaseReached(bot.progression().phase());
                n++;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not restore bot " + s.name(), ex);
            }
        }
        return n;
    }

    /** Desligamento: salva tudo e tira as entidades do mundo (elas não são salvas com o mundo). */
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
