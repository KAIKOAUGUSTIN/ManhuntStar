package dev.manhuntstar.host;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.util.Pos;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shows a bot's path in the world (the {@code debug.path} option). */
public interface PathDebug {

    void update(Bot bot, List<Pos> path, int index, Set<Pos> mine, Set<Pos> place);

    void clear(UUID botId);
}
