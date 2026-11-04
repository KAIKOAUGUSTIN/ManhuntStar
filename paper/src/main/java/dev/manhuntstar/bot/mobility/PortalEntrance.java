package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;

/**
 * Bots seguindo o alvo para outra dimensão: depois de 30–60 s (um décimo disso no End), um portal
 * do Nether aparece num lugar que o jogador está vendo e os bots saem dele um a um. Com
 * "entrada-por-portal" desligada, eles chegam escondidos perto do jogador, sem portal.
 */
public final class PortalEntrance {

    private static final int[][] FRAME = {{-1, -1}, {0, -1}, {1, -1}, {2, -1}, {-1, 0}, {-1, 1}, {-1, 2}, {2, 0}, {2, 1}, {2, 2}, {-1, 3}, {0, 3}, {1, 3}, {2, 3}};

    private final BotManager manager;
    private final UUID playerId;
    private final UUID worldId;
    private final List<Bot> bots;
    private int timer;
    private boolean portalSpawned;
    private Pos portalPos;
    private int nextDelay;
    private int index;
    private boolean finished;

    public PortalEntrance(BotManager manager, Player player, List<Bot> bots) {
        this.manager = manager;
        this.playerId = player.getUniqueId();
        this.worldId = player.getWorld().getUID();
        this.bots = new ArrayList<>(bots);
        int min = manager.plugin().settings().minNetherDelay.value() * 20;
        int max = manager.plugin().settings().maxNetherDelay.value() * 20;
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            min /= 10;
            max /= 10;
        }
        timer = max > min ? min + ThreadLocalRandom.current().nextInt(max - min + 1) : min;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID worldId() {
        return worldId;
    }

    public boolean isFinished() {
        return finished;
    }

    public void tick() {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.getWorld().getUID().equals(worldId)) {
            abort();
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        if (!portalSpawned) {
            portalSpawned = true;
            if (manager.plugin().settings().portalEntrance.on()) {
                spawnPortal(player);
            }
            nextDelay = 6 + ThreadLocalRandom.current().nextInt(27);
            return;
        }
        if (index >= bots.size()) {
            finished = true;
            return;
        }
        if (nextDelay > 0) {
            nextDelay--;
            return;
        }
        arrive(bots.get(index++), player);
        nextDelay = 6 + ThreadLocalRandom.current().nextInt(27);
        if (index >= bots.size()) {
            finished = true;
        }
    }

    private void abort() {
        for (Bot b : bots) {
            b.brain().setTeleporting(false);
        }
        finished = true;
    }

