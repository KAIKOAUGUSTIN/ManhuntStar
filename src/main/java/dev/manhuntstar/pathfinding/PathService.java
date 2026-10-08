package dev.manhuntstar.pathfinding;

import dev.manhuntstar.config.Settings;
import dev.manhuntstar.util.Pos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Serviço de pathfinding compartilhado por todos os bots. As cópias de chunk são tiradas na thread
 * principal e o A* / campo de fluxo rodam num pool fixo de threads. No modo síncrono o A* roda na
 * thread principal com orçamento curto de tempo.
 */
public final class PathService {

    /** Raio (em blocos) da região copiada em volta do bot para o A*. */
    public static final int ASTAR_REGION_RADIUS = 48;
    private static final long ASYNC_TIME_BUDGET_MS = 250L;
    private static final long SYNC_TIME_BUDGET_MS = 20L;
    private static final long FIELD_TTL_MS = 2000L;
    private static final int MAX_FIELD_REQUESTS_PER_TICK = 3;

    private final Settings settings;
    private final Logger logger;
    private final ExecutorService pool;
    private final ChunkSnapshotCache snapshots = new ChunkSnapshotCache();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger samples = new AtomicInteger();
    private final AtomicLong totalMs = new AtomicLong();
    private volatile int effectiveNodeCap;
    private int fieldRequestsThisTick;
    private long lastCaptureMs;
    private final Map<FieldKey, FlowField> fields = new HashMap<>();
    private final Map<FieldKey, CompletableFuture<FlowField>> pendingFields = new HashMap<>();
    private boolean closed;

    private record FieldKey(UUID world, long pos) {
    }

    public PathService(Settings settings, Logger logger) {
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
     * Pede um caminho. Assíncrono: devolve um future completado numa thread do pool (ler só com
     * {@code isDone()/join()} na thread principal). Síncrono: já vem completado.
     */
    public CompletableFuture<PathResult> findPath(World world, Pos start, Pos target, boolean allowPlacement, boolean allowBoat) {
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
            logger.log(Level.WARNING, "Erro no A*", e);
            return null;
        }
    }

    private void record(long ms) {
        samples.incrementAndGet();
        totalMs.addAndGet(ms);
    }

    // ------------------------------------------------------------------ campo de fluxo (Dijkstra)

    /**
     * Campo de fluxo para o alvo. Devolve o último calculado (mesmo velho) e agenda um novo se
     * passou de 2 s; null enquanto o primeiro ainda não ficou pronto.
     */
    public FlowField flowField(World world, Pos target) {
        FieldKey key = new FieldKey(world.getUID(), target.asLong());
        FlowField existing = fields.get(key);
        if (existing != null && System.currentTimeMillis() - existing.computedAtMs() < FIELD_TTL_MS) {
            return existing;
        }
        CompletableFuture<FlowField> inFlight = pendingFields.get(key);
        if (inFlight != null) {
            if (!inFlight.isDone()) {
                return existing;
            }
            pendingFields.remove(key);
            FlowField done = inFlight.getNow(null);
            if (done != null) {
                fields.put(key, done);
                return done;
            }
        }
        if (closed || fieldRequestsThisTick >= MAX_FIELD_REQUESTS_PER_TICK) {
            return existing;
        }
        fieldRequestsThisTick++;
        SnapshotGrid grid = snapshots.capture(world, target.x(), target.z(), FlowField.RADIUS);
        activeTasks.incrementAndGet();
        pendingFields.put(key, CompletableFuture.supplyAsync(() -> {
            try {
                return FlowField.compute(grid, target);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Erro no campo de fluxo", e);
                return null;
            } finally {
                activeTasks.decrementAndGet();
            }
        }, pool));
        return existing;
    }

    // ------------------------------------------------------------------ manutenção

    /** Um bloco mudou: descarta a cópia do chunk para o próximo cálculo ver o mundo atual. */
    public void invalidate(World world, Pos pos) {
        snapshots.invalidate(world, pos.x(), pos.z());
    }

    public void invalidate(World world, int x, int z) {
        snapshots.invalidate(world, x, z);
    }

    /** Chamado uma vez por tick pelo plugin. */
    public void tick() {
        fieldRequestsThisTick = 0;
        int now = Bukkit.getCurrentTick();
        if (now % 20 == 0) {
            adjustNodeCap();
        }
        if (now % 100 == 0) {
            snapshots.purge();
            long cutoff = System.currentTimeMillis() - FIELD_TTL_MS * 5;
            fields.values().removeIf(f -> f.computedAtMs() < cutoff);
            pendingFields.values().removeIf(CompletableFuture::isDone);
        }
    }

    /** Ajuste dinâmico do mod: média > 500 ms reduz 20%, < 125 ms aumenta 10% (50..5000). */
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

    /** Volta o limite efetivo ao valor configurado (quando o admin muda o limite). */
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
        fields.clear();
        pendingFields.clear();
    }
}
