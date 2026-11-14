package dev.manhuntstar.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the platforms find FastStats' and bStats' own files unless they say otherwise. */
class ManhuntPlatformTest {

    @TempDir
    Path dir;

    /** A platform that only has its data folder; the rest are the interface's defaults. */
    private ManhuntPlatform platform(File dataFolder) {
        return (ManhuntPlatform) Proxy.newProxyInstance(ManhuntPlatform.class.getClassLoader(), new Class<?>[] {ManhuntPlatform.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("dataFolder")) {
                        return dataFolder;
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void fastStatsConfigIsNextToTheDataFolder() {
        File dataFolder = dir.resolve("plugins").resolve("ManhuntStar").toFile();
        assertEquals(dir.resolve("plugins").resolve("faststats").resolve("config.properties").toFile(), platform(dataFolder).fastStatsConfig());
    }

    @Test
    void noBStatsConfigWhereManhuntStarNeverUsedBStats() {
        assertNull(platform(dir.toFile()).bStatsConfig());
    }
}
