package dev.manhuntstar.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.manhuntstar.bot.BotGroup;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** bots.yml: um salvamento mais velho que ainda estava na fila não grava por cima de um mais novo. */
class BotStorageTest {

    @Test
    void anOlderQueuedSaveDoesNotOverwriteANewerOne(@TempDir Path dir) {
        BotStorage storage = new BotStorage(dir.toFile(), Logger.getLogger("teste"));
        // a gravação em segundo plano espera este monitor (o mesmo do write): ela só roda depois do
        // salvamento direto, como quando o servidor desliga logo depois de um salvamento periódico
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
