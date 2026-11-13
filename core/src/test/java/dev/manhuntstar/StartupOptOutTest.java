package dev.manhuntstar;

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
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupOptOutTest {
    @TempDir
    Path dir;

    private static class ConfigLoaded extends RuntimeException {}

    @Test
    void failedOptOutSaveStillDisablesBothOptionsBeforePlatformStarts() throws Exception {
        Logger logger = Logger.getAnonymousLogger();
        YamlFiles yaml = new SnakeYamlFiles(logger);
        YamlFiles failing = new FailingSaveYamlFiles(yaml, file -> {});
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
            assertTrue(yaml.load(dir.resolve("config.yml").toFile()).getBoolean("faststats.statistics", false));
            assertTrue(yaml.load(dir.resolve("config.yml").toFile()).getBoolean("faststats.error-reports", false));
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
