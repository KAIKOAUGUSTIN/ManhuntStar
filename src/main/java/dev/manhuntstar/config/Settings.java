package dev.manhuntstar.config;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.GearPersistence;
import dev.manhuntstar.bot.Modes.GlowColor;
import dev.manhuntstar.bot.Modes.LateJoin;
import dev.manhuntstar.bot.Modes.PathMode;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.bot.Modes.PlayerListMode;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.bot.Modes.SpawnMode;
import dev.manhuntstar.bot.Modes.TargetMode;
import dev.manhuntstar.config.Setting.Category;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

/**
 * Todas as opções do plugin. As chaves de comando ({@code /manhunt settings <chave>}) e o config.yml
 * são em inglês; os caminhos em português das versões antigas só são lidos na migração.
 */
public final class Settings {

    private final Map<String, Setting<?>> byKey = new LinkedHashMap<>();
    private final Map<String, String> keyAliases = new LinkedHashMap<>();

    // --- Geral (idioma e fidelidade de jogador) ---
    public final Setting.Text language = register(new Setting.Text("general.language", null, "language", Category.GENERAL,
            Messages.DEFAULT_LANGUAGE, () -> this.languages.get()));
    public final Setting.Bool tabList = bool("general.tab-list", "geral.tab-list", "tablist", Category.GENERAL, true);
    public final Setting.Bool joinLeaveMessages = bool("general.join-leave-messages", "geral.mensagens-entrada-saida", "joinmessages", Category.GENERAL, true);
    public final Setting.Bool deathMessages = bool("general.death-messages", "geral.mensagens-morte", "deathmessages", Category.GENERAL, true);
    public final Setting.Bool countInServerList = bool("general.count-in-server-list", "geral.contar-na-lista-de-servidores", "serverlistcount", Category.GENERAL, true);
    public final Setting.Bool replaceListCommand = bool("general.include-in-list-command", "geral.incluir-no-comando-list", "listcommand", Category.GENERAL, true);
    public final Setting.Bool respectPvp = bool("general.respect-disabled-pvp", "geral.respeitar-pvp-desligado", "respectpvp", Category.GENERAL, false);
    public final Setting.Bool skinFallbackUpload = bool("general.mineskin-fallback-skin", "geral.skin-robo-mineskin", "mineskin", Category.GENERAL, true);

