package dev.manhuntstar.mod;

import dev.faststats.Metrics;
import dev.faststats.data.Metric;
import dev.manhuntstar.ManhuntCore;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.metrics.UsageStats;
import java.io.File;
import java.util.function.Function;

/**
 * The mods' anonymous statistics on FastStats (https://faststats.dev), shared by the loaders: each one
 * makes its own FastStats context (FabricContext, NeoForgeContext) when the mod loads, on a dedicated
 * server only, with {@link #token} and {@link #metrics}. The SDK then starts and stops with the server.
 *
 * <p>The custom metrics are the Paper plugin's ({@link UsageStats}): {@code bots}, {@code language} and
 * {@code default_difficulty}. Only metrics are turned on: no error tracking and no feature flags.
 */
public final class ModStatistics {

    private ModStatistics() {
    }

    /**
     * The project token when this server sends statistics, or an empty string: a build without a token
     * (FASTSTATS_TOKEN), or {@code general.metrics: false} in config.yml. config.yml is read here, before
     * core starts, since FastStats has to be set up when the mod loads.
     */
    public static String token(ModPlatform platform) {
        String token = UsageStats.token();
        if (token.isEmpty() || !Settings.metricsEnabled(platform.yaml(), new File(platform.dataFolder(), "config.yml"))) {
            return "";
        }
        return token;
    }

    /** The custom metrics, for the context's factory: {@code .metrics(ModStatistics.metrics(platform))}. */
    public static Function<Metrics.Factory, Metrics> metrics(ModPlatform platform) {
        // FastStats reads them off the server thread: core's copies, empty while no server runs
        return factory -> factory
                .addMetric(Metric.number("bots", () -> read(platform, UsageStats::bots)))
                .addMetric(Metric.string("language", () -> read(platform, UsageStats::language)))
                .addMetric(Metric.string("default_difficulty", () -> read(platform, UsageStats::defaultDifficulty)))
                .create();
    }

    private static <T> T read(ModPlatform platform, Function<UsageStats, T> value) {
        ManhuntCore core = platform.core();
        return core == null ? null : value.apply(core.usage());
    }
}
