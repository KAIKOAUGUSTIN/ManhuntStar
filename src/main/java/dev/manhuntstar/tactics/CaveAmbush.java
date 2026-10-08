package dev.manhuntstar.tactics;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;

/**
 * Emboscada em caverna: quando o jogador está numa caverna (Y &lt; 60, sem luz do céu), um bot que o
 * persegue é levado para um ponto escondido da mesma caverna a 50–130 blocos e coloca uma cama
 * vermelha. Enquanto a cama existir e o jogador continuar nessa caverna, o bot renasce ao lado
 * dela (voltando à fase 1 e recuperando uma fase a cada 5 s). Se o jogador sair da caverna ou a
 * cama for quebrada, a emboscada acaba e os bots da caverna morrem.
 */
public final class CaveAmbush {

    private static final int CAVE_MAX_Y = 60;
    private static final int MAX_SCAN_NODES = 6000;
    private static final int MAX_RADIUS = 96;
    /** Sem esconderijo na caverna, espera isso (10 s) antes de procurar de novo, em vez de todo segundo. */
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
        if (Bukkit.getCurrentTick() % 20 != 0) {
            return;
        }
        cleanupInvalid();
        if (!manager.plugin().settings().caveAmbush.on() || manager.countdown().isActive()) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        long cooldown = manager.plugin().settings().caveAmbushCooldown.value() * 20L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!manager.isEligibleTarget(p) || !isInCave(p.getWorld(), Pos.of(p.getLocation()))) {
                continue;
            }
            Long last = lastAmbushTick.get(p.getUniqueId());
            Long retry = nextAttemptTick.get(p.getUniqueId());
            if ((last != null && now - last < cooldown) || (retry != null && now < retry)) {
                continue;
            }
            if (tryStartOne(p)) {
                lastAmbushTick.put(p.getUniqueId(), now);
            } else {
                nextAttemptTick.put(p.getUniqueId(), now + RETRY_TICKS);
            }
        }
    }

    private boolean tryStartOne(Player target) {
        List<Bot> candidates = new ArrayList<>();
        for (Bot b : manager.aliveBots()) {
            if (b.world().equals(target.getWorld()) && target.getUniqueId().equals(b.targetPlayerId())
                    && !beds.containsKey(b.id()) && !botTargets.containsKey(b.id()) && b.idle() == null) {
                candidates.add(b);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        Bot chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        World w = target.getWorld();
        // a caverna do jogador é varrida uma vez só; esconderijo e cama só consultam o resultado
        Set<Long> cave = caveRegion(w, Pos.of(target.getLocation()));
        if (cave == null) {
            return false;
        }
        Pos ambush = findHiddenFarPos(w, target, cave);
        if (ambush == null) {
            return false;
        }
        int restorePhase = chosen.progression().phase();
        Location dest = new Location(w, ambush.x() + 0.5, ambush.y() + 0.1, ambush.z() + 0.5, target.getLocation().getYaw(), target.getLocation().getPitch());
        manager.teleportBot(chosen, dest);
        chosen.setTargetPlayer(target.getUniqueId());
        AmbushBed bed = tryPlaceBed(chosen, w, target, ambush, restorePhase, cave);
        if (bed != null) {
            beds.put(chosen.id(), bed);
            botTargets.put(chosen.id(), target.getUniqueId());
        }
        return true;
    }

    private Pos findHiddenFarPos(World w, Player target, Set<Long> cave) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double distance = 50 + r.nextDouble() * 80;
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int minY = Math.max(w.getMinHeight() + 2, 8);
            int maxY = Math.min(58, w.getMaxHeight() - 3);
            for (int y = maxY; y >= minY; y--) {
                Pos c = new Pos(x, y, z);
                if (!isSafeRespawnSpot(w, c) || WorldUtil.skyLight(w, c) > 0) {
                    continue;
                }
                if (WorldUtil.clearLine(w, Vec3.of(target.getEyeLocation()), Vec3.center(c))) {
                    continue;
                }
                if (inRegion(cave, c)) {
                    return c;
                }
            }
        }
        return null;
    }

    private AmbushBed tryPlaceBed(Bot bot, World w, Player target, Pos near, int restorePhase, Set<Long> cave) {
        if (w.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (int radius = 1; radius <= 5; radius++) {
            for (BlockFace facing : faces) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Pos foot = near.offset(dx, 0, dz);
                        Pos head = foot.relative(facing);
                        if (!canPlaceBed(w, foot, head) || !inRegion(cave, foot)) {
                            continue;
                        }
                        placeBedHalf(w, foot, facing, Bed.Part.FOOT);
                        placeBedHalf(w, head, facing, Bed.Part.HEAD);
                        manager.plugin().pathService().invalidate(w, foot);
                        manager.plugin().pathService().invalidate(w, head);
                        bot.lookAt(Vec3.center(foot));
                        bot.swing();
                        return new AmbushBed(bot.id(), target.getUniqueId(), w.getUID(), foot, head, restorePhase);
                    }
                }
            }
        }
        return null;
    }

    private static void placeBedHalf(World w, Pos p, BlockFace facing, Bed.Part part) {
        Block b = p.block(w);
        Bed data = (Bed) Material.RED_BED.createBlockData();
        data.setFacing(facing);
        data.setPart(part);
        b.setBlockData(data, false);
    }

    private static boolean canPlaceBed(World w, Pos foot, Pos head) {
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
        World w = Bukkit.getWorld(bed.worldId());
        if (w == null) {
            return false;
        }
        return bed.foot().block(w).getBlockData() instanceof Bed f && f.getPart() == Bed.Part.FOOT
                && bed.head().block(w).getBlockData() instanceof Bed h && h.getPart() == Bed.Part.HEAD;
    }

    /** Onde o bot deve renascer (perto da cama) ou null se a emboscada não vale mais. */
    public Location respawnLocation(Bot bot) {
        AmbushBed bed = beds.get(bot.id());
        if (bed == null) {
            return null;
        }
        Player target = Bukkit.getPlayer(bed.targetId());
        World w = Bukkit.getWorld(bed.worldId());
        if (target == null || target.isDead() || w == null || !target.getWorld().equals(w)
                || !isInCave(w, Pos.of(target.getLocation())) || !isBedValid(bed) || !isSameCave(w, Pos.of(target.getLocation()), bed.foot())) {
            removeBed(bot.id());
            botTargets.remove(bot.id());
            return null;
        }
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Pos p = bed.foot().offset(dx, 0, dz);
                    if (isSafeRespawnSpot(w, p)) {
                        return new Location(w, p.x() + 0.5, p.y() + 0.1, p.z() + 0.5);
                    }
                }
            }
        }
        return new Location(w, bed.foot().x() + 0.5, bed.foot().y() + 0.6, bed.foot().z() + 0.5);
    }

    public int restorePhase(Bot bot) {
        AmbushBed bed = beds.get(bot.id());
        return bed == null ? bot.progression().phase() : Math.max(bed.restorePhase(), bot.progression().phase());
    }

    private void cleanupInvalid() {
        List<UUID> toCleanup = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : botTargets.entrySet()) {
            Player target = Bukkit.getPlayer(e.getValue());
            AmbushBed bed = beds.get(e.getKey());
            if (target == null || target.isDead() || bed == null) {
                toCleanup.add(e.getKey());
                continue;
            }
            World w = target.getWorld();
            boolean valid = w.getUID().equals(bed.worldId()) && isInCave(w, Pos.of(target.getLocation()))
                    && isBedValid(bed) && isSameCave(w, Pos.of(target.getLocation()), bed.foot());
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
                if (targetId.equals(b.targetPlayerId()) && isInCave(b.world(), b.blockPos())) {
                    removeBed(b.id());
                    botTargets.remove(b.id());
                    manager.killSilently(b);
                }
            }
        }
    }

    public void onBlockBroken(World w, Pos pos) {
        Iterator<Map.Entry<UUID, AmbushBed>> it = beds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AmbushBed> e = it.next();
            AmbushBed bed = e.getValue();
            if (bed.worldId().equals(w.getUID()) && (bed.foot().equals(pos) || bed.head().equals(pos))) {
                it.remove();
                botTargets.remove(e.getKey());
                removeBedBlocks(bed);
            }
        }
    }

    public boolean isAmbushBed(World w, Pos pos) {
        for (AmbushBed bed : beds.values()) {
            if (bed.worldId().equals(w.getUID()) && (bed.foot().equals(pos) || bed.head().equals(pos))) {
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
        World w = Bukkit.getWorld(bed.worldId());
        if (w == null) {
            return;
        }
        for (Pos p : new Pos[]{bed.foot(), bed.head()}) {
            if (p.block(w).getType() == Material.RED_BED) {
                p.block(w).setType(Material.AIR, false);
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

    // ------------------------------------------------------------------ caverna

    public static boolean isInCave(World w, Pos pos) {
        if (w == null || pos.y() >= CAVE_MAX_Y) {
            return false;
        }
        return WorldUtil.skyLight(w, pos) <= 0 && WorldUtil.skyLight(w, pos.above()) <= 0;
    }

    private static boolean isSafeRespawnSpot(World w, Pos p) {
        if (p.y() >= CAVE_MAX_Y || WorldUtil.skyLight(w, p) > 0) {
            return false;
        }
        return WorldUtil.canBeReplaced(w, p) && WorldUtil.canBeReplaced(w, p.above()) && WorldUtil.blocksMotion(w, p.below())
                && !WorldUtil.isLava(w, p.below()) && !WorldUtil.isLava(w, p);
    }

    /** Busca em largura pelo ar da caverna (até 6000 blocos, raio 96) ligando os dois pontos. */
    public static boolean isSameCave(World w, Pos a, Pos b) {
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
     * O ar da caverna ligado a {@code from}, com os mesmos limites de {@link #isSameCave} (6000 blocos,
     * raio 96), ou null se não houver ar perto. Serve para testar vários pontos com uma busca só.
     */
    private static Set<Long> caveRegion(World w, Pos from) {
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

    /** O ponto está a até 2 blocos do ar da região (o mesmo critério de chegada de {@link #isSameCave}). */
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

    private static Pos normalize(World w, Pos origin) {
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

    private static boolean passable(World w, Pos p) {
        if (p.y() >= CAVE_MAX_Y || !WorldUtil.isLoaded(w, p) || WorldUtil.skyLight(w, p) > 0) {
            return false;
        }
        if (WorldUtil.isLava(w, p)) {
            return false;
        }
        Block b = p.block(w);
        if (b.getType().isAir() || b.isPassable()) {
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
