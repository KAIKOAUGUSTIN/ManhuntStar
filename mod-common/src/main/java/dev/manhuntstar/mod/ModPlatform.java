package dev.manhuntstar.mod;

import com.mojang.brigadier.CommandDispatcher;
import dev.manhuntstar.ManhuntCore;
import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.command.ManhuntCommand;
import dev.manhuntstar.host.ManhuntPlatform;
import dev.manhuntstar.host.PlatformServices;
import dev.manhuntstar.metrics.UsageStats;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.ScheduledTask;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * The mods' side of {@link ManhuntCore}, the same for Fabric and NeoForge: Minecraft's server, the config
 * folder, the TAB list, glow, death messages and mob goals. The loader creates it once and calls
 * {@link #serverStarted}, {@link #serverStopping} and {@link #registerCommands}; the game's events reach
 * core through the mixins and {@link ModHooks}.
 *
 * <p>A new core is made for every server start (in singleplayer, each world opened is a new server).
 */
public final class ModPlatform implements ManhuntPlatform {

    private static volatile ModPlatform instance;

    /**
     * The core of the running server, for the command tree: the loaders register commands before the
     * server exists, so the tree reaches core through this.
     */
    private static final ManhuntHost HOST = (ManhuntHost) Proxy.newProxyInstance(ManhuntHost.class.getClassLoader(), new Class<?>[]{ManhuntHost.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "ManhuntHost(current server)";
                    };
                }
                ManhuntCore core = instance == null ? null : instance.core;
                if (core == null) {
                    throw new IllegalStateException("Manhunt* isn't running");
                }
                try {
                    return method.invoke(core, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });

    private final Logger logger;
    private final File dataFolder;
    private final String version;
    private final String loader;
    private final YamlFiles yaml;

    private ModServer server;
    private ManhuntCore core;
    /**
     * The values sent to FastStats, of the last core started. Kept when the server stops: FastStats sends
     * once more when it stops, after Manhunt* has (ModStatistics). Read by FastStats' thread.
     */
    private volatile UsageStats usage;
    private volatile boolean running;
    private ModMobGoals mobGoals;
    private ModAimDiagnostics aim;
    private ScheduledTask tickTask;

    /**
     * @param dataFolder where config.yml, lang/ and bots.yml go (config/manhuntstar)
     * @param version the mod's version
     * @param loader the loader's name and version, for the log ("Fabric 0.20.1")
     */
    public ModPlatform(Logger logger, File dataFolder, String version, String loader) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.version = version;
        this.loader = loader;
        this.yaml = new ModYamlFiles(logger);
        instance = this;
    }

    /** The platform, while the server runs and Manhunt* is on (null otherwise). */
    static ModPlatform running() {
        ModPlatform p = instance;
        return p != null && p.running ? p : null;
    }

    ManhuntCore core() {
        return core;
    }

    /** Null until a server has started. */
    UsageStats usage() {
        return usage;
    }

    ModMobGoals mobGoals() {
        return mobGoals;
    }

    ModAimDiagnostics aim() {
        return aim;
    }

    // ------------------------------------------------------------------ loader calls

    /** Adds /manhunt to the game's commands (each time the loader rebuilds them). */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.getRoot().addChild(new ManhuntCommand<>(HOST, new ModCommands(context)).build());
    }

    /** The server finished starting (worlds loaded): Manhunt* starts. */
    public void serverStarted(MinecraftServer minecraft) {
        try {
            server = new ModServer(minecraft, logger);
            Platform.set(server);
            ModTypes.init(minecraft.overworld());
            core = new ManhuntCore(this);
            usage = core.usage();
            core.enable();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Manhunt* could not start", t);
            running = false;
        }
    }

    /** The server is stopping: Manhunt* saves and stops. */
    public void serverStopping() {
        running = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        try {
            if (core != null) {
                core.disable();
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Manhunt* could not stop cleanly", t);
        } finally {
            if (server != null) {
                server.shutdown();
            }
            core = null;
            server = null;
            mobGoals = null;
            ModWorld.forgetAll();
        }
    }

    /** The start of a server tick: the scheduler runs what is due (core's tick among it). */
    public void serverTick() {
        ModServer s = server;
        if (s != null) {
            s.heartbeat();
        }
    }

    // ------------------------------------------------------------------ ManhuntPlatform

    @Override
    public GameServer server() {
        return server;
    }

    @Override
    public YamlFiles yaml() {
        return yaml;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public File dataFolder() {
        return dataFolder;
    }

    @Override
    public InputStream resource(String path) {
        return ModPlatform.class.getClassLoader().getResourceAsStream(path);
    }

    /** Copies a file from the jar into the config folder, unless it is already there (as Bukkit's saveResource). */
    @Override
    public void saveResource(String path) {
        File out = new File(dataFolder, path);
        if (out.exists()) {
            logger.warning("Could not save " + out.getName() + " to " + out + " because " + out.getName() + " already exists.");
            return;
        }
        try (InputStream in = resource(path)) {
            if (in == null) {
                throw new IllegalArgumentException("The embedded resource '" + path + "' cannot be found");
            }
            Files.createDirectories(out.toPath().getParent());
            Files.copy(in, out.toPath());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + out.getName() + " to " + out, e);
        }
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String description() {
        return loader + ", Minecraft " + SharedConstants.getCurrentVersion().name();
    }

    @Override
    public PlatformServices services(ManhuntCore core) {
        ModTabList tabList = new ModTabList(core.settings().tabList::on);
        aim = new ModAimDiagnostics(logger);
        mobGoals = new ModMobGoals(core.mobTargeting());
        return new PlatformServices(new ModPathDebug(), tabList, new ModGlow(core.settings()), new ModDeathMessages(core.botManager()),
                new ModBotEvents(), aim, new ModMenuScreens(core));
    }

    /** The game's events start reaching core (as the plugin registers its listeners here). */
    @Override
    public void start(ManhuntCore core) {
        running = true;
    }

    @Override
    public void started(ManhuntCore core) {
        mobGoals.installAll();
        tickTask = server.runTimer(core::tick, 1L, 1L);
    }
}
