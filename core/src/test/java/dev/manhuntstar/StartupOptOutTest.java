package dev.manhuntstar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.config.FailingSaveYamlFiles;
import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.host.ManhuntPlatform;
import dev.manhuntstar.platform.FakeServer;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.pathfinding.BlockCosts;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupOptOutTest {
    @TempDir
    Path dir;

    private static class ConfigLoaded extends RuntimeException {}

    @Test
    void failedFirstConfigStillDisablesBothOptionsAndIsWrittenNextStart() throws Exception {
        checkFailedSaveAndRetry(false, true);
    }

    @Test
    void failedUpgradeStillCarriesOverOldOptOut() throws Exception {
        checkFailedSaveAndRetry(true, true);
    }

    @Test
    void upgradeWritesTheOptOutWithTheNewVersion() throws Exception {
        checkFailedSaveAndRetry(true, false);
    }

    private void checkFailedSaveAndRetry(boolean oldConfig, boolean failUpgrade) throws Exception {
        if (oldConfig) {
            Files.writeString(dir.resolve("config.yml"), "general:\n  update-check: false\n");
        }
        Logger logger = Logger.getAnonymousLogger();
        YamlFiles yaml = new SnakeYamlFiles(logger);
        AtomicBoolean fail = new AtomicBoolean(true);
        YamlFiles failing = new YamlFiles() {
            public ConfigNode load(File file) { return yaml.load(file); }
            public ConfigNode load(Reader reader) { return yaml.load(reader); }
            public ConfigNode create() { return yaml.create(); }
            public String saveToString(ConfigNode node) { return yaml.saveToString(node); }
            public void save(ConfigNode node, File file) throws IOException {
                if (fail.get() && (failUpgrade || file.getName().equals("config.yml"))) {
                    new FailingSaveYamlFiles(yaml, ignored -> {}).save(node, file);
                } else {
                    yaml.save(node, file);
                }
            }
        };
        Path bStats = dir.resolve("bstats.yml");
        Files.writeString(bStats, "enabled: false\n");
        ManhuntPlatform platform = (ManhuntPlatform) Proxy.newProxyInstance(
                ManhuntPlatform.class.getClassLoader(), new Class<?>[]{ManhuntPlatform.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "server" -> new FakeServer();
                    case "yaml" -> failing;
                    case "logger" -> logger;
                    case "dataFolder" -> dir.toFile();
                    case "bStatsConfig" -> bStats.toFile();
                    case "resource" -> getClass().getResourceAsStream("/" + args[0]);
                    case "saveResource" -> {
                        try (var in = getClass().getResourceAsStream("/" + args[0])) {
                            Files.copy(in, dir.resolve((String) args[0]));
                        }
                        yield null;
                    }
                    case "version" -> "2.0.0";
                    case "services" -> throw new ConfigLoaded();
                    default -> throw new AssertionError(method.getName());
                });
        // A real startup initializes this process-wide cache; restore it for other synthetic worlds.
        var costs = BlockCosts.class.getDeclaredField("costs");
        var waterloggable = BlockCosts.class.getDeclaredField("waterloggable");
        costs.setAccessible(true);
        waterloggable.setAccessible(true);
        Object previousCosts = costs.get(null);
        Object previousWaterloggable = waterloggable.get(null);
        GameServer previousServer;
        try {
            previousServer = Platform.server();
        } catch (IllegalStateException e) {
            previousServer = null;
        }
        Platform.set(new FakeServer());
        ManhuntCore core = new ManhuntCore(platform);
        try {
            assertThrows(ConfigLoaded.class, core::enable);
            assertFalse(core.settings().statistics.on());
            assertFalse(core.settings().errorReports.on());
            ConfigNode saved = yaml.load(dir.resolve("config.yml").toFile());
            // never a config.yml with the faststats section but without bStats' choice: a write that
            // failed left the previous file (or none), for the next start to write again
            if (!oldConfig) {
                assertFalse(Files.exists(dir.resolve("config.yml")));
            } else if (failUpgrade) {
                assertEquals(1, saved.getInt("config-version", 1));
            } else {
                assertEquals(2, saved.getInt("config-version", 1));
                assertFalse(saved.getBoolean("faststats.statistics", true));
                assertFalse(saved.getBoolean("faststats.error-reports", true));
            }

            core.disable();
            fail.set(false);
            ManhuntCore restarted = new ManhuntCore(platform);
            try {
                assertThrows(ConfigLoaded.class, restarted::enable);
                assertFalse(restarted.settings().statistics.on());
                assertFalse(restarted.settings().errorReports.on());
                saved = yaml.load(dir.resolve("config.yml").toFile());
                assertEquals(2, saved.getInt("config-version", 0));
                assertFalse(saved.getBoolean("faststats.statistics", true));
                assertFalse(saved.getBoolean("faststats.error-reports", true));
            } finally {
                restarted.disable();
            }
        } finally {
            try {
                core.disable();
            } finally {
                Platform.set(previousServer);
                costs.set(null, previousCosts);
                waterloggable.set(null, previousWaterloggable);
            }
        }
    }
}
