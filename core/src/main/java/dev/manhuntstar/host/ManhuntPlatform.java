package dev.manhuntstar.host;

import dev.manhuntstar.ManhuntCore;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * The plugin or mod running Manhunt*: what {@link ManhuntCore} needs from it, and the moments of the start
 * when the platform does its own part (in this order: {@link #services}, {@link #start}, {@link #started}).
 */
public interface ManhuntPlatform {

    GameServer server();

    /** Reads and writes the YAML files (config.yml, languages, bots.yml). */
    YamlFiles yaml();

    Logger logger();

    /** The folder of config.yml, bots.yml, the languages and the skins. */
    File dataFolder();

    /**
     * FastStats' own config.properties, shared by every plugin or mod that uses it: next to the data
     * folder ({@code plugins/faststats} on Paper, {@code config/faststats} on the mods).
     */
    default File fastStatsConfig() {
        return new File(new File(dataFolder().getParentFile(), "faststats"), "config.properties");
    }

    /** A file bundled in the jar (config.yml, the languages), or null. */
    InputStream resource(String path);

    /** Copies a file bundled in the jar to the data folder, where it isn't yet. */
    void saveResource(String path);

    /** The plugin's or mod's version (for the update check). */
    String version();

    /** The platform and the game's version, for the line logged at the start ("Paper 26.3"). */
    String description();

    /** Creates the platform's own services, once core's services and the bot manager exist. */
    PlatformServices services(ManhuntCore core);

    /** Registers the listeners and the commands, before the saved bots come back. */
    void start(ManhuntCore core);

    /** With the saved bots back: starts calling {@link ManhuntCore#tick} every tick, and the rest of the platform. */
    void started(ManhuntCore core);
}
