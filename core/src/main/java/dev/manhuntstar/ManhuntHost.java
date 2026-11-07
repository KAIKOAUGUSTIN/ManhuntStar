package dev.manhuntstar;

import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.host.AimDiagnostics;
import dev.manhuntstar.host.BotEventSink;
import dev.manhuntstar.host.BotGlow;
import dev.manhuntstar.host.BotTabList;
import dev.manhuntstar.host.DeathMessageSource;
import dev.manhuntstar.host.Menus;
import dev.manhuntstar.host.PathDebug;
import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.persistence.BotStorage;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.skin.SkinService;
import dev.manhuntstar.util.ChunkTickets;
import java.util.logging.Logger;

/**
 * What the bots need from the plugin or mod running them: the server, the options, the services and the
 * platform's own features (glow, player list, skins, death messages, events).
 */
public interface ManhuntHost {

    GameServer server();

    YamlFiles yaml();

    Logger logger();

    Settings settings();

    Messages messages();

    PathService pathService();

    ChunkTickets chunkTickets();

    BotManager botManager();

    BotStorage storage();

    SkinService skins();

    BotGlow glow();

    BotTabList tabList();

    DeathMessageSource deathMessages();

    PathDebug debugRenderer();

    BotEventSink events();

    AimDiagnostics aim();

    Menus menus();

    /** Reads config.yml and the language files again and applies them (/manhunt reload). */
    void reloadAll();

    /** An option changed (command or menu): applies its effect and writes config.yml. */
    void onSettingChanged(Setting<?> setting);

    /** Writes config.yml (options, player list and ring point). */
    void saveSettings();
}
