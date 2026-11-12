package dev.manhuntstar.metrics;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import dev.manhuntstar.ManhuntStarPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bukkit.scheduler.BukkitTask;

/**
 * Anonymous usage statistics on FastStats (https://faststats.dev): server count, Paper, Minecraft and
 * Java versions, and the metrics below. Server owners can turn it off for every plugin in
 * {@code plugins/faststats/config.properties}; on the first start that file is created and nothing is
 * sent until the next restart. The project token comes from {@code faststatsToken} in
 * gradle.properties; when it is empty nothing is sent.
 *
 * <p>Custom metrics (each one has to be created on the FastStats project with the same ID):
 * {@code bots} (number), {@code language} and {@code default_difficulty} (strings). Only metrics are
 * turned on: no error tracking and no feature flags.
 */
public final class MetricsService {

    /** How often the values are copied on the main thread (1 minute); FastStats reads the copies. */
    private static final long SNAPSHOT_TICKS = 20L * 60;

    private final ManhuntStarPlugin plugin;
    private BukkitContext context;
    private BukkitTask snapshotTask;
    private volatile int bots;
    private volatile String language;
    private volatile String defaultDifficulty;

    public MetricsService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String token = token();
        if (token.isEmpty()) {
            return;
        }
        // FastStats collects and sends the values off the main thread (every 30 minutes), where the
        // bots can't be read: it gets the copies made on the main thread
        snapshot();
        snapshotTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::snapshot, SNAPSHOT_TICKS, SNAPSHOT_TICKS);
        context = new BukkitContext.Factory(plugin, token)
                .metrics(factory -> factory
                        .addMetric(Metric.number("bots", () -> bots))
                        .addMetric(Metric.string("language", () -> language))
                        .addMetric(Metric.string("default_difficulty", () -> defaultDifficulty))
                        .create())
                .create();
        context.ready();
    }

    private void snapshot() {
        bots = plugin.core().botManager().aliveBots().size();
        language = plugin.core().messages().language();
        defaultDifficulty = plugin.core().settings().defaultDifficulty.get().configName();
    }

    public void shutdown() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (context != null) {
            context.shutdown();
            context = null;
        }
    }

    /** The project token written into manhuntstar.properties at build time, or an empty string. */
    static String token() {
        try (InputStream in = MetricsService.class.getResourceAsStream("/manhuntstar.properties")) {
            if (in == null) {
                return "";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("faststats-token", "").trim();
        } catch (IOException e) {
            return "";
        }
    }
}
