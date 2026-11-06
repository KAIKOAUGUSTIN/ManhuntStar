package dev.manhuntstar.tactics;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.BedPart;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cave ambush: when the player is in a cave (Y &lt; 60, no skylight), a bot chasing them is taken to a
 * hidden spot in the same cave 50–130 blocks away and places a red bed. While the bed exists and the
 * player stays in that cave, the bot respawns next to it (back to phase 1 and regaining a phase every
 * 5 s). If the player leaves the cave or the bed is broken, the ambush ends and the cave's bots die.
 */
public final class CaveAmbush {

    private static final int CAVE_MAX_Y = 60;
    private static final int MAX_SCAN_NODES = 6000;
    private static final int MAX_RADIUS = 96;
    /** With no hiding spot in the cave, waits this long (10 s) before looking again, instead of every second. */
    private static final int RETRY_TICKS = 200;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public record AmbushBed(UUID botId, UUID targetId, UUID worldId, Pos foot, Pos head, int restorePhase) {
    }

    private final BotManager manager;
    private final Map<UUID, AmbushBed> beds = new HashMap<>();
    private final Map<UUID, UUID> botTargets = new HashMap<>();
    private final Map<UUID, Long> lastAmbushTick = new HashMap<>();
    private final Map<UUID, Long> nextAttemptTick = new HashMap<>();

    public CaveAmbush(BotManager manager) {
        this.manager = manager;
    }

    public boolean isReserved(Bot bot) {
        return botTargets.containsKey(bot.id());
    }

