package dev.manhuntstar.metrics;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import dev.manhuntstar.ManhuntStarPlugin;

/**
 * Anonymous usage statistics on FastStats (https://faststats.dev): server count, Paper, Minecraft and
 * Java versions, and the metrics in {@link UsageStats}. Off with {@code general.metrics: false} in
 * config.yml (read when the server starts), or for every plugin in
 * {@code plugins/faststats/config.properties}; on the first start that file is created and nothing is
 * sent until the next restart. Builds without a project token (FASTSTATS_TOKEN) send nothing.
 *
 * <p>Custom metrics (each one has to be created on the FastStats project with the same ID):
 * {@code bots} (number), {@code language} and {@code default_difficulty} (strings). Only metrics are
 * turned on: no error tracking and no feature flags.
 */
public final class MetricsService {

    private final ManhuntStarPlugin plugin;
    private BukkitContext context;

    public MetricsService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String token = UsageStats.token();
        if (token.isEmpty() || !plugin.core().settings().metrics.on()) {
            return;
        }
        // FastStats reads the values off the main thread (every 30 minutes): it gets core's copies
        UsageStats usage = plugin.core().usage();
        context = new BukkitContext.Factory(plugin, token)
                .metrics(factory -> factory
                        .addMetric(Metric.number("bots", usage::bots))
                        .addMetric(Metric.string("language", usage::language))
                        .addMetric(Metric.string("default_difficulty", usage::defaultDifficulty))
                        .create())
                .create();
        context.ready();
    }

    public void shutdown() {
        if (context != null) {
            context.shutdown();
            context = null;
        }
    }
}
