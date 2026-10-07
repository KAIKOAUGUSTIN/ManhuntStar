package dev.manhuntstar.api;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Entity;

/**
 * Ponto de entrada para outros plugins: lista os bots, encontra o bot de uma entidade e expõe
 * o gerenciador. Os eventos ficam em {@link BotEvents}.
 */
public final class ManhuntStarAPI {

    private ManhuntStarAPI() {
    }

    private static ManhuntStarPlugin plugin() {
        return ManhuntStarPlugin.instance();
    }

    public static Collection<Bot> getBots() {
        return plugin().botManager().allBots();
    }

    public static Bot getBot(UUID botId) {
        return plugin().botManager().bot(botId);
    }

    public static Bot getBot(String name) {
        return plugin().botManager().bot(name);
    }

    /** O bot dono desta entidade (Mannequin), ou null se não for um bot. */
    public static Bot getBot(Entity entity) {
        return plugin().botManager().byEntity(entity);
    }

    public static boolean isBot(Entity entity) {
        return plugin().botManager().isBotEntity(entity);
    }
}