    private void arrive(Bot bot, Player player) {
        if (!bot.isAlive()) {
            return;
        }
        World w = player.getWorld();
        Pos spot = portalPos != null ? exitSpot(w, portalPos) : hiddenSpot(w, Pos.of(player.getLocation()));
        Location dest = new Location(w, spot.x() + 0.5, spot.y() + 0.1, spot.z() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        manager.teleportBot(bot, dest);
        bot.brain().setTeleporting(false);
        bot.lookAt(Vec3.of(player.getLocation()));
        w.playSound(dest, portalPos != null ? Sound.BLOCK_PORTAL_TRAVEL : Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1f);
    }

    // ------------------------------------------------------------------ portal

    private void spawnPortal(Player player) {
        World w = player.getWorld();
        portalPos = findPortalLocation(player);
        boolean end = w.getEnvironment() == World.Environment.THE_END;
        if (portalPos == null) {
            portalPos = Pos.of(player.getLocation()).relative(Pos.faceFromYaw(player.getLocation().getYaw()), 15);
        }
        if (end) {
            if (!WorldUtil.blocksMotion(w, portalPos.below())) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Block b = portalPos.offset(x, -1, z).block(w);
                        if (!b.getType().isSolid()) {
                            b.setType(Material.OBSIDIAN);
                        }
                    }
                }
            }
            return;
        }
        buildNetherPortal(w, portalPos);
        manager.plugin().pathService().invalidate(w, portalPos);
    }

    /** Portal 2x3 com moldura de obsidiana no eixo X, chão de obsidiana onde não houver apoio. */
    public static void buildNetherPortal(World w, Pos pos) {
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                Block floor = pos.offset(x, -1, z).block(w);
                if (!floor.getType().isSolid() || floor.getType() == Material.LAVA) {
                    floor.setType(Material.OBSIDIAN);
                }
            }
        }
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                pos.offset(x, y, 0).block(w).setType(Material.AIR, false);
            }
        }
        for (int[] f : FRAME) {
            pos.offset(f[0], f[1], 0).block(w).setType(Material.OBSIDIAN, false);
        }
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                Block b = pos.offset(x, y, 0).block(w);
                b.setType(Material.NETHER_PORTAL, false);
                if (b.getBlockData() instanceof Orientable o) {
                    o.setAxis(Axis.X);
                    b.setBlockData(o, false);
                }
            }
        }
    }

    private Pos findPortalLocation(Player player) {
        World w = player.getWorld();
        Vec3 pp = Vec3.of(player.getLocation());
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
        boolean end = w.getEnvironment() == World.Environment.THE_END;
        for (int dist = 100; dist >= 10; dist -= 5) {
            for (int i = 0; i < 12; i++) {
                double angle = Math.toRadians(i * 30);
                int tx = (int) (pp.x() + Math.cos(angle) * dist);
                int tz = (int) (pp.z() + Math.sin(angle) * dist);
                if (!w.isChunkLoaded(tx >> 4, tz >> 4)) {
                    continue;
                }
                Pos candidate = null;
                if (nether) {
                    int start = Math.max(5, (int) pp.y() - 20);
                    int endY = Math.min(120, (int) pp.y() + 40);
                    for (int y = start; y < endY; y++) {
                        Pos p = new Pos(tx, y, tz);
                        if (WorldUtil.blocksMotion(w, p) && !WorldUtil.isLava(w, p) && WorldUtil.isAir(w, p.above()) && WorldUtil.isAir(w, p.above(2))) {
                            candidate = p.above();
                            break;
                        }
                    }
                } else if (end) {
                    int ty = WorldUtil.surfaceY(w, tx, tz);
                    if (ty > w.getMinHeight() + 1) {
                        candidate = new Pos(tx, ty, tz);
                    }
                } else {
                    candidate = new Pos(tx, WorldUtil.surfaceYNoLeaves(w, tx, tz), tz);
                }
                if (candidate == null || WorldUtil.isLava(w, candidate.below()) || WorldUtil.isLava(w, candidate)
                        || !WorldUtil.blocksMotion(w, candidate.below())) {
                    continue;
                }
                if (visibleTo(player, candidate)) {
                    return candidate;
                }
            }
        }
        return end ? new Pos((int) pp.x() + 5, (int) pp.y(), (int) pp.z() + 5) : null;
    }

    private static boolean visibleTo(Player player, Pos pos) {
        Vec3 eye = Vec3.of(player.getEyeLocation());
        Vec3 center = Vec3.center(pos);
        Vec3 look = Vec3.of(player.getLocation().getDirection());
        if (look.dot(center.subtract(eye).normalize()) < 0.5) {
            return false;
        }
        return WorldUtil.clearLine(player.getWorld(), eye, center.add(0, 1, 0));
    }

    private static Pos exitSpot(World w, Pos base) {
        Pos[] preferred = {base.offset(0, 0, 1), base.offset(1, 0, 1), base.offset(0, 0, -1), base.offset(1, 0, -1), base.offset(-2, 0, 0), base.offset(3, 0, 0)};
        for (Pos c : preferred) {
            if (isSafeSpot(w, c)) {
                return c;
            }
        }
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Pos c = base.offset(dx, 0, dz);
                    if (isSafeSpot(w, c)) {
                        return c;
                    }
                }
            }
        }
        return base.offset(0, 0, 1);
    }

    /** Lugar seguro fora da vista do jogador, a 15–40 blocos (entrada sem portal). */
    private static Pos hiddenSpot(World w, Pos center) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 30; i++) {
            int x = center.x() + r.nextInt(50) - 25;
            int z = center.z() + r.nextInt(50) - 25;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            if (w.getEnvironment() == World.Environment.NETHER) {
                for (int y = Math.min(w.getMaxHeight() - 3, center.y() + 24); y >= Math.max(w.getMinHeight() + 2, center.y() - 24); y--) {
                    Pos c = new Pos(x, y, z);
                    if (isSafeSpot(w, c)) {
                        return c;
                    }
                }
            } else {
                Pos c = new Pos(x, WorldUtil.surfaceY(w, x, z), z);
                if (isSafeSpot(w, c)) {
                    return c;
                }
            }
        }
        return center;
    }

    static boolean isSafeSpot(World w, Pos c) {
        if (!WorldUtil.isSafeStand(w, c)) {
            return false;
        }
        Material feet = c.block(w).getType();
        Material head = c.above().block(w).getType();
        if (feet == Material.NETHER_PORTAL || head == Material.NETHER_PORTAL) {
            return false;
        }
        int open = WorldUtil.countOpenSides(w, c);
        return w.getEnvironment() == World.Environment.NETHER ? open >= 2 : open >= 1;
    }
}
