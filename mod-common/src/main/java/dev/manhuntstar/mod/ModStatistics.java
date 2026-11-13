package dev.manhuntstar.mod;

import dev.faststats.ErrorTracker;
import dev.faststats.SimpleContext;
import dev.faststats.data.Metric;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.metrics.ErrorReports;
import dev.manhuntstar.metrics.UsageStats;
import java.io.File;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * The mods' FastStats (https://faststats.dev), shared by the loaders: anonymous statistics and error
 * reports. Each loader makes its own context (FabricContext, NeoForgeContext) when the mod loads, on a
 * dedicated server only, with {@link #token} and {@link #start}; the SDK then starts and stops with the
 * server.
 *
 * <p>The custom metrics are the Paper plugin's ({@link UsageStats}): {@code bots}, {@code language} and
 * {@code default_difficulty}. The error reports are {@link ErrorReports}. No feature flags.
 */
public final class ModStatistics {

    private ModStatistics() {
    }

    /**
     * The project token when this server sends anything, or an empty string: a build without a token
     * (FASTSTATS_TOKEN), or both {@code faststats.statistics} and {@code faststats.error-reports} off in config.yml.
     * config.yml is read here, before core starts, since FastStats has to be set up when the mod loads.
     */
    public static String token(ModPlatform platform) {
        String token = UsageStats.token();
        if (token.isEmpty() || (!statisticsEnabled(platform) && !errorReportsEnabled(platform))) {
            return "";
        }
        return token;
    }

    /**
     * Makes the context with the metrics and the error tracker that config.yml leaves on. If FastStats
     * can't start (its config.properties can't be written...), the mod loads anyway, without it.
     */
    public static <C extends SimpleContext, F extends SimpleContext.Factory<C, F>> void start(F factory, ModPlatform platform) {
        ErrorReports errors = null;
        try {
            if (statisticsEnabled(platform)) {
                factory.metrics(f -> f
                        .addMetric(Metric.number("bots", () -> read(platform, UsageStats::bots)))
                        .addMetric(Metric.string("language", () -> read(platform, UsageStats::language)))
                        .addMetric(Metric.string("default_difficulty", () -> read(platform, UsageStats::defaultDifficulty)))
                        .create());
            }
            if (errorReportsEnabled(platform) && ErrorReports.sentBy(platform.fastStatsConfig())) {
                // kept until the JVM exits when the reports are sent: a dedicated server doesn't start twice
                errors = new ErrorReports(platform.logger());
                factory.errorTrackerService(errors.tracker());
            } else if (errorReportsEnabled(platform)) {
                // Let the SDK create its config and show first-run consent without attaching error handlers.
                factory.errorTrackerService(ErrorTracker.contextUnaware());
            }
            C context = factory.create();
            if (errors != null) {
                errors.closeUnlessSentBy(context);
            }
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
