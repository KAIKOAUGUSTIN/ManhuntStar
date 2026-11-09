package dev.manhuntstar.pathfinding;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Pos;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pathfinding service shared by all bots. The chunk copies are taken on the main
 * thread and the A* runs on a fixed thread pool. In synchronous mode the A* runs on the
 * main thread with a short time budget.
 */
public final class PathService {

    /** Radius (in blocks) of the region copied around the bot for the A*. */
    public static final int ASTAR_REGION_RADIUS = 48;
    private static final long ASYNC_TIME_BUDGET_MS = 250L;
    private static final long SYNC_TIME_BUDGET_MS = 20L;

    private final GameServer server;
    private final Settings settings;
    private final Logger logger;
    private final ExecutorService pool;
    private final ChunkSnapshotCache snapshots;
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger samples = new AtomicInteger();
    private final AtomicLong totalMs = new AtomicLong();
    private volatile int effectiveNodeCap;
    private long lastCaptureMs;
    private boolean closed;

    /** The platform must have registered the block types ({@link dev.manhuntstar.platform.BlockType}) before. */
    public PathService(GameServer server, Settings settings, Logger logger) {
        this.server = server;
        this.snapshots = new ChunkSnapshotCache(server);
        this.settings = settings;
        this.logger = logger;
        this.effectiveNodeCap = settings.nodeCap.value();
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ManhuntStar-Pathfinding-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        BlockCosts.init();
    }

    // ------------------------------------------------------------------ A*

    /**
     * Asks for a path. Asynchronous: returns a future completed on a pool thread (only read it with
     * {@code isDone()/join()} on the main thread). Synchronous: it comes already completed.
     */
    public CompletableFuture<PathResult> findPath(GameWorld world, Pos start, Pos target, boolean allowPlacement, boolean allowBoat) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        long t0 = System.nanoTime();
        SnapshotGrid grid = snapshots.capture(world, start.x(), start.z(), ASTAR_REGION_RADIUS);
        lastCaptureMs = (System.nanoTime() - t0) / 1_000_000L;
        int cap = settings.dynamicNodes.on() ? effectiveNodeCap : settings.nodeCap.value();
        double greed = settings.greediness.value();
        long submitted = System.currentTimeMillis();
        if (!settings.asyncPaths.on()) {
            PathResult r = safeFind(grid, start, target, cap, SYNC_TIME_BUDGET_MS, greed, allowPlacement, allowBoat);
            record(System.currentTimeMillis() - submitted);
            return CompletableFuture.completedFuture(r);
        }
        activeTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return safeFind(grid, start, target, cap, ASYNC_TIME_BUDGET_MS, greed, allowPlacement, allowBoat);
            } finally {
                activeTasks.decrementAndGet();
                record(System.currentTimeMillis() - submitted);
            }
        }, pool);
    }

    private PathResult safeFind(CostProvider grid, Pos start, Pos target, int cap, long budget, double greed, boolean place, boolean boat) {
        try {
            return AStar.get().findPath(grid, start, target, cap, budget, greed, place, boat);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "A* error", e);
            return null;
        }
    }

    private void record(long ms) {
        samples.incrementAndGet();
        totalMs.addAndGet(ms);
    }

    // ------------------------------------------------------------------ maintenance

    /** A block changed: drops the chunk copy so the next computation sees the current world. */
    public void invalidate(GameWorld world, Pos pos) {
        snapshots.invalidate(world, pos.x(), pos.z());
    }

    public void invalidate(GameWorld world, int x, int z) {
        snapshots.invalidate(world, x, z);
    }

    /** Called once per tick by the plugin. */
    public void tick() {
        int now = server.currentTick();
        if (now % 20 == 0) {
            adjustNodeCap();
        }
        if (now % 100 == 0) {
            snapshots.purge();
        }
    }

    /** The mod's dynamic adjustment: an average > 500 ms lowers it 20%, < 125 ms raises it 10% (50..5000). */
    private void adjustNodeCap() {
        int n = samples.getAndSet(0);
        long total = totalMs.getAndSet(0);
        if (!settings.dynamicNodes.on() || n == 0) {
            return;
        }
        long avg = total / n;
        if (avg > 500) {
            effectiveNodeCap = Math.max(50, (int) (effectiveNodeCap * 0.8));
        } else if (avg < 125 && effectiveNodeCap < 5000) {
            effectiveNodeCap = Math.min(5000, (int) Math.ceil(effectiveNodeCap * 1.1));
        }
    }

    public int effectiveNodeCap() {
        return settings.dynamicNodes.on() ? effectiveNodeCap : settings.nodeCap.value();
    }

    /** Brings the effective limit back to the configured value (when the admin changes the limit). */
    public void resetNodeCap() {
        effectiveNodeCap = settings.nodeCap.value();
    }

    public int activeTasks() {
        return activeTasks.get();
    }

    public long lastCaptureMs() {
        return lastCaptureMs;
    }

    public void shutdown() {
        closed = true;
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        snapshots.clear();
    }
}
