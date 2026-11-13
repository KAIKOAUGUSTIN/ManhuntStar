package dev.manhuntstar.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.paper.BukkitYamlFiles;
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

/** bots.yml: an older save that was still queued doesn't overwrite a newer one; versions. */
class BotStorageTest {

    @Test
    void anOlderQueuedSaveDoesNotOverwriteANewerOne(@TempDir Path dir) {
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"), new BukkitYamlFiles());
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
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"), new BukkitYamlFiles());
        storage.save(List.of(), List.of(), null, false);
        assertEquals(BotStorage.VERSIONS.latest(), new BukkitYamlFiles().load(dir.resolve("bots.yml").toFile()).getInt("versao", 0));
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
            List<BotGroup> groups = new BotStorage(dir.toFile(), logger, new BukkitYamlFiles()).load().groups();
            assertEquals("Equipe", groups.getFirst().name());
            assertTrue(records.stream().anyMatch(r -> r.getMessage().contains("newer ManhuntStar")));
        } finally {
            logger.removeHandler(handler);
        }
    }
}