    // --- Bots ---
    public final Setting.Dbl botHealth = dbl("bots.max-health", "bots.vida-maxima", "bothealth", Category.BOTS, 20.0, 1.0, 500.0, 1.0);
    public final Setting.Bool botInvincible = bool("bots.invincible", "bots.invenciveis", "botinvincible", Category.BOTS, false);
    public final Setting.Bool respawn = bool("bots.respawn", "bots.respawn", "respawn", Category.BOTS, true);
    public final Setting.Bool persistentBots = bool("bots.persistent", "bots.persistentes", "persistentbots", Category.BOTS, true);
    public final Setting.Bool dropInventory = bool("bots.drop-inventory", "bots.dropar-inventario", "dropinventory", Category.BOTS, true);
    public final Setting.Enumerated<GearPersistence> gearPersistence = choice("bots.gear-persistence", "bots.persistencia-equipamento", "gearpersistence", Category.BOTS, GearPersistence.class, GearPersistence.KEEP_ON_RESPAWN_NO_DROP);
    public final Setting.Dbl phaseMultiplier = dbl("bots.phase-duration-multiplier", "bots.multiplicador-duracao-fase", "phasemultiplier", Category.BOTS, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<BotDifficulty> defaultDifficulty = choice("bots.default-difficulty", "bots.dificuldade-padrao", "defaultdifficulty", Category.BOTS, BotDifficulty.class, BotDifficulty.TERMINATOR);
    public final Setting.Bool autoEat = bool("bots.auto-eat", "bots.comer-automaticamente", "autoeat", Category.BOTS, true);
    public final Setting.Bool autoGapple = bool("bots.auto-golden-apple", "bots.maca-dourada-automatica", "autogapple", Category.BOTS, true);
    public final Setting.Bool fireWaterEscape = bool("bots.extinguish-with-water", "bots.fugir-do-fogo-com-agua", "firewaterescape", Category.BOTS, true);
    public final Setting.Bool mobsAttackBots = bool("bots.mobs-attack-bots", "bots.mobs-atacam-bots", "mobsattackbots", Category.BOTS, true);
    public final Setting.Int aimSmoothness = integer("bots.aim-smoothness-ms", "bots.suavidade-mira-ms", "aimsmoothness", Category.BOTS, 0, 0, 200);

    // --- Combate ---
    public final Setting.Dbl targetPriority = dbl("combat.target-priority", "combate.prioridade-alvo", "targetpriority", Category.COMBAT, 2.0, -5.0, 5.0, 0.5);
    public final Setting.Enumerated<TargetMode> targetMode = choice("combat.target-mode", "combate.modo-alvo", "targetmode", Category.COMBAT, TargetMode.class, TargetMode.EQUAL_BOTS_PER_PLAYER);
    public final Setting.Bool allowTrident = bool("combat.trident", "combate.tridente", "allowtrident", Category.COMBAT, true);
    public final Setting.Bool allowCrossbow = bool("combat.crossbow", "combate.besta", "allowcrossbow", Category.COMBAT, true);
    public final Setting.Bool allowSpear = bool("combat.mounted-spear", "combate.lanca-a-cavalo", "allowspear", Category.COMBAT, true);
    public final Setting.Dbl projInaccuracy = dbl("combat.projectile-inaccuracy", "combate.imprecisao-projeteis", "projinaccuracy", Category.COMBAT, 1.0, 0.0, 2.0, 0.1);
    public final Setting.Dbl humanize = dbl("combat.humanization", "combate.humanizacao", "humanize", Category.COMBAT, 0.0, 0.0, 1.0, 0.1);
    public final Setting.Dbl meleeMissChance = dbl("combat.melee-miss-chance", "combate.chance-errar-golpe", "meleemisschance", Category.COMBAT, 0.12, 0.0, 0.6, 0.02);
    public final Setting.Dbl meleeDelayJitter = dbl("combat.melee-delay-jitter", "combate.atraso-aleatorio-golpe", "meleedelayjitter", Category.COMBAT, 4.0, 0.0, 12.0, 1.0);
    public final Setting.Dbl meleeReachVariance = dbl("combat.melee-reach-variance", "combate.variacao-alcance", "meleereachvariance", Category.COMBAT, 0.35, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeAimOffset = dbl("combat.melee-aim-offset", "combate.desvio-mira", "meleeaimoffset", Category.COMBAT, 0.25, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeSpamVariance = dbl("combat.melee-rhythm-variance", "combate.variacao-ritmo-golpes", "meleespamvariance", Category.COMBAT, 3.0, 0.0, 10.0, 0.5);

    // --- Mobilidade ---
    public final Setting.Bool allowTowering = bool("mobility.towering", "mobilidade.construir-torres", "allowtowering", Category.MOBILITY, true);
    public final Setting.Bool allowPearl = bool("mobility.ender-pearls", "mobilidade.perolas", "allowpearl", Category.MOBILITY, true);
    public final Setting.Dbl pearlCooldownMult = dbl("mobility.pearl-cooldown-multiplier", "mobilidade.multiplicador-recarga-perola", "pearlcooldownmult", Category.MOBILITY, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<PearlDimension> pearlDimension = choice("mobility.pearl-dimension", "mobilidade.dimensao-perolas", "pearldimension", Category.MOBILITY, PearlDimension.class, PearlDimension.NETHER_ONLY);
    public final Setting.Bool allowHorse = bool("mobility.horses", "mobilidade.cavalos", "allowhorse", Category.MOBILITY, true);
    public final Setting.Bool allowBoat = bool("mobility.boats", "mobilidade.barcos", "allowboat", Category.MOBILITY, true);
    public final Setting.Bool useElytra = bool("mobility.elytra", "mobilidade.elytra", "allowelytra", Category.MOBILITY, false);
    public final Setting.Bool allowRedistribution = bool("mobility.redistribute", "mobilidade.redistribuir", "allowredistribution", Category.MOBILITY, true);
    public final Setting.Dbl redistDistance = dbl("mobility.redistribute-distance", "mobilidade.distancia-redistribuicao", "redistdistance", Category.MOBILITY, 200.0, 20.0, 500.0, 10.0);
    public final Setting.Int minNetherDelay = integer("mobility.min-dimension-delay-seconds", "mobilidade.atraso-min-dimensao-segundos", "minnetherdelay", Category.MOBILITY, 30, 0, 600);
    public final Setting.Int maxNetherDelay = integer("mobility.max-dimension-delay-seconds", "mobilidade.atraso-max-dimensao-segundos", "maxnetherdelay", Category.MOBILITY, 60, 0, 600);

    // --- Pathfinding ---
    public final Setting.Enumerated<PathMode> pathMode = choice("pathfinding.mode", "pathfinding.modo", "pathmode", Category.PATHFINDING, PathMode.class, PathMode.ASTAR);
    public final Setting.Dbl greediness = dbl("pathfinding.greediness", "pathfinding.gulosidade", "pathgreediness", Category.PATHFINDING, 1.5, 1.0, 10.0, 0.1);
    public final Setting.Int nodeCap = integer("pathfinding.node-limit", "pathfinding.limite-nos", "pathnodes", Category.PATHFINDING, 2500, 50, 5000);
    public final Setting.Bool dynamicNodes = bool("pathfinding.dynamic-node-limit", "pathfinding.limite-dinamico", "pathdynamicnodes", Category.PATHFINDING, true);
    public final Setting.Bool fallbackDumb = bool("pathfinding.dumb-fallback", "pathfinding.fallback-dumb", "pathfallback", Category.PATHFINDING, true);
    public final Setting.Bool asyncPaths = bool("pathfinding.async", "pathfinding.assincrono", "pathasync", Category.PATHFINDING, true);
    public final Setting.Bool debugPaths = bool("pathfinding.debug-particles", "pathfinding.debug-particulas", "pathdebug", Category.PATHFINDING, false);

    // --- Táticas ---
    public final Setting.Bool caveAmbush = bool("tactics.cave-ambush", "taticas.emboscada-caverna", "caveambush", Category.TACTICS, true);
    public final Setting.Int caveAmbushCooldown = integer("tactics.cave-ambush-cooldown-seconds", "taticas.recarga-emboscada-caverna-segundos", "caveambushcooldown", Category.TACTICS, 1, 0, 3600);
    public final Setting.Bool autoCapacities = bool("tactics.auto-abilities", "taticas.capacidades-automaticas", "autocapacities", Category.TACTICS, false);
    public final Setting.Bool fireworkCrossbow = bool("tactics.firework-crossbow", "taticas.besta-com-fogos", "fireworkcrossbow", Category.TACTICS, false);
    public final Setting.Bool bowPressure = bool("tactics.bow-pressure", "taticas.pressao-de-arco", "bowpressure", Category.TACTICS, true);
    public final Setting.Bool silentDeaths = bool("tactics.silent-deaths", "taticas.mortes-silenciosas", "silentdeaths", Category.TACTICS, true);
    public final Setting.Bool portalEntrance = bool("tactics.portal-entrance", "taticas.entrada-por-portal", "portalentrance", Category.TACTICS, true);

    // --- Manhunt ---
    public final Setting.Enumerated<SpawnMode> spawnMode = choice("manhunt.spawn-mode", "manhunt.modo-spawn", "spawnmode", Category.MANHUNT, SpawnMode.class, SpawnMode.MANHUNT);
    public final Setting.Int startCountdown = integer("manhunt.countdown-seconds", "manhunt.contagem-segundos", "countdown", Category.MANHUNT, 20, 0, 600);
    public final Setting.Bool countdownOnLeave = bool("manhunt.countdown-on-ring-leave", "manhunt.contagem-ao-sair-da-roda", "countdownonleave", Category.MANHUNT, true);
    public final Setting.Bool countdownInvulnerable = bool("manhunt.invulnerable-during-countdown", "manhunt.invulneravel-na-contagem", "countdowninvulnerable", Category.MANHUNT, true);
    public final Setting.Dbl ringRadius = dbl("manhunt.ring-radius", "manhunt.raio-roda", "ringradius", Category.MANHUNT, 0.0, 0.0, 64.0, 1.0);
    public final Setting.Enumerated<RingCenter> ringCenter = choice("manhunt.ring-center", "manhunt.centro-roda", "ringcenter", Category.MANHUNT, RingCenter.class, RingCenter.AUTO);
    public final Setting.Enumerated<LateJoin> lateJoin = choice("manhunt.late-join", "manhunt.quem-entra-no-meio", "latejoin", Category.MANHUNT, LateJoin.class, LateJoin.PROTECTED);
    public final Setting.Enumerated<PlayerListMode> playerListMode = choice("manhunt.player-list-mode", "manhunt.lista-modo", "playerlistmode", Category.MANHUNT, PlayerListMode.class, PlayerListMode.OFF);

    // --- Aparência ---
    public final Setting.Bool glowHunters = bool("appearance.glow-hunters", "aparencia.brilho-cacadores", "glowhunters", Category.APPEARANCE, false);
    public final Setting.Enumerated<GlowColor> glowHuntersColor = choice("appearance.glow-hunters-color", "aparencia.cor-brilho-cacadores", "glowhunterscolor", Category.APPEARANCE, GlowColor.class, GlowColor.RED);
    public final Setting.Bool glowEscapers = bool("appearance.glow-escapers", "aparencia.brilho-fugitivos", "glowescapers", Category.APPEARANCE, false);
    public final Setting.Enumerated<GlowColor> glowEscapersColor = choice("appearance.glow-escapers-color", "aparencia.cor-brilho-fugitivos", "glowescaperscolor", Category.APPEARANCE, GlowColor.class, GlowColor.AQUA);
    public final Setting.Bool showNameTag = bool("appearance.show-name-tag", "aparencia.mostrar-nome", "nametag", Category.APPEARANCE, true);

    /** Opções que já existiram e saem do config.yml ao salvar. */
    private static final List<String> REMOVED_PATHS = List.of("manhunt.liberar-ao-sair-da-roda");

    /** Seções do config.yml com chaves em português (até a versão 1.5): sinal de que precisa migrar. */
    private static final List<String> LEGACY_SECTIONS = List.of("geral", "combate", "mobilidade", "taticas", "aparencia");
    private static final String MOBS_PATH = "bots.mobs-that-ignore-bots";
    private static final String LEGACY_MOBS_PATH = "bots.mobs-que-nao-atacam-bots";
    private static final String PLAYER_LIST_PATH = "manhunt.player-list";
    private static final String LEGACY_PLAYER_LIST_PATH = "manhunt.lista-jogadores";
    private static final String RING_POINT_PATH = "manhunt.ring-point";
    private static final String LEGACY_RING_POINT_PATH = "manhunt.ponto-roda";

    /** Idiomas que aparecem no Tab de {@code /manhunt settings language} (os arquivos da pasta lang/). */
    private volatile Supplier<List<String>> languages = () -> Messages.BUNDLED_LANGUAGES;

    private volatile Set<EntityType> excludedMobs = Set.of();
    private volatile List<String> playerList = List.of();
    private volatile RingPoint ringPoint;

    /** Ponto da roda no modo manual (ring-center: manual). */
    public record RingPoint(String world, double x, double y, double z) {
    }

    public Settings() {
        // nomes antigos (do mod e das versões anteriores): continuam valendo, mas não aparecem no Tab
        alias("mobtarget", "mobsattackbots");
        alias("smoothness", "aimsmoothness");
        alias("projaccuracy", "projinaccuracy");
        alias("botuseelytra", "allowelytra");
        alias("min_sec_range_nether_entering", "minnetherdelay");
        alias("max_sec_range_nether_entering", "maxnetherdelay");
        alias("greediness", "pathgreediness");
        alias("nodes", "pathnodes");
        alias("dynamic", "pathdynamicnodes");
        alias("fallback", "pathfallback");
        alias("async", "pathasync");
        alias("debugastar", "pathdebug");
        alias("starttime", "countdown");
        alias("listmode", "playerlistmode");
        alias("glowing", "glowhunters");
        alias("glowcolor", "glowhunterscolor");
        alias("glowingescape", "glowescapers");
        alias("glowcolorescape", "glowescaperscolor");
        alias("meleerangejitter", "meleereachvariance");
        alias("allowhorseriding", "allowhorse");
        alias("allowboatriding", "allowboat");
        alias("autogoldenapple", "autogapple");
        alias("dropinventoryondeath", "dropinventory");
        alias("allowenderpearl", "allowpearl");
    }

    private Setting.Bool bool(String path, String legacy, String key, Category c, boolean def) {
        return register(new Setting.Bool(path, legacy, key, c, def));
    }

    private Setting.Int integer(String path, String legacy, String key, Category c, int def, int min, int max) {
        return register(new Setting.Int(path, legacy, key, c, def, min, max));
    }

    private Setting.Dbl dbl(String path, String legacy, String key, Category c, double def, double min, double max, double step) {
        return register(new Setting.Dbl(path, legacy, key, c, def, min, max, step));
    }

    private <E extends Enum<E> & Setting.Choice> Setting.Enumerated<E> choice(String path, String legacy, String key, Category c, Class<E> type, E def) {
        return register(new Setting.Enumerated<>(path, legacy, key, c, type, def));
    }

    private <S extends Setting<?>> S register(S s) {
        byKey.put(s.key(), s);
        return s;
    }

    private void alias(String alias, String key) {
        keyAliases.put(alias, key);
    }

    public Setting<?> byKey(String key) {
        String k = key.toLowerCase(Locale.ROOT).trim();
        Setting<?> s = byKey.get(k);
        if (s == null && keyAliases.containsKey(k)) {
            s = byKey.get(keyAliases.get(k));
        }
        return s;
    }

    public Collection<Setting<?>> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public List<Setting<?>> inCategory(Category category) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : byKey.values()) {
            if (s.category() == category) {
                out.add(s);
            }
        }
        return out;
    }

    public void setLanguages(Supplier<List<String>> languages) {
        this.languages = languages;
    }

    public Set<EntityType> excludedMobs() {
        return excludedMobs;
    }

    public RingPoint ringPoint() {
        return ringPoint;
    }

    public void setRingPoint(RingPoint point) {
        ringPoint = point;
    }

    /** Nomes da lista de jogadores (permitidos ou bloqueados, conforme a opção player-list-mode). */
    public List<String> playerList() {
        return playerList;
    }

    public boolean isListed(String name) {
        for (String n : playerList) {
            if (n.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** @return false se o nome já estava na lista */
    public synchronized boolean addListedPlayer(String name) {
        if (isListed(name)) {
            return false;
        }
        List<String> next = new ArrayList<>(playerList);
        next.add(name);
        playerList = List.copyOf(next);
        return true;
    }

    /** @return false se o nome não estava na lista */
    public synchronized boolean removeListedPlayer(String name) {
        List<String> next = new ArrayList<>(playerList);
        if (!next.removeIf(n -> n.equalsIgnoreCase(name))) {
            return false;
        }
        playerList = List.copyOf(next);
        return true;
    }

    /** A lista deixa este jogador ser caçado e disparar a roda? */
    public boolean listAllows(String name) {
        return switch (playerListMode.get()) {
            case OFF -> true;
            case ALLOWLIST -> isListed(name);
            case BLOCKLIST -> !isListed(name);
        };
    }

    /** O config.yml ainda tem as chaves em português das versões até a 1.5? */
    public static boolean isLegacy(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String section : LEGACY_SECTIONS) {
            if (cfg.isConfigurationSection(section)) {
                return true;
            }
        }
        return false;
    }

    public void load(File file, Logger logger) {
        load(YamlConfiguration.loadConfiguration(file), false, logger);
    }

    /**
     * Lê um config.yml com as chaves em português (versões até a 1.5). O arquivo novo é gravado depois
     * com {@link #save}; o idioma fica em pt-br, que era o único que existia.
     */
    public void loadLegacy(File file, Logger logger) {
        load(YamlConfiguration.loadConfiguration(file), true, logger);
        language.set("pt-br");
    }

    private void load(YamlConfiguration cfg, boolean legacy, Logger logger) {
        for (Setting<?> s : byKey.values()) {
            if (legacy) {
                s.load(cfg, s.legacyPath());
            } else {
                s.load(cfg);
            }
        }
        Set<EntityType> excluded = new HashSet<>();
        for (String name : cfg.getStringList(legacy ? LEGACY_MOBS_PATH : MOBS_PATH)) {
            try {
                excluded.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown mob type in " + MOBS_PATH + ": " + name);
            }
        }
        excludedMobs = Set.copyOf(excluded);
        List<String> names = new ArrayList<>();
        for (String name : cfg.getStringList(legacy ? LEGACY_PLAYER_LIST_PATH : PLAYER_LIST_PATH)) {
            String n = name.trim();
            if (!n.isEmpty() && names.stream().noneMatch(n::equalsIgnoreCase)) {
                names.add(n);
            }
        }
        playerList = List.copyOf(names);
        ConfigurationSection point = cfg.getConfigurationSection(legacy ? LEGACY_RING_POINT_PATH : RING_POINT_PATH);
        String worldKey = legacy ? "mundo" : "world";
        ringPoint = point == null || !point.isString(worldKey) ? null
                : new RingPoint(point.getString(worldKey), point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
        if (minNetherDelay.value() > maxNetherDelay.value()) {
            maxNetherDelay.set(minNetherDelay.value());
        }
    }

    /** Grava os valores atuais preservando comentários e chaves extras do arquivo. */
    public void save(File file, Logger logger) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.options().parseComments(true);
        for (Setting<?> s : byKey.values()) {
            s.save(cfg);
        }
        for (String path : REMOVED_PATHS) {
            cfg.set(path, null);
        }
        List<String> mobs = new ArrayList<>();
        for (EntityType type : excludedMobs) {
            mobs.add(type.name());
        }
        mobs.sort(null);
        cfg.set(MOBS_PATH, mobs);
        cfg.set(PLAYER_LIST_PATH, new ArrayList<>(playerList));
        RingPoint point = ringPoint;
        if (point == null) {
            cfg.set(RING_POINT_PATH, null);
        } else {
            cfg.set(RING_POINT_PATH + ".world", point.world());
            cfg.set(RING_POINT_PATH + ".x", point.x());
            cfg.set(RING_POINT_PATH + ".y", point.y());
            cfg.set(RING_POINT_PATH + ".z", point.z());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            logger.warning("Could not save config.yml: " + e.getMessage());
        }
    }
}
