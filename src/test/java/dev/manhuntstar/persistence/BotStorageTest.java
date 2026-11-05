package dev.manhuntstar.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.manhuntstar.bot.BotGroup;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** bots.yml: an older save that was still queued doesn't overwrite a newer one. */
class BotStorageTest {

    @Test
    void anOlderQueuedSaveDoesNotOverwriteANewerOne(@TempDir Path dir) {
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"));
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
}
