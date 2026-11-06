package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.SoundCategory;

/**
 * Bots following the target to another dimension: after 30–60 s (a tenth of that in the End), a Nether
 * portal appears somewhere the player can see and the bots come out of it one by one. With
 * {@code tactics.portal-entrance} off, they arrive hidden near the player, without a portal.
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

    public PortalEntrance(BotManager manager, GamePlayer player, List<Bot> bots) {
        this.manager = manager;
        this.playerId = player.uuid();
        this.worldId = player.world().id();
        this.bots = new ArrayList<>(bots);
        int min = manager.plugin().settings().minNetherDelay.value() * 20;
        int max = manager.plugin().settings().maxNetherDelay.value() * 20;
        if (player.world().dimension() == Dimension.THE_END) {
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
        GamePlayer player = Platform.server().player(playerId);
        if (player == null || !player.world().id().equals(worldId)) {
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

    private void arrive(Bot bot, GamePlayer player) {
        if (!bot.isAlive()) {
            return;
        }
        GameWorld w = player.world();
        Pos spot = portalPos != null ? exitSpot(w, portalPos) : hiddenSpot(w, player.blockPos());
        Place dest = new Place(w, spot.x() + 0.5, spot.y() + 0.1, spot.z() + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        manager.teleportBot(bot, dest);
        bot.brain().setTeleporting(false);
        bot.lookAt(player.position());
        w.playSound(dest.pos(), portalPos != null ? Sounds.BLOCK_PORTAL_TRAVEL : Sounds.ENTITY_ENDERMAN_TELEPORT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.6f, 1f);
    }

    // ------------------------------------------------------------------ portal

    private void spawnPortal(GamePlayer player) {
        GameWorld w = player.world();
        portalPos = findPortalLocation(player);
        boolean end = w.dimension() == Dimension.THE_END;
        if (portalPos == null) {
            portalPos = player.blockPos().relative(Face.fromYaw(player.yaw()), 15);
        }
        if (end) {
            if (!WorldUtil.blocksMotion(w, portalPos.below())) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        GameBlock b = w.block(portalPos.offset(x, -1, z));
                        if (!b.type().is(BlockTrait.SOLID)) {
                            b.setType(Blocks.OBSIDIAN);
                        }
                    }
                }
            }
            return;
        }
        buildNetherPortal(w, portalPos);
        manager.plugin().pathService().invalidate(w, portalPos);
    }

    /** 2x3 portal with an obsidian frame along the X axis, obsidian floor where there is no support. */
    public static void buildNetherPortal(GameWorld w, Pos pos) {
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                GameBlock floor = w.block(pos.offset(x, -1, z));
                if (!floor.type().is(BlockTrait.SOLID) || floor.type() == Blocks.LAVA) {
                    floor.setType(Blocks.OBSIDIAN);
                }
            }
        }
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                w.block(pos.offset(x, y, 0)).setType(Blocks.AIR, false);
            }
        }
        for (int[] f : FRAME) {
            w.block(pos.offset(f[0], f[1], 0)).setType(Blocks.OBSIDIAN, false);
        }
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                w.block(pos.offset(x, y, 0)).setPortalX();
            }
        }
    }

    private Pos findPortalLocation(GamePlayer player) {
        GameWorld w = player.world();
        Vec3 pp = player.position();
        boolean nether = w.dimension() == Dimension.NETHER;
        boolean end = w.dimension() == Dimension.THE_END;
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
                    if (ty > w.minHeight() + 1) {
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

    private static boolean visibleTo(GamePlayer player, Pos pos) {
        Vec3 eye = player.eyePosition();
        Vec3 center = Vec3.center(pos);
        Vec3 look = player.direction();
        if (look.dot(center.subtract(eye).normalize()) < 0.5) {
            return false;
        }
        return WorldUtil.clearLine(player.world(), eye, center.add(0, 1, 0));
    }

    private static Pos exitSpot(GameWorld w, Pos base) {
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

    /** A safe spot out of the player's sight, 15–40 blocks away (entrance without a portal). */
    private static Pos hiddenSpot(GameWorld w, Pos center) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 30; i++) {
            int x = center.x() + r.nextInt(50) - 25;
            int z = center.z() + r.nextInt(50) - 25;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            if (w.dimension() == Dimension.NETHER) {
                for (int y = Math.min(w.maxHeight() - 3, center.y() + 24); y >= Math.max(w.minHeight() + 2, center.y() - 24); y--) {
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

    static boolean isSafeSpot(GameWorld w, Pos c) {
        if (!WorldUtil.isSafeStand(w, c)) {
            return false;
        }
        BlockType feet = w.block(c).type();
        BlockType head = w.block(c.above()).type();
        if (feet == Blocks.NETHER_PORTAL || head == Blocks.NETHER_PORTAL) {
            return false;
        }
        int open = WorldUtil.countOpenSides(w, c);
        return w.dimension() == Dimension.NETHER ? open >= 2 : open >= 1;
    }
}
