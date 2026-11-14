package dev.manhuntstar.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.config.SnakeYamlFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** bots.yml: an older save that was still queued doesn't overwrite a newer one; an unreadable file isn't overwritten; versions. */
class BotStorageTest {

    /** core's own YAML files (SnakeYAML), as the mods use; BukkitYamlFiles has the same contract (YamlFilesContract). */
    private static SnakeYamlFiles yaml() {
        return new SnakeYamlFiles(Logger.getLogger("teste-yaml"));
    }

    @Test
    void anOlderQueuedSaveDoesNotOverwriteANewerOne(@TempDir Path dir) {
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"), yaml());
        // the background write waits for this monitor (the same one write uses): it only runs after the
        // direct save, as when the server shuts down right after a periodic save
        synchronized (storage) {
            storage.save(List.of(), List.of(new BotGroup("antigo")), null, true);
            storage.save(List.of(), List.of(new BotGroup("novo")), null, false);
        }
        storage.shutdown();
        List<BotGroup> groups = storage.load().groups();
        assertEquals(1, groups.size());
        assertEquals("novo", groups.getFirst().name());
    }

    @Test
    void aSaveWritesTheVersion(@TempDir Path dir) {
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"), yaml());
        storage.save(List.of(), List.of(), null, false);
        assertEquals(BotStorage.VERSIONS.latest(), yaml().load(dir.resolve("bots.yml").toFile()).getInt("versao", 0));
    }

    @Test
    void anUnreadableBotsFileIsNotOverwritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bots.yml");
        // a YAML error (a tab before a key) in a file that has saved bots
        String broken = "versao: 1\nbots:\n\tabc:\n    nome: Hunter\n";
        Files.writeString(file, broken);
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste-ilegivel"), yaml());
        BotStorage.LoadResult loaded = storage.load();
        assertTrue(loaded.bots().isEmpty());
        assertTrue(loaded.groups().isEmpty());
        // what a pause, a ring or a command does after the start: a save of the (empty) state
        storage.save(List.of(), List.of(new BotGroup("nova")), null, false);
        storage.save(List.of(), List.of(), null, true);
        storage.shutdown();
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void aNewerBotsFileIsLoadedWithAWarning(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bots.yml"), "versao: 99\ngrupos:\n  equipe:\n    nome: Equipe\n");
        Logger logger = Logger.getLogger("teste-versao");
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            List<BotGroup> groups = new BotStorage(dir.toFile(), logger, yaml()).load().groups();
            assertEquals("Equipe", groups.getFirst().name());
            assertTrue(records.stream().anyMatch(r -> r.getMessage().contains("newer ManhuntStar")));
        } finally {
            logger.removeHandler(handler);
        }
    }
}
