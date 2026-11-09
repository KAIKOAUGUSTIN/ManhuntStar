package dev.manhuntstar.mod;

import dev.manhuntstar.platform.ScheduledTask;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tasks by server tick, as Bukkit's scheduler runs them: {@link #heartbeat} runs at the start of every tick
 * the tasks that are due; a task scheduled during a tick is due at the earliest on the next one; repeating
 * tasks run again {@code period} ticks later; async tasks run on other threads when due.
 */
final class ModScheduler {

    private static final AtomicInteger THREADS = new AtomicInteger();

    private final Logger logger;
    private final Queue<Task> pending = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ExecutorService async = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Manhunt* async " + THREADS.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private volatile long tick;

    ModScheduler(Logger logger) {
        this.logger = logger;
    }

    private final class Task implements Comparable<Task>, ScheduledTask {
        final Runnable body;
        final long period;
        final boolean isAsync;
        final long order = sequence.incrementAndGet();
        long due;
        volatile boolean cancelled;

        Task(Runnable body, long due, long period, boolean isAsync) {
            this.body = body;
            this.due = due;
            this.period = period;
            this.isAsync = isAsync;
        }

        @Override
        public int compareTo(Task o) {
            int c = Long.compare(due, o.due);
            return c != 0 ? c : Long.compare(order, o.order);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    ScheduledTask schedule(Runnable body, long delayTicks, long periodTicks, boolean isAsync) {
        Task t = new Task(body, tick + Math.max(0, delayTicks), periodTicks, isAsync);
        pending.add(t);
        return t;
    }

    /** One server tick: runs what is due (on the server thread). */
    void heartbeat() {
        long now = ++tick;
        for (Task t; (t = pending.poll()) != null; ) {
            queue.add(t);
        }
        while (!queue.isEmpty() && queue.peek().due <= now) {
            Task t = queue.poll();
            if (t.cancelled) {
                continue;
            }
            if (t.isAsync) {
                async.execute(() -> run(t));
            } else {
                run(t);
            }
            if (t.period > 0 && !t.cancelled) {
                t.due = now + t.period;
                pending.add(t);
            }
        }
    }

    private void run(Task t) {
        try {
            t.body.run();
        } catch (Throwable e) {
            logger.log(Level.WARNING, "A Manhunt* task failed", e);
        }
    }

    /** Stops everything (the server is stopping). */
    void shutdown() {
        pending.clear();
        queue.clear();
        async.shutdownNow();
    }
}
