package dev.manhuntstar.metrics;

import dev.manhuntstar.ManhuntStarPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

/**
 * Anonymous usage statistics on bStats (https://bstats.org): server count, Paper and Java versions,
 * and the charts below. Server owners can turn it off for every plugin in
 * {@code plugins/bStats/config.yml}. The plugin ID comes from {@code bstatsId} in gradle.properties;
 * with 0 nothing is sent.
 *
 * <p>Custom charts (each one has to be created on the bStats page with the same ID):
 * {@code bots} (single line chart), {@code language} and {@code default_difficulty} (simple pies).
 */
public final class MetricsService {

    private final ManhuntStarPlugin plugin;
    private Metrics metrics;

    public MetricsService(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int id = pluginId();
        if (id <= 0) {
            return;
        }
        metrics = new Metrics(plugin, id);
        // bStats collects the values on the main thread and sends them off it
        metrics.addCustomChart(new SingleLineChart("bots", () -> plugin.core().botManager().aliveBots().size()));
        metrics.addCustomChart(new SimplePie("language", () -> plugin.core().messages().language()));
        metrics.addCustomChart(new SimplePie("default_difficulty", () -> plugin.core().settings().defaultDifficulty.get().configName()));
    }

    public void shutdown() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    /** The ID written into manhuntstar.properties at build time, or 0. */
    static int pluginId() {
        try (InputStream in = MetricsService.class.getResourceAsStream("/manhuntstar.properties")) {
            if (in == null) {
                return 0;
            }
            Properties props = new Properties();
            props.load(in);
            return Integer.parseInt(props.getProperty("bstats-id", "0").trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }
}
