package dev.manhuntstar.metrics;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Settings;

/**
 * FastStats (https://faststats.dev): anonymous usage statistics (server count, Paper, Minecraft and
 * Java versions, and the metrics in {@link UsageStats}) and error reports ({@link ErrorReports}).
 * {@code general.metrics} and {@code general.error-reports} in config.yml turn each one off (read when the
 * server starts); {@code plugins/faststats/config.properties} turns them off for every plugin. On the
 * first start that file is created and nothing is sent until the next restart. Builds without a project
 * token (FASTSTATS_TOKEN) send nothing.
 *
 * <p>Custom metrics (each one has to be created on the FastStats project with the same ID):
 * {@code bots} (number), {@code language} and {@code default_difficulty} (strings). No feature flags.
 */
public final class MetricsService {

    private final ManhuntStarPlugin plugin;
    private BukkitContext context;
    private ErrorReports errors;

    public MetricsService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String token = UsageStats.token();
        Settings settings = plugin.core().settings();
        boolean metrics = settings.metrics.on();
        boolean errorReports = settings.errorReports.on();
        if (token.isEmpty() || (!metrics && !errorReports)) {
            return;
        }
        BukkitContext.Factory factory = new BukkitContext.Factory(plugin, token);
        if (metrics) {
            // FastStats reads the values off the main thread (every 30 minutes): it gets core's copies
            UsageStats usage = plugin.core().usage();
            factory.metrics(f -> f
                    .addMetric(Metric.number("bots", usage::bots))
                    .addMetric(Metric.string("language", usage::language))
                    .addMetric(Metric.string("default_difficulty", usage::defaultDifficulty))
                    .create());
        }
        if (errorReports) {
            errors = new ErrorReports(plugin.getLogger());
            factory.errorTrackerService(errors.tracker());
        }
        context = factory.create();
        context.ready();
    }

    public void shutdown() {
        if (context != null) {
            context.shutdown();
            context = null;
        }
        if (errors != null) {
            errors.close();
            errors = null;
        }
    }
}
