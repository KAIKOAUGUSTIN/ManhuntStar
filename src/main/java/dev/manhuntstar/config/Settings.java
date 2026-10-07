package dev.manhuntstar.config;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.GearPersistence;
import dev.manhuntstar.bot.Modes.GlowColor;
import dev.manhuntstar.bot.Modes.PathMode;
import dev.manhuntstar.bot.Modes.PearlDimension;
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
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

/**
 * Todas as opções do plugin. As chaves de comando seguem as do mod original
 * ({@code /bot settings <chave>} e {@code /bot gameplay <chave>}); o config.yml usa nomes em português.
 */
public final class Settings {

    private final Map<String, Setting<?>> byKey = new LinkedHashMap<>();
    private final Map<String, String> keyAliases = new LinkedHashMap<>();

    // --- Geral (fidelidade de jogador) ---
    public final Setting.Bool tabList = bool("geral.tab-list", "tablist", Category.GERAL, true);
    public final Setting.Bool joinLeaveMessages = bool("geral.mensagens-entrada-saida", "joinmessages", Category.GERAL, true);
    public final Setting.Bool deathMessages = bool("geral.mensagens-morte", "deathmessages", Category.GERAL, true);
    public final Setting.Bool countInServerList = bool("geral.contar-na-lista-de-servidores", "serverlistcount", Category.GERAL, true);
    public final Setting.Bool replaceListCommand = bool("geral.incluir-no-comando-list", "listcommand", Category.GERAL, true);
    public final Setting.Bool respectPvp = bool("geral.respeitar-pvp-desligado", "respectpvp", Category.GERAL, false);
    public final Setting.Bool skinFallbackUpload = bool("geral.skin-robo-mineskin", "mineskin", Category.GERAL, true);

