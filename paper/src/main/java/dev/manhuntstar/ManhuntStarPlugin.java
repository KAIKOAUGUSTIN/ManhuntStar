package dev.manhuntstar;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.command.ManhuntCommand;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.debug.DebugRenderer;
import dev.manhuntstar.fidelity.DeathMessages;
import dev.manhuntstar.fidelity.GlowService;
import dev.manhuntstar.fidelity.TabListService;
import dev.manhuntstar.gui.MenuManager;
import dev.manhuntstar.listener.BotEntityListener;
import dev.manhuntstar.listener.MobTargeting;
import dev.manhuntstar.listener.WorldPlayerListener;
import dev.manhuntstar.metrics.MetricsService;
import dev.manhuntstar.paper.BukkitYamlFiles;
import dev.manhuntstar.paper.Kinds;
import dev.manhuntstar.paper.PaperBlocks;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperItems;
import dev.manhuntstar.paper.PaperServer;
import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.persistence.BotStorage;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.skin.SkinService;
import dev.manhuntstar.update.UpdateChecker;
import dev.manhuntstar.util.ChunkTickets;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manhunt*: ManHunt hunter bots for Paper, recreated from a Fabric mod by OpaleMC (CC0).
 * Starts the services, registers events and commands and runs a single global tick for all bots.
 */
public final class ManhuntStarPlugin extends JavaPlugin {

    private static ManhuntStarPlugin instance;

    private final GameServer server = new PaperServer(this);
    private final YamlFiles yaml = new BukkitYamlFiles();
    private final Settings settings = new Settings();
    private final Messages messages = new Messages();
    private PathService pathService;
    private DebugRenderer debugRenderer;
    private SkinService skins;
    private TabListService tabList;
    private GlowService glow;
    private DeathMessages deathMessages;
    private BotStorage storage;
    private BotManager botManager;
    private MenuManager menus;
    private MobTargeting mobTargeting;
    private UpdateChecker updates;
    private MetricsService metrics;
    private final ChunkTickets chunkTickets = new ChunkTickets();
    private BukkitTask tickTask;

    public static ManhuntStarPlugin instance() {
        return instance;
    }

    /** The server as core sees it. */
    public GameServer server() {
        return server;
    }

    /** Reads and writes the plugin's YAML files (config.yml, languages). */
    public YamlFiles yaml() {
        return yaml;
    }

    public ChunkTickets chunkTickets() {
        return chunkTickets;
    }

    @Override
    public void onEnable() {
        instance = this;
        Platform.set(server);
        Kinds.init();
        PaperBlocks.init();
        PaperItems.init();
        migrateLegacyFiles();
        saveDefaultConfig();
        Messages.install(yaml, langFolder(), this::getResource, getLogger());
        settings.setLanguages(() -> Messages.available(langFolder()));
        loadConfigFiles();

        pathService = new PathService(server, settings, getLogger());
        debugRenderer = new DebugRenderer();
        skins = new SkinService(getDataFolder(), getLogger(), settings.skinFallbackUpload::on);
        tabList = new TabListService(getLogger(), settings.tabList::on);
        tabList.init();
        glow = new GlowService(settings);
        storage = new BotStorage(getDataFolder(), getLogger(), yaml);
        botManager = new BotManager(this);
        deathMessages = new DeathMessages(botManager);
        menus = new MenuManager(this);
        mobTargeting = new MobTargeting(this);
        updates = new UpdateChecker(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new BotEntityListener(this), this);
        pm.registerEvents(new WorldPlayerListener(this), this);
        pm.registerEvents(mobTargeting, this);
        pm.registerEvents(menus, this);
        pm.registerEvents(updates, this);
        dev.manhuntstar.debug.AimDebug.install(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new ManhuntCommand(this).build(), "Manhunt* hunter bots"));

        removeOrphans();
        BotStorage.LoadResult saved = storage.load();
        int restored = botManager.restore(saved.bots(), saved.groups());
        botManager.restoreManhunt(saved.manhunt());
        if (restored > 0) {
            getLogger().info(restored + " bot(s) restored from bots.yml.");
        }
        mobTargeting.installAll();
        tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        updates.apply(settings.updateCheck.on());
        metrics = new MetricsService(this);
        metrics.start();
        getLogger().info("Manhunt* enabled (Paper " + Bukkit.getMinecraftVersion() + ", language " + messages.language() + ").");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (updates != null) {
            updates.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        if (botManager != null) {
            botManager.shutdown();
        }
        chunkTickets.releaseAll();
        if (tabList != null) {
            tabList.clear();
        }
        if (glow != null) {
            glow.cleanup();
        }
        if (pathService != null) {
            pathService.shutdown();
        }
        if (storage != null) {
            storage.shutdown();
        }
        if (skins != null) {
            skins.shutdown();
        }
        instance = null;
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        pathService.tick();
        botManager.tick();
        mobTargeting.tick(now);
        debugRenderer.tick(now, settings.debugPaths.on());
        tabList.tick(now);
    }