    public void tick() {
        if (Platform.server().currentTick() % 20 != 0) {
            return;
        }
        cleanupInvalid();
        // ring bots are left out in tryStartOne; the ones already hunting keep ambushing
        if (!manager.plugin().settings().caveAmbush.on()) {
            return;
        }
        long now = Platform.server().currentTick();
        long cooldown = manager.plugin().settings().caveAmbushCooldown.value() * 20L;
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            if (!manager.isEligibleTarget(p) || !isInCave(PaperWorld.of(PaperWorld.bukkit(p.world())), p.blockPos())) {
                continue;
            }
            Long last = lastAmbushTick.get(p.uuid());
            Long retry = nextAttemptTick.get(p.uuid());
            if ((last != null && now - last < cooldown) || (retry != null && now < retry)) {
                continue;
            }
            if (tryStartOne(p)) {
                lastAmbushTick.put(p.uuid(), now);
            } else {
                nextAttemptTick.put(p.uuid(), now + RETRY_TICKS);
            }
        }
    }

    private boolean tryStartOne(GamePlayer target) {
        List<Bot> candidates = new ArrayList<>();
        for (Bot b : manager.aliveBots()) {
            if (b.world().equals(target.world()) && target.uuid().equals(b.targetPlayerId())
                    && !beds.containsKey(b.id()) && !botTargets.containsKey(b.id()) && b.idle() == null) {
                candidates.add(b);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        Bot chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        GameWorld w = PaperWorld.of(PaperWorld.bukkit(target.world()));
        // the player's cave is scanned only once; hiding spot and bed only look up the result
        Set<Long> cave = caveRegion(w, target.blockPos());
        if (cave == null) {
            return false;
        }
        Pos ambush = findHiddenFarPos(w, target, cave);
        if (ambush == null) {
            return false;
        }
        int restorePhase = chosen.progression().phase();
        Place dest = new Place(w, ambush.x() + 0.5, ambush.y() + 0.1, ambush.z() + 0.5, target.yaw(), target.pitch());
        manager.teleportBot(chosen, dest);
        chosen.setTargetPlayer(target.uuid());
        AmbushBed bed = tryPlaceBed(chosen, w, target, ambush, restorePhase, cave);
        if (bed != null) {
            beds.put(chosen.id(), bed);
            botTargets.put(chosen.id(), target.uuid());
        }
        return true;
    }

    private Pos findHiddenFarPos(GameWorld w, GamePlayer target, Set<Long> cave) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double distance = 50 + r.nextDouble() * 80;
            int x = (int) Math.floor(target.position().x() + Math.cos(angle) * distance);
            int z = (int) Math.floor(target.position().z() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int minY = Math.max(w.minHeight() + 2, 8);
            int maxY = Math.min(58, w.maxHeight() - 3);
            for (int y = maxY; y >= minY; y--) {
                Pos c = new Pos(x, y, z);
                if (!isSafeRespawnSpot(w, c) || WorldUtil.skyLight(w, c) > 0) {
                    continue;
                }
                if (WorldUtil.clearLine(w, target.eyePosition(), Vec3.center(c))) {
                    continue;
                }
                if (inRegion(cave, c)) {
                    return c;
                }
            }
        }
        return null;
    }

    private AmbushBed tryPlaceBed(Bot bot, GameWorld w, GamePlayer target, Pos near, int restorePhase, Set<Long> cave) {
        if (w.dimension() != Dimension.NORMAL) {
            return null;
        }
        Face[] faces = {Face.NORTH, Face.EAST, Face.SOUTH, Face.WEST};
        for (int radius = 1; radius <= 5; radius++) {
            for (Face facing : faces) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Pos foot = near.offset(dx, 0, dz);
                        Pos head = foot.relative(facing);
                        if (!canPlaceBed(w, foot, head) || !inRegion(cave, foot)) {
                            continue;
                        }
                        w.block(foot).setBed(Blocks.RED_BED, facing, BedPart.FOOT);
                        w.block(head).setBed(Blocks.RED_BED, facing, BedPart.HEAD);
                        manager.plugin().pathService().invalidate(w, foot);
                        manager.plugin().pathService().invalidate(w, head);
                        bot.lookAt(Vec3.center(foot));
                        bot.swing();
                        return new AmbushBed(bot.id(), target.uuid(), w.id(), foot, head, restorePhase);
                    }
                }
            }
        }
        return null;
    }

    private static boolean canPlaceBed(GameWorld w, Pos foot, Pos head) {
        if (foot.y() >= CAVE_MAX_Y || head.y() >= CAVE_MAX_Y) {
            return false;
        }
        if (WorldUtil.skyLight(w, foot) > 0 || WorldUtil.skyLight(w, head) > 0) {
            return false;
        }
        if (!WorldUtil.canBeReplaced(w, foot) || !WorldUtil.canBeReplaced(w, head)) {
            return false;
        }
        if (WorldUtil.hasFluid(w, foot) || WorldUtil.hasFluid(w, head)) {
            return false;
        }
        return WorldUtil.blocksMotion(w, foot.below()) && WorldUtil.blocksMotion(w, head.below())
                && !WorldUtil.isLava(w, foot.below()) && !WorldUtil.isLava(w, head.below());
    }

    private boolean isBedValid(AmbushBed bed) {
        GameWorld w = manager.plugin().server().world(bed.worldId());
        if (w == null) {
            return false;
        }
        return w.block(bed.foot()).bedPart() == BedPart.FOOT && w.block(bed.head()).bedPart() == BedPart.HEAD;
    }

    /** Where the bot should respawn (near the bed) or null if the ambush no longer applies. */
    public Place respawnLocation(Bot bot) {
        AmbushBed bed = beds.get(bot.id());
        if (bed == null) {
            return null;
        }
        GamePlayer target = Platform.server().player(bed.targetId());
        GameWorld w = manager.plugin().server().world(bed.worldId());
        if (target == null || target.isDead() || w == null || !target.world().equals(w)
                || !isInCave(w, target.blockPos()) || !isBedValid(bed) || !isSameCave(w, target.blockPos(), bed.foot())) {
            removeBed(bot.id());
            botTargets.remove(bot.id());
            return null;
        }
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Pos p = bed.foot().offset(dx, 0, dz);
                    if (isSafeRespawnSpot(w, p)) {
                        return new Place(w, p.x() + 0.5, p.y() + 0.1, p.z() + 0.5);
                    }
                }
            }
        }
        return new Place(w, bed.foot().x() + 0.5, bed.foot().y() + 0.6, bed.foot().z() + 0.5);
    }

    public int restorePhase(Bot bot) {
        AmbushBed bed = beds.get(bot.id());
        return bed == null ? bot.progression().phase() : Math.max(bed.restorePhase(), bot.progression().phase());
    }

    private void cleanupInvalid() {
        List<UUID> toCleanup = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : botTargets.entrySet()) {
            GamePlayer target = Platform.server().player(e.getValue());
            AmbushBed bed = beds.get(e.getKey());
            if (target == null || target.isDead() || bed == null) {
                toCleanup.add(e.getKey());
                continue;
            }
            GameWorld w = PaperWorld.of(PaperWorld.bukkit(target.world()));
            boolean valid = w.id().equals(bed.worldId()) && isInCave(w, target.blockPos())
                    && isBedValid(bed) && isSameCave(w, target.blockPos(), bed.foot());
            if (!valid) {
                toCleanup.add(e.getKey());
            }
        }
        Set<UUID> targetsToKill = new HashSet<>();
        for (UUID botId : toCleanup) {
            UUID t = botTargets.remove(botId);
            removeBed(botId);
            if (t != null) {
                targetsToKill.add(t);
            }
        }
        for (UUID targetId : targetsToKill) {
            for (Bot b : new ArrayList<>(manager.aliveBots())) {
                // bots waiting in a ring (even underground) aren't part of the ambush
                if (targetId.equals(b.targetPlayerId()) && b.idle() == null && isInCave(b.world(), b.blockPos())) {
                    removeBed(b.id());
                    botTargets.remove(b.id());
                    manager.killSilently(b);
                }
            }
        }
    }

    public void onBlockBroken(GameWorld w, Pos pos) {
        Iterator<Map.Entry<UUID, AmbushBed>> it = beds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AmbushBed> e = it.next();
            AmbushBed bed = e.getValue();
            if (bed.worldId().equals(w.id()) && (bed.foot().equals(pos) || bed.head().equals(pos))) {
                it.remove();
                botTargets.remove(e.getKey());
                removeBedBlocks(bed);
            }
        }
    }

    public boolean isAmbushBed(GameWorld w, Pos pos) {
        for (AmbushBed bed : beds.values()) {
            if (bed.worldId().equals(w.id()) && (bed.foot().equals(pos) || bed.head().equals(pos))) {
                return true;
            }
        }
        return false;
    }

    public void forget(UUID botId) {
        removeBed(botId);
        botTargets.remove(botId);
    }

    private void removeBed(UUID botId) {
        AmbushBed bed = beds.remove(botId);
        if (bed != null) {
            removeBedBlocks(bed);
        }
    }

    private static void removeBedBlocks(AmbushBed bed) {
        GameWorld w = Platform.server().world(bed.worldId());
        if (w == null) {
            return;
        }
        for (Pos p : new Pos[]{bed.foot(), bed.head()}) {
            if (w.block(p).type() == Blocks.RED_BED) {
                w.block(p).setType(Blocks.AIR, false);
            }
        }
    }

    public void clear() {
        for (UUID id : new ArrayList<>(beds.keySet())) {
            removeBed(id);
        }
        botTargets.clear();
        lastAmbushTick.clear();
        nextAttemptTick.clear();
    }

    // ------------------------------------------------------------------ cave

    public static boolean isInCave(GameWorld w, Pos pos) {
        if (w == null || pos.y() >= CAVE_MAX_Y) {
            return false;
        }
        return WorldUtil.skyLight(w, pos) <= 0 && WorldUtil.skyLight(w, pos.above()) <= 0;
    }

    private static boolean isSafeRespawnSpot(GameWorld w, Pos p) {
        if (p.y() >= CAVE_MAX_Y || WorldUtil.skyLight(w, p) > 0) {
            return false;
        }
        return WorldUtil.canBeReplaced(w, p) && WorldUtil.canBeReplaced(w, p.above()) && WorldUtil.blocksMotion(w, p.below())
                && !WorldUtil.isLava(w, p.below()) && !WorldUtil.isLava(w, p);
    }

    /** Breadth-first search through the cave's air (up to 6000 blocks, radius 96) connecting the two points. */
    public static boolean isSameCave(GameWorld w, Pos a, Pos b) {
        if (w == null || a == null || b == null || !within(a, b, MAX_RADIUS)) {
            return false;
        }
        Pos start = normalize(w, a);
        Pos target = normalize(w, b);
        if (start == null || target == null) {
            return false;
        }
        if (start.equals(target)) {
            return true;
        }
        ArrayDeque<Pos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        open.add(start);
        visited.add(start.asLong());
        while (!open.isEmpty() && visited.size() < MAX_SCAN_NODES) {
            Pos cur = open.poll();
            if (Math.abs(cur.x() - target.x()) <= 2 && Math.abs(cur.y() - target.y()) <= 2 && Math.abs(cur.z() - target.z()) <= 2) {
                return true;
            }
            for (int[] d : DIRS) {
                Pos next = cur.offset(d[0], d[1], d[2]);
                if (visited.contains(next.asLong()) || !within(a, next, MAX_RADIUS) || !passable(w, next)) {
                    continue;
                }
                visited.add(next.asLong());
                open.add(next);
            }
        }
        return false;
    }

    /**
     * The cave air connected to {@code from}, with the same limits as {@link #isSameCave} (6000 blocks,
     * radius 96), or null if there is no air nearby. Used to test several points with a single search.
     */
    private static Set<Long> caveRegion(GameWorld w, Pos from) {
        Pos start = normalize(w, from);
        if (start == null) {
            return null;
        }
        ArrayDeque<Pos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        open.add(start);
        visited.add(start.asLong());
        while (!open.isEmpty() && visited.size() < MAX_SCAN_NODES) {
            Pos cur = open.poll();
            for (int[] d : DIRS) {
                Pos next = cur.offset(d[0], d[1], d[2]);
                if (visited.contains(next.asLong()) || !within(from, next, MAX_RADIUS) || !passable(w, next)) {
                    continue;
                }
                visited.add(next.asLong());
                open.add(next);
            }
        }
        return visited;
    }

    /** The point is within 2 blocks of the region's air (the same arrival rule as {@link #isSameCave}). */
    private static boolean inRegion(Set<Long> region, Pos p) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (region.contains(Pos.pack(p.x() + dx, p.y() + dy, p.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Pos normalize(GameWorld w, Pos origin) {
        if (passable(w, origin)) {
            return origin;
        }
        for (int r = 1; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        Pos c = origin.offset(dx, dy, dz);
                        if (passable(w, c)) {
                            return c;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean passable(GameWorld w, Pos p) {
        if (p.y() >= CAVE_MAX_Y || !WorldUtil.isLoaded(w, p) || WorldUtil.skyLight(w, p) > 0) {
            return false;
        }
        if (WorldUtil.isLava(w, p)) {
            return false;
        }
        GameBlock b = w.block(p);
        if (b.type().is(BlockTrait.AIR) || b.isPassable()) {
            return true;
        }
        return b.isReplaceable() || WorldUtil.isWater(w, p);
    }

    private static boolean within(Pos o, Pos p, int radius) {
        long dx = p.x() - o.x();
        long dy = p.y() - o.y();
        long dz = p.z() - o.z();
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }
}