    // --- Bots ---
    public final Setting.Dbl botHealth = dbl("bots.vida-maxima", "bothealth", Category.BOTS, 20.0, 1.0, 500.0, 1.0);
    public final Setting.Bool botInvincible = bool("bots.invenciveis", "botinvincible", Category.BOTS, false);
    public final Setting.Bool respawn = bool("bots.respawn", "respawn", Category.BOTS, true);
    public final Setting.Bool persistentBots = bool("bots.persistentes", "persistentbots", Category.BOTS, true);
    public final Setting.Bool dropInventory = bool("bots.dropar-inventario", "dropinventory", Category.BOTS, true);
    public final Setting.Enumerated<GearPersistence> gearPersistence = choice("bots.persistencia-equipamento", "gearpersistence", Category.BOTS, GearPersistence.class, GearPersistence.KEEP_ON_RESPAWN_NO_DROP);
    public final Setting.Dbl phaseMultiplier = dbl("bots.multiplicador-duracao-fase", "phasemultiplier", Category.BOTS, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<BotDifficulty> defaultDifficulty = choice("bots.dificuldade-padrao", "defaultdifficulty", Category.BOTS, BotDifficulty.class, BotDifficulty.TERMINATOR);
    public final Setting.Bool autoEat = bool("bots.comer-automaticamente", "autoeat", Category.BOTS, true);
    public final Setting.Bool autoGapple = bool("bots.maca-dourada-automatica", "autogapple", Category.BOTS, true);
    public final Setting.Bool fireWaterEscape = bool("bots.fugir-do-fogo-com-agua", "firewaterescape", Category.BOTS, true);
    public final Setting.Bool mobsAttackBots = bool("bots.mobs-atacam-bots", "mobtarget", Category.BOTS, true);
    public final Setting.Int aimSmoothness = integer("bots.suavidade-mira-ms", "smoothness", Category.BOTS, 0, 0, 200);

    // --- Combate ---
    public final Setting.Dbl targetPriority = dbl("combate.prioridade-alvo", "targetpriority", Category.COMBATE, 2.0, -5.0, 5.0, 0.5);
    public final Setting.Enumerated<TargetMode> targetMode = choice("combate.modo-alvo", "targetmode", Category.COMBATE, TargetMode.class, TargetMode.EQUAL_BOTS_PER_PLAYER);
    public final Setting.Bool allowTrident = bool("combate.tridente", "allowtrident", Category.COMBATE, true);
    public final Setting.Bool allowCrossbow = bool("combate.besta", "allowcrossbow", Category.COMBATE, true);
    public final Setting.Dbl projInaccuracy = dbl("combate.imprecisao-projeteis", "projaccuracy", Category.COMBATE, 1.0, 0.0, 2.0, 0.1);
    public final Setting.Dbl humanize = dbl("combate.humanizacao", "humanize", Category.COMBATE, 0.0, 0.0, 1.0, 0.1);
    public final Setting.Dbl meleeMissChance = dbl("combate.chance-errar-golpe", "meleemisschance", Category.COMBATE, 0.12, 0.0, 0.6, 0.02);
    public final Setting.Dbl meleeDelayJitter = dbl("combate.atraso-aleatorio-golpe", "meleedelayjitter", Category.COMBATE, 4.0, 0.0, 12.0, 1.0);
    public final Setting.Dbl meleeReachVariance = dbl("combate.variacao-alcance", "meleereachvariance", Category.COMBATE, 0.35, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeAimOffset = dbl("combate.desvio-mira", "meleeaimoffset", Category.COMBATE, 0.25, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeSpamVariance = dbl("combate.variacao-ritmo-golpes", "meleespamvariance", Category.COMBATE, 3.0, 0.0, 10.0, 0.5);

    // --- Mobilidade ---
    public final Setting.Bool allowTowering = bool("mobilidade.construir-torres", "allowtowering", Category.MOBILIDADE, true);
    public final Setting.Bool allowPearl = bool("mobilidade.perolas", "allowpearl", Category.MOBILIDADE, true);
    public final Setting.Dbl pearlCooldownMult = dbl("mobilidade.multiplicador-recarga-perola", "pearlcooldownmult", Category.MOBILIDADE, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<PearlDimension> pearlDimension = choice("mobilidade.dimensao-perolas", "pearldimension", Category.MOBILIDADE, PearlDimension.class, PearlDimension.NETHER_ONLY);
    public final Setting.Bool allowHorse = bool("mobilidade.cavalos", "allowhorse", Category.MOBILIDADE, true);
    public final Setting.Bool allowBoat = bool("mobilidade.barcos", "allowboat", Category.MOBILIDADE, true);
    public final Setting.Bool useElytra = bool("mobilidade.elytra", "botuseelytra", Category.MOBILIDADE, false);
    public final Setting.Bool allowRedistribution = bool("mobilidade.redistribuir", "allowredistribution", Category.MOBILIDADE, true);
    public final Setting.Dbl redistDistance = dbl("mobilidade.distancia-redistribuicao", "redistdistance", Category.MOBILIDADE, 200.0, 20.0, 500.0, 10.0);
    public final Setting.Int minNetherDelay = integer("mobilidade.atraso-min-dimensao-segundos", "min_sec_range_nether_entering", Category.MOBILIDADE, 30, 0, 600);
    public final Setting.Int maxNetherDelay = integer("mobilidade.atraso-max-dimensao-segundos", "max_sec_range_nether_entering", Category.MOBILIDADE, 60, 0, 600);

    // --- Pathfinding ---
    public final Setting.Enumerated<PathMode> pathMode = choice("pathfinding.modo", "pathmode", Category.PATHFINDING, PathMode.class, PathMode.ASTAR);
    public final Setting.Dbl greediness = dbl("pathfinding.gulosidade", "greediness", Category.PATHFINDING, 1.5, 1.0, 10.0, 0.1);
    public final Setting.Int nodeCap = integer("pathfinding.limite-nos", "nodes", Category.PATHFINDING, 2500, 50, 5000);
    public final Setting.Bool dynamicNodes = bool("pathfinding.limite-dinamico", "dynamic", Category.PATHFINDING, true);
    public final Setting.Bool fallbackDumb = bool("pathfinding.fallback-dumb", "fallback", Category.PATHFINDING, true);
    public final Setting.Bool asyncPaths = bool("pathfinding.assincrono", "async", Category.PATHFINDING, true);
    public final Setting.Bool debugPaths = bool("pathfinding.debug-particulas", "debugastar", Category.PATHFINDING, false);

    // --- Táticas ---
    public final Setting.Bool caveAmbush = bool("taticas.emboscada-caverna", "caveambush", Category.TATICAS, true);
    public final Setting.Int caveAmbushCooldown = integer("taticas.recarga-emboscada-caverna-segundos", "caveambushcooldown", Category.TATICAS, 1, 0, 3600);
    public final Setting.Bool autoCapacities = bool("taticas.capacidades-automaticas", "autocapacities", Category.TATICAS, false);
    public final Setting.Bool fireworkCrossbow = bool("taticas.besta-com-fogos", "fireworkcrossbow", Category.TATICAS, false);
    public final Setting.Bool bowPressure = bool("taticas.pressao-de-arco", "bowpressure", Category.TATICAS, true);
    public final Setting.Bool silentDeaths = bool("taticas.mortes-silenciosas", "silentdeaths", Category.TATICAS, true);
    public final Setting.Bool portalEntrance = bool("taticas.entrada-por-portal", "portalentrance", Category.TATICAS, true);

    // --- Manhunt ---
    public final Setting.Enumerated<SpawnMode> spawnMode = choice("manhunt.modo-spawn", "spawnmode", Category.MANHUNT, SpawnMode.class, SpawnMode.MANHUNT);
    public final Setting.Int startCountdown = integer("manhunt.contagem-segundos", "starttime", Category.MANHUNT, 20, 0, 600);
    public final Setting.Bool countdownInvulnerable = bool("manhunt.invulneravel-na-contagem", "countdowninvulnerable", Category.MANHUNT, true);
    public final Setting.Bool releaseOnLeaveCircle = bool("manhunt.liberar-ao-sair-da-roda", "releaseonleave", Category.MANHUNT, false);

    // --- Aparência ---
    public final Setting.Bool glowHunters = bool("aparencia.brilho-cacadores", "glowing", Category.APARENCIA, false);
    public final Setting.Enumerated<GlowColor> glowHuntersColor = choice("aparencia.cor-brilho-cacadores", "glowcolor", Category.APARENCIA, GlowColor.class, GlowColor.RED);
    public final Setting.Bool glowEscapers = bool("aparencia.brilho-fugitivos", "glowingescape", Category.APARENCIA, false);
    public final Setting.Enumerated<GlowColor> glowEscapersColor = choice("aparencia.cor-brilho-fugitivos", "glowcolorescape", Category.APARENCIA, GlowColor.class, GlowColor.AQUA);
    public final Setting.Bool showNameTag = bool("aparencia.mostrar-nome", "nametag", Category.APARENCIA, true);

    private volatile Set<EntityType> excludedMobs = Set.of();

    public Settings() {
        alias("astar nodes", "nodes");
        alias("astar dynamic", "dynamic");
        alias("astar fallback", "fallback");
        alias("astar async", "async");
        alias("meleerangejitter", "meleereachvariance");
        alias("allowhorseriding", "allowhorse");
        alias("allowboatriding", "allowboat");
        alias("autogoldenapple", "autogapple");
        alias("dropinventoryondeath", "dropinventory");
        alias("allowenderpearl", "allowpearl");
    }

    private Setting.Bool bool(String path, String key, Category c, boolean def) {
        return register(new Setting.Bool(path, key, c, def));
    }

    private Setting.Int integer(String path, String key, Category c, int def, int min, int max) {
        return register(new Setting.Int(path, key, c, def, min, max));
    }

    private Setting.Dbl dbl(String path, String key, Category c, double def, double min, double max, double step) {
        return register(new Setting.Dbl(path, key, c, def, min, max, step));
    }

    private <E extends Enum<E> & Setting.Choice> Setting.Enumerated<E> choice(String path, String key, Category c, Class<E> type, E def) {
        return register(new Setting.Enumerated<>(path, key, c, type, def));
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

    public Set<EntityType> excludedMobs() {
        return excludedMobs;
    }

    public void load(File file, Logger logger) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (Setting<?> s : byKey.values()) {
            s.load(cfg);
        }
        Set<EntityType> excluded = new HashSet<>();
        for (String name : cfg.getStringList("bots.mobs-que-nao-atacam-bots")) {
            try {
                excluded.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("Tipo de mob desconhecido em bots.mobs-que-nao-atacam-bots: " + name);
            }
        }
        excludedMobs = Set.copyOf(excluded);
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
        try {
            cfg.save(file);
        } catch (IOException e) {
            logger.warning("Não foi possível salvar o config.yml: " + e.getMessage());
        }
    }
}
