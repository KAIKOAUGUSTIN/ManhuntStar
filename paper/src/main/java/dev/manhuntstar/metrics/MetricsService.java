package dev.manhuntstar.metrics;

import dev.faststats.ErrorTracker;
import dev.faststats.FastStatsContext;
import dev.faststats.bukkit.FastStatsBukkit;
import dev.faststats.data.Metric;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Settings;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * FastStats (https://faststats.dev): anonymous usage statistics (server count, Paper, Minecraft and
 * Java versions, and the metrics in {@link UsageStats}) and error reports ({@link ErrorReports}).
 * {@code faststats.statistics} and {@code faststats.error-reports} in config.yml turn each one off (read when the
 * server starts); {@code plugins/faststats/config.properties} turns them off for every plugin. When
 * FastStats first runs on a server (no such file yet, from any plugin) the file is created and nothing is
 * sent until the next restart. Builds without a project token (FASTSTATS_TOKEN) send nothing.
 *
 * <p>Custom metrics (each one has to be a data source on the FastStats project, with the same Reference ID):
 * {@code bots} (number), {@code plugin_language} and {@code default_difficulty} (strings), the IDs in
 * {@link UsageStats}. No feature flags.
 */
public final class MetricsService {

    private final ManhuntStarPlugin plugin;
    private FastStatsContext context;
    private ErrorReports errors;

    public MetricsService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    /** In onEnable: FastStats' context has to be made ready once the plugin is enabled. */
    public void start() {
        String token = UsageStats.token();
        Settings settings = plugin.core().settings();
        boolean metrics = settings.statistics.on();
        boolean errorReports = settings.errorReports.on();
        if (token.isEmpty() || (!metrics && !errorReports)) {
            return;
        }
        if (FirstRun.inThisJvm(plugin.fastStatsConfig())) {
            // FastStats was set up for the first time on this server during this run (by another plugin, or by
            // Manhunt* itself before a reload): nothing is sent until the next restart
            plugin.getLogger().info("FastStats runs for the first time on this server: Manhunt* sends nothing until the next restart.");
            return;
        }
        // before the builder: it writes plugins/faststats/config.properties on FastStats' first run
        ErrorReports.Mode mode = ErrorReports.mode(errorReports, metrics, plugin.fastStatsConfig());
        AtomicBoolean ready = new AtomicBoolean();
        try {
            FastStatsContext.Builder builder = FastStatsBukkit.builder(plugin).token(token);
            if (metrics) {
                // FastStats reads the values off the main thread (every 30 minutes): it gets core's copies
                UsageStats usage = plugin.core().usage();
                builder.metrics(f -> f
                        .addMetric(Metric.number(UsageStats.BOTS_ID, usage::bots))
                        .addMetric(Metric.string(UsageStats.LANGUAGE_ID, usage::language))
                        .addMetric(Metric.string(UsageStats.DEFAULT_DIFFICULTY_ID, usage::defaultDifficulty))
                        .create());
            }
            switch (mode) {
                case TRACK -> {
                    errors = new ErrorReports(plugin.getLogger());
                    builder.errorTrackerService(errors.tracker()).onReady(errors::closeUnlessSent);
                }
                case IDLE -> builder.errorTrackerService(ErrorTracker.contextUnaware());
                case NONE -> {
                    // no error reports from this start
                }
            }
            // the last hook, so it only runs if every one before it (the SDK's own first) did
            context = builder.onReady(c -> ready.set(true)).build();
            context.ready();
        } catch (RuntimeException e) {
            // e.g. plugins/faststats/config.properties can't be written: Manhunt* runs without FastStats
            plugin.getLogger().log(Level.WARNING, "FastStats could not start; no statistics or error reports this time.", e);
            shutdown();
            return;
        }
        if (!ready.get()) {
            // the SDK has logged why: Manhunt* runs without FastStats
            shutdown();
        }
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
