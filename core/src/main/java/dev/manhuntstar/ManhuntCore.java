package dev.manhuntstar;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.bot.GameEvents;
import dev.manhuntstar.bot.MobTargeting;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.config.Setting;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.gui.MenuManager;
import dev.manhuntstar.host.AimDiagnostics;
import dev.manhuntstar.host.BotEventSink;
import dev.manhuntstar.host.BotGlow;
import dev.manhuntstar.host.BotTabList;
import dev.manhuntstar.host.DeathMessageSource;
import dev.manhuntstar.host.ManhuntPlatform;
import dev.manhuntstar.host.PathDebug;
import dev.manhuntstar.host.PlatformServices;
import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.persistence.BotStorage;
import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.skin.SkinService;
import dev.manhuntstar.update.UpdateChecker;
import dev.manhuntstar.util.ChunkTickets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Manhunt* itself, the same on every platform: starts the services, restores the saved bots, runs a single
 * global tick for all bots, applies the options when they change and stops it all. The plugin or mod
 * creates one with its {@link ManhuntPlatform} and calls {@link #enable}, {@link #tick} and {@link #disable}.
 */
public final class ManhuntCore implements ManhuntHost {

    private final ManhuntPlatform platform;
    private final GameServer server;
    private final YamlFiles yaml;
    private final Logger logger;
    private final File dataFolder;
    private final Settings settings = new Settings();
    private final Messages messages = new Messages();
    private final ChunkTickets chunkTickets = new ChunkTickets();
    private final GameEvents gameEvents = new GameEvents(this);
    private PathService pathService;
    private SkinService skins;
    private BotStorage storage;
    private BotManager botManager;
    private MobTargeting mobTargeting;
    private UpdateChecker updates;
    private PlatformServices services;
    private MenuManager menus;

    public ManhuntCore(ManhuntPlatform platform) {
        this.platform = platform;
        this.server = platform.server();
        this.yaml = platform.yaml();
        this.logger = platform.logger();
        this.dataFolder = platform.dataFolder();
    }

    /** Starts everything, once the platform has registered the game's blocks, items and entity types. */
    public void enable() {
        migrateLegacyFiles();
        if (!new File(dataFolder, "config.yml").exists()) {
            platform.saveResource("config.yml");
        }
        Messages.install(yaml, langFolder(), platform::resource, logger);
        settings.setLanguages(() -> Messages.available(langFolder()));
        loadConfigFiles();

        pathService = new PathService(server, settings, logger);
        skins = new SkinService(dataFolder, logger, yaml, settings.skinFallbackUpload::on);
        storage = new BotStorage(dataFolder, logger, yaml);
        botManager = new BotManager(this);
        mobTargeting = new MobTargeting(this);
        updates = new UpdateChecker(this, platform.version());
        services = platform.services(this);
        menus = new MenuManager(this, services.menuScreens());
        platform.start(this);

        removeOrphans();
        BotStorage.LoadResult saved = storage.load();
        int restored = botManager.restore(saved.bots(), saved.groups());
        botManager.restoreManhunt(saved.manhunt());
        if (restored > 0) {
            logger.info(restored + " bot(s) restored from bots.yml.");
        }
        platform.started(this);
        updates.apply(settings.updateCheck.on());
        logger.info("Manhunt* enabled (" + platform.description() + ", language " + messages.language() + ").");
    }

    /** Stops everything, once the platform no longer calls {@link #tick}. */
    public void disable() {
        if (updates != null) {
            updates.shutdown();
        }
        if (botManager != null) {
            botManager.shutdown();
        }
        chunkTickets.releaseAll();
        if (services != null) {
            services.tabList().clear();
            services.glow().cleanup();
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
    }

    /** The single global tick of all bots (once per server tick). */
    public void tick() {
        int now = server.currentTick();
        pathService.tick();
        botManager.tick();
        mobTargeting.tick(now);
        services.debugRenderer().tick(now, settings.debugPaths.on());
        services.tabList().tick(now);
    }

    /** Bot mannequins left in the world (e.g. a server crash): removed before restoring. */
    private void removeOrphans() {
        for (GameWorld w : server.worlds()) {
            for (GameEntity e : w.entities()) {
                if (e instanceof BotBody && botManager.isBotEntity(e)) {
                    e.remove();
                }
            }
            for (GameEntity e : w.entities()) {
                if (e instanceof GameHorse) {
                    botManager.removeOrphan(e);
                }
            }
        }
    }

    // ------------------------------------------------------------------ configuration

    private File langFolder() {
        return new File(dataFolder, Messages.FOLDER);
    }

    private void loadConfigFiles() {
        settings.load(yaml, new File(dataFolder, "config.yml"), logger);
        loadMessages();
    }

    private void loadMessages() {
        messages.load(yaml, langFolder(), settings.language.get(), platform::resource, logger);
    }

    /**
     * Files from versions up to 1.5, with Portuguese keys: the old config.yml becomes
     * config-old.yml and its values go into a new config.yml (with the language set to pt-br, the only
     * one there was); messages.yml becomes messages-old.yml, because the texts now live in the lang/ folder.
     */
    private void migrateLegacyFiles() {
        File config = new File(dataFolder, "config.yml");
        if (config.exists() && Settings.isLegacy(yaml, config)) {
            settings.loadLegacy(yaml, config, logger);
            File backup = new File(dataFolder, "config-old.yml");
            try {
                Files.move(config.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                platform.saveResource("config.yml");
                settings.save(yaml, config, logger);
                logger.info("config.yml moved to the new English keys (language pt-br); the old file is config-old.yml.");
            } catch (IOException e) {
                logger.warning("Could not migrate config.yml: " + e.getMessage());
            }
        }
        File oldMessages = new File(dataFolder, "messages.yml");
        if (oldMessages.exists()) {
            try {
                Files.move(oldMessages.toPath(), new File(dataFolder, "messages-old.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("messages.yml is no longer used: texts now live in the " + Messages.FOLDER
                        + "/ folder (the old file is messages-old.yml).");
            } catch (IOException e) {
                logger.warning("Could not rename messages.yml: " + e.getMessage());
            }
        }
    }

    /** /manhunt reload: reads config.yml and the language files again and applies them. */
    @Override
    public void reloadAll() {
        loadConfigFiles();
        for (Setting<?> s : settings.all()) {
            applySideEffects(s);
        }
    }

    /** An option changed (command or menu): applies its effect and writes config.yml. */
    @Override
    public void onSettingChanged(Setting<?> s) {
        applySideEffects(s);
        saveSettings();
    }

    /** Writes config.yml (options, player list and ring point). */
    @Override
    public void saveSettings() {
        settings.save(yaml, new File(dataFolder, "config.yml"), logger);
    }

    private void applySideEffects(Setting<?> s) {
        if (s == settings.language) {
            loadMessages();
        } else if (s == settings.glowHunters || s == settings.glowEscapers || s == settings.glowHuntersColor || s == settings.glowEscapersColor) {
            services.glow().applyAll(botManager.aliveBots());
        } else if (s == settings.tabList) {
            services.tabList().resync();
        } else if (s == settings.nodeCap || s == settings.dynamicNodes) {
            pathService.resetNodeCap();
        } else if (s == settings.showNameTag) {
            for (Bot b : botManager.aliveBots()) {
                b.entity().setCustomNameVisible(settings.showNameTag.on());
            }
        } else if (s == settings.botHealth) {
            for (Bot b : botManager.aliveBots()) {
                b.entity().setAttributeBase(Attr.MAX_HEALTH, settings.botHealth.value());
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
            services.debugRenderer().clearAll();
        }
    }

    // ------------------------------------------------------------------ access

    /** What the bots do when the game reports something; the platform's listeners call it. */
    public GameEvents gameEvents() {
        return gameEvents;
    }

    public MobTargeting mobTargeting() {
        return mobTargeting;
    }

    public UpdateChecker updates() {
        return updates;
    }

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
    public Settings settings() {
        return settings;
    }

    @Override
    public Messages messages() {
        return messages;
    }

    @Override
    public PathService pathService() {
        return pathService;
    }

    @Override
    public ChunkTickets chunkTickets() {
        return chunkTickets;
    }

    @Override
    public BotManager botManager() {
        return botManager;
    }

    @Override
    public BotStorage storage() {
        return storage;
    }

    @Override
    public SkinService skins() {
        return skins;
    }

    @Override
    public BotGlow glow() {
        return services.glow();
    }

    @Override
    public BotTabList tabList() {
        return services.tabList();
    }

    @Override
    public DeathMessageSource deathMessages() {
        return services.deathMessages();
    }

    @Override
    public PathDebug debugRenderer() {
        return services.debugRenderer();
    }

    @Override
    public BotEventSink events() {
        return services.events();
    }

    @Override
    public AimDiagnostics aim() {
        return services.aim();
    }

    @Override
    public MenuManager menus() {
        return menus;
    }
}
