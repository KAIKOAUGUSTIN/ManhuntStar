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
import dev.manhuntstar.pathfinding.PathService;
import dev.manhuntstar.persistence.BotStorage;
import dev.manhuntstar.skin.SkinService;
import dev.manhuntstar.util.ChunkTickets;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manhunt*: bots caçadores de ManHunt para Paper, recriados a partir de um mod Fabric de OpaleMC (CC0).
 * Liga os serviços, registra eventos e comandos e roda um único tick global para todos os bots.
 */
public final class ManhuntStarPlugin extends JavaPlugin {

    private static ManhuntStarPlugin instance;

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
    private final ChunkTickets chunkTickets = new ChunkTickets(this);
    private BukkitTask tickTask;

    public static ManhuntStarPlugin instance() {
        return instance;
    }

    public ChunkTickets chunkTickets() {
        return chunkTickets;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        loadConfigFiles();

        pathService = new PathService(settings, getLogger());
        debugRenderer = new DebugRenderer();
        skins = new SkinService(getDataFolder(), getLogger(), settings.skinFallbackUpload::on);
        tabList = new TabListService(getLogger(), settings.tabList::on);
        tabList.init();
        glow = new GlowService(settings);
        storage = new BotStorage(getDataFolder(), getLogger());
        botManager = new BotManager(this);
        deathMessages = new DeathMessages(botManager);
        menus = new MenuManager(this);
        mobTargeting = new MobTargeting(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new BotEntityListener(this), this);
        pm.registerEvents(new WorldPlayerListener(this), this);
        pm.registerEvents(mobTargeting, this);
        pm.registerEvents(menus, this);
        dev.manhuntstar.debug.AimDebug.install(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new ManhuntCommand(this).build(), "Bots caçadores do Manhunt*"));

        removeOrphans();
        BotStorage.LoadResult saved = storage.load();
        int restored = botManager.restore(saved.bots(), saved.groups());
        botManager.restoreManhunt(saved.manhunt());
        if (restored > 0) {
            getLogger().info(restored + " bot(s) restaurado(s) do bots.yml.");
        }
        mobTargeting.installAll();
        tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Manhunt* ligado (Paper " + Bukkit.getMinecraftVersion() + ").");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
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

    /** Mannequins de bots que ficaram no mundo (ex.: queda do servidor): remove antes de restaurar. */
    private void removeOrphans() {
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Mannequin.class)) {
                if (botManager.isBotEntity(e)) {
                    e.remove();
                }
            }
            for (Entity e : w.getEntitiesByClass(org.bukkit.entity.AbstractHorse.class)) {
                botManager.removeOrphan(e);
            }
        }
    }

    // ------------------------------------------------------------------ configuração

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void loadConfigFiles() {
        settings.load(new File(getDataFolder(), "config.yml"), getLogger());
        upgradeMessages();
        messages.load(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"), getLogger());
    }

    /**
     * messages.yml de uma versão anterior (os textos padrão mudaram, por exemplo os nomes dos
     * comandos): guarda o antigo como messages-v<versão>.yml e grava o novo no lugar.
     */
    private void upgradeMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            return;
        }
        int bundled;
        try (InputStream in = getResource("messages.yml")) {
            if (in == null) {
                return;
            }
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getInt("versao", 1);
        } catch (IOException e) {
            return;
        }
        int current = YamlConfiguration.loadConfiguration(file).getInt("versao", 1);
        if (current >= bundled) {
            return;
        }
        File backup = new File(getDataFolder(), "messages-v" + current + ".yml");
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Não foi possível atualizar o messages.yml: " + e.getMessage());
            return;
        }
        saveResource("messages.yml", false);
        getLogger().info("messages.yml atualizado para a versão " + bundled + "; o anterior ficou em " + backup.getName() + ".");
    }

    /** /manhunt reload: relê config.yml e messages.yml e aplica. */
    public void reloadAll() {
        loadConfigFiles();
        for (Setting<?> s : settings.all()) {
            applySideEffects(s);
        }
    }

    /** Uma opção mudou (comando ou menu): aplica o efeito e grava no config.yml. */
    public void onSettingChanged(Setting<?> s) {
        applySideEffects(s);
        saveSettings();
    }

    /** Grava o config.yml (opções, lista de jogadores e ponto da roda). */
    public void saveSettings() {
        settings.save(new File(getDataFolder(), "config.yml"), getLogger());
    }

    private void applySideEffects(Setting<?> s) {
        if (s == settings.glowHunters || s == settings.glowEscapers || s == settings.glowHuntersColor || s == settings.glowEscapersColor) {
            glow.applyAll(botManager.aliveBots());
        } else if (s == settings.tabList) {
            tabList.resync();
        } else if (s == settings.nodeCap || s == settings.dynamicNodes) {
            pathService.resetNodeCap();
        } else if (s == settings.showNameTag) {
            for (Bot b : botManager.aliveBots()) {
                b.entity().setCustomNameVisible(settings.showNameTag.on());
            }
        } else if (s == settings.botHealth) {
            for (Bot b : botManager.aliveBots()) {
                var attr = b.entity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
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
        } else if (s == settings.debugPaths && !settings.debugPaths.on()) {
            debugRenderer.clearAll();
        }
    }

    // ------------------------------------------------------------------ acesso

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
}
