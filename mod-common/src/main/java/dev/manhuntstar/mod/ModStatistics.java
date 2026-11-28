package dev.manhuntstar.mod;

import dev.faststats.ErrorTracker;
import dev.faststats.FastStatsContext;
import dev.faststats.data.Metric;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.metrics.ErrorReports;
import dev.manhuntstar.metrics.FirstRun;
import dev.manhuntstar.metrics.UsageStats;
import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The mods' FastStats (https://faststats.dev), shared by the loaders: anonymous statistics and error
 * reports, on a dedicated server and on the client (singleplayer and LAN), on by default and without the
 * SDK's consent screen (the builder isn't asked for it). Each loader calls {@link #start} with its own
 * builder (FastStatsFabric, FastStatsNeoForge) when the mod loads. On a dedicated server the SDK makes the
 * context ready once the server has started and shuts it down when the server stops; on the client it is
 * ready right away. Players turn it off in Options, Telemetry Data, FastStats, or in config.yml.
 *
 * <p>The custom metrics are the Paper plugin's ({@link UsageStats}): {@code bots}, {@code plugin_language} and
 * {@code default_difficulty}. The error reports are {@link ErrorReports}. No feature flags.
 */
public final class ModStatistics {

    private ModStatistics() {
    }

    /**
     * Makes the context with the metrics and the error tracker that config.yml leaves on, unless this server
     * sends nothing: a build without a token (FASTSTATS_TOKEN), or both {@code faststats.statistics} and
     * {@code faststats.error-reports} off. config.yml is read here, before core starts, since FastStats has to
     * be set up when the mod loads. If FastStats can't start (its config.properties can't be written...), the
     * mod loads anyway, without it.
     *
     * @param builder the loader's {@code builder("manhuntstar")}, asked for only when there is something to
     *                send: it writes config/faststats/config.properties on FastStats' first run
     */
    public static void start(Supplier<? extends FastStatsContext.Builder> builder, ModPlatform platform) {
        String token = UsageStats.token();
        boolean statistics = statisticsEnabled(platform);
        boolean errorReports = errorReportsEnabled(platform);
        if (token.isEmpty() || (!statistics && !errorReports)) {
            return;
        }
        if (FirstRun.inThisJvm(platform.fastStatsConfig())) {
            // another mod set FastStats up for the first time on this server or computer during this run:
            // nothing is sent until the next restart
            platform.logger().info("FastStats runs for the first time here: Manhunt* sends nothing until the next restart.");
            return;
        }
        // before the builder, which writes the file this reads
        ErrorReports.Mode mode = ErrorReports.mode(errorReports, statistics, platform.fastStatsConfig());
        ErrorReports errors = null;
        try {
            FastStatsContext.Builder fastStats = builder.get().token(token);
            if (statistics) {
                fastStats.metrics(f -> f
                        .addMetric(Metric.number(UsageStats.BOTS_ID, () -> read(platform, UsageStats::bots)))
                        .addMetric(Metric.string(UsageStats.LANGUAGE_ID, () -> read(platform, UsageStats::language)))
                        .addMetric(Metric.string(UsageStats.DEFAULT_DIFFICULTY_ID, () -> read(platform, UsageStats::defaultDifficulty)))
                        .create());
            }
            switch (mode) {
                case TRACK -> {
                    // kept until the JVM exits when the reports are sent (a dedicated server doesn't start
                    // twice); closed once the server has started if they aren't
                    errors = new ErrorReports(platform.logger());
                    fastStats.errorTrackerService(errors.tracker()).onReady(errors::closeUnlessSent);
                }
                case IDLE -> fastStats.errorTrackerService(ErrorTracker.contextUnaware());
                case NONE -> {
                    // no error reports from this start
                }
            }
            fastStats.build();
        } catch (RuntimeException e) {
            if (errors != null) {
                errors.close();
            }
            platform.logger().log(Level.WARNING, "FastStats could not start; no statistics or error reports this time.", e);
        }
    }

    private static boolean statisticsEnabled(ModPlatform platform) {
        return Settings.statisticsEnabled(platform.yaml(), config(platform));
    }

    private static boolean errorReportsEnabled(ModPlatform platform) {
        return Settings.errorReportsEnabled(platform.yaml(), config(platform));
    }

    private static File config(ModPlatform platform) {
        return new File(platform.dataFolder(), "config.yml");
    }

    /** FastStats reads the values off the server thread: core's copies, empty until a server has started. */
    private static <T> T read(ModPlatform platform, Function<UsageStats, T> value) {
        UsageStats usage = platform.usage();
        return usage == null ? null : value.apply(usage);
    }
}
