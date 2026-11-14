package dev.manhuntstar;

import dev.manhuntstar.command.ManhuntCommand;
import dev.manhuntstar.debug.DebugRenderer;
import dev.manhuntstar.fidelity.DeathMessages;
import dev.manhuntstar.fidelity.GlowService;
import dev.manhuntstar.fidelity.TabListService;
import dev.manhuntstar.host.ManhuntPlatform;
import dev.manhuntstar.host.PlatformServices;
import dev.manhuntstar.listener.BotEntityListener;
import dev.manhuntstar.listener.MobTargetGoals;
import dev.manhuntstar.listener.UpdateNotifyListener;
import dev.manhuntstar.listener.WorldPlayerListener;
import dev.manhuntstar.metrics.MetricsService;
import dev.manhuntstar.paper.BukkitYamlFiles;
import dev.manhuntstar.paper.Kinds;
import dev.manhuntstar.paper.PaperAimDiagnostics;
import dev.manhuntstar.paper.PaperBlocks;
import dev.manhuntstar.paper.PaperBotEvents;
import dev.manhuntstar.paper.PaperCommands;
import dev.manhuntstar.paper.PaperItems;
import dev.manhuntstar.paper.PaperMenuScreens;
import dev.manhuntstar.paper.PaperServer;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.YamlFiles;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manhunt*: ManHunt hunter bots for Paper, recreated from a Fabric mod by OpaleMC (CC0).
 * The Paper side of {@link ManhuntCore}: Paper's server and files, the features only Paper has (player
 * list, glow, death messages, mob goals, metrics, events for other plugins), the listeners and the command.
 */
public final class ManhuntStarPlugin extends JavaPlugin implements ManhuntPlatform {

    private static ManhuntStarPlugin instance;

    private final GameServer server = new PaperServer(this);
    private final YamlFiles yaml = new BukkitYamlFiles();
    private final ManhuntCore core = new ManhuntCore(this);
    private PaperMenuScreens menuScreens;
    private MobTargetGoals mobGoals;
    private MetricsService metrics;
    private BukkitTask tickTask;

    public static ManhuntStarPlugin instance() {
        return instance;
    }

    /** Manhunt* itself: the bots, the options and the services. */
    public ManhuntCore core() {
        return core;
    }

    @Override
    public void onEnable() {
        instance = this;
        Platform.set(server);
        Kinds.init();
        PaperBlocks.init();
        PaperItems.init();
        core.enable();
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        core.disable();
        instance = null;
    }

    // ------------------------------------------------------------------ platform

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
        return getLogger();
    }

    @Override
    public File bStatsConfig() {
        return new File(new File(getDataFolder().getParentFile(), "bStats"), "config.yml");
    }

    @Override
    public File dataFolder() {
        return getDataFolder();
    }

    @Override
    public InputStream resource(String path) {
        return getResource(path);
    }

    @Override
    public void saveResource(String path) {
        saveResource(path, false);
    }

    @Override
    public String version() {
        return getPluginMeta().getVersion();
    }

    @Override
    public String description() {
        return "Paper " + Bukkit.getMinecraftVersion();
    }

    @Override
    public PlatformServices services(ManhuntCore core) {
        DebugRenderer debugRenderer = new DebugRenderer();
        TabListService tabList = new TabListService(getLogger(), core.settings().tabList::on);
        tabList.init();
        GlowService glow = new GlowService(core.settings());
        DeathMessages deathMessages = new DeathMessages(core.botManager());
        menuScreens = new PaperMenuScreens(this);
        mobGoals = new MobTargetGoals(this, core.mobTargeting());
        return new PlatformServices(debugRenderer, tabList, glow, deathMessages, new PaperBotEvents(), new PaperAimDiagnostics(), menuScreens);
    }

    @Override
    public void start(ManhuntCore core) {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new BotEntityListener(this), this);
        pm.registerEvents(new WorldPlayerListener(this), this);
        pm.registerEvents(mobGoals, this);
        pm.registerEvents(menuScreens, this);
        pm.registerEvents(new UpdateNotifyListener(core.updates()), this);
        dev.manhuntstar.debug.AimDebug.install(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new ManhuntCommand<>(core, new PaperCommands()).build(), "Manhunt* hunter bots"));
    }

    @Override
    public void started(ManhuntCore core) {
        mobGoals.installAll();
        tickTask = getServer().getScheduler().runTaskTimer(this, core::tick, 1L, 1L);
        metrics = new MetricsService(this);
        metrics.start();
    }
}