    /** Bot mannequins left in the world (e.g. a server crash): removed before restoring. */
    private void removeOrphans() {
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Mannequin.class)) {
                if (botManager.isBotEntity(PaperEntities.of(e))) {
                    e.remove();
                }
            }
            for (Entity e : w.getEntitiesByClass(org.bukkit.entity.AbstractHorse.class)) {
                botManager.removeOrphan(PaperEntities.of(e));
            }
        }
    }

    // ------------------------------------------------------------------ configuration

    private File langFolder() {
        return new File(getDataFolder(), Messages.FOLDER);
    }

    private void loadConfigFiles() {
        settings.load(yaml, new File(getDataFolder(), "config.yml"), getLogger());
        loadMessages();
    }

    private void loadMessages() {
        messages.load(yaml, langFolder(), settings.language.get(), this::getResource, getLogger());
    }

    /**
     * Files from versions up to 1.5, with Portuguese keys: the old config.yml becomes
     * config-old.yml and its values go into a new config.yml (with the language set to pt-br, the only
     * one there was); messages.yml becomes messages-old.yml, because the texts now live in the lang/ folder.
     */
    private void migrateLegacyFiles() {
        File config = new File(getDataFolder(), "config.yml");
        if (config.exists() && Settings.isLegacy(yaml, config)) {
            settings.loadLegacy(yaml, config, getLogger());
            File backup = new File(getDataFolder(), "config-old.yml");
            try {
                Files.move(config.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                saveResource("config.yml", false);
                settings.save(yaml, config, getLogger());
                getLogger().info("config.yml moved to the new English keys (language pt-br); the old file is config-old.yml.");
            } catch (IOException e) {
                getLogger().warning("Could not migrate config.yml: " + e.getMessage());
            }
        }
        File oldMessages = new File(getDataFolder(), "messages.yml");
        if (oldMessages.exists()) {
            try {
                Files.move(oldMessages.toPath(), new File(getDataFolder(), "messages-old.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("messages.yml is no longer used: texts now live in the " + Messages.FOLDER
                        + "/ folder (the old file is messages-old.yml).");
            } catch (IOException e) {
                getLogger().warning("Could not rename messages.yml: " + e.getMessage());
            }
        }
    }

    /** /manhunt reload: reads config.yml and the language files again and applies them. */
    public void reloadAll() {
        loadConfigFiles();
        for (Setting<?> s : settings.all()) {
            applySideEffects(s);
        }
    }

    /** An option changed (command or menu): applies its effect and writes config.yml. */
    public void onSettingChanged(Setting<?> s) {
        applySideEffects(s);
        saveSettings();
    }

    /** Writes config.yml (options, player list and ring point). */
    public void saveSettings() {
        settings.save(yaml, new File(getDataFolder(), "config.yml"), getLogger());
    }

    private void applySideEffects(Setting<?> s) {
        if (s == settings.language) {
            loadMessages();
        } else if (s == settings.glowHunters || s == settings.glowEscapers || s == settings.glowHuntersColor || s == settings.glowEscapersColor) {
            glow.applyAll(botManager.aliveBots());
        } else if (s == settings.tabList) {
            tabList.resync();
        } else if (s == settings.nodeCap || s == settings.dynamicNodes) {
            pathService.resetNodeCap();
        } else if (s == settings.showNameTag) {
            for (Bot b : botManager.aliveBots()) {
                b.mannequin().setCustomNameVisible(settings.showNameTag.on());
            }
        } else if (s == settings.botHealth) {
            for (Bot b : botManager.aliveBots()) {
                var attr = b.mannequin().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(settings.botHealth.value());
                }
                b.setHealth(Math.min(b.health(), settings.botHealth.value()));
            }
        } else if (s == settings.pathMode) {
            for (Bot b : botManager.aliveBots()) {
                b.brain().navigator().reset();
            }
        } else if (s == settings.persistentBots) {
            botManager.markDirty();
            botManager.flush(true);
        } else if (s == settings.updateCheck) {
            updates.apply(settings.updateCheck.on());
        } else if (s == settings.debugPaths && !settings.debugPaths.on()) {
            debugRenderer.clearAll();
        }
    }

    // ------------------------------------------------------------------ access

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public PathService pathService() {
        return pathService;
    }

    public DebugRenderer debugRenderer() {
        return debugRenderer;
    }

    public SkinService skins() {
        return skins;
    }

    public TabListService tabList() {
        return tabList;
    }

    public GlowService glow() {
        return glow;
    }

    public DeathMessages deathMessages() {
        return deathMessages;
    }

    public BotStorage storage() {
        return storage;
    }

    public BotManager botManager() {
        return botManager;
    }

    public MenuManager menus() {
        return menus;
    }

    public UpdateChecker updates() {
        return updates;
    }
}
