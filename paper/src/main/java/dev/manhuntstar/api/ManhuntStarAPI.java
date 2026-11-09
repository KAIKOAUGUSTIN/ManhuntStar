package dev.manhuntstar.api;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.paper.PaperEntities;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Entity;

/**
 * Entry point for other plugins: lists the bots, finds the bot of an entity and exposes the
 * manager. The events live in {@link BotEvents}.
 */
public final class ManhuntStarAPI {

    private ManhuntStarAPI() {
    }

    private static ManhuntStarPlugin plugin() {
        return ManhuntStarPlugin.instance();
    }

    public static Collection<Bot> getBots() {
        return plugin().core().botManager().allBots();
    }

    public static Bot getBot(UUID botId) {
        return plugin().core().botManager().bot(botId);
    }

    public static Bot getBot(String name) {
        return plugin().core().botManager().bot(name);
    }

    /** The bot that owns this entity (Mannequin), or null if it is not a bot. */
    public static Bot getBot(Entity entity) {
        return plugin().core().botManager().byEntity(PaperEntities.of(entity));
    }

    public static boolean isBot(Entity entity) {
        return plugin().core().botManager().isBotEntity(PaperEntities.of(entity));
    }
}
