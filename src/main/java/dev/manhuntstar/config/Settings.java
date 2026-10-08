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
import java.io.Reader;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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
    public final Setting.Bool tabList = bool("geral.tab-list", "tablist", "Bots na lista do TAB", Category.GERAL, true);
    public final Setting.Bool joinLeaveMessages = bool("geral.mensagens-entrada-saida", "joinmessages", "Mensagens de entrada e saída", Category.GERAL, true);
    public final Setting.Bool deathMessages = bool("geral.mensagens-morte", "deathmessages", "Mensagens de morte", Category.GERAL, true);
    public final Setting.Bool countInServerList = bool("geral.contar-na-lista-de-servidores", "serverlistcount", "Contar na lista de servidores", Category.GERAL, true);
    public final Setting.Bool replaceListCommand = bool("geral.incluir-no-comando-list", "listcommand", "Bots no /list", Category.GERAL, true);
    public final Setting.Bool respectPvp = bool("geral.respeitar-pvp-desligado", "respectpvp", "Respeitar PvP desligado", Category.GERAL, false);
    public final Setting.Bool skinFallbackUpload = bool("geral.skin-robo-mineskin", "mineskin", "Skin reserva pelo MineSkin", Category.GERAL, true);

    // --- Bots ---
    public final Setting.Dbl botHealth = dbl("bots.vida-maxima", "bothealth", "Vida máxima", Category.BOTS, 20.0, 1.0, 500.0, 1.0);
    public final Setting.Bool botInvincible = bool("bots.invenciveis", "botinvincible", "Bots invencíveis", Category.BOTS, false);
    public final Setting.Bool respawn = bool("bots.respawn", "respawn", "Renascer depois de morrer", Category.BOTS, true);
    public final Setting.Bool persistentBots = bool("bots.persistentes", "persistentbots", "Manter bots ao reiniciar", Category.BOTS, true);
    public final Setting.Bool dropInventory = bool("bots.dropar-inventario", "dropinventory", "Derrubar inventário ao morrer", Category.BOTS, true);
    public final Setting.Enumerated<GearPersistence> gearPersistence = choice("bots.persistencia-equipamento", "gearpersistence", "Equipamento ao morrer", Category.BOTS, GearPersistence.class, GearPersistence.KEEP_ON_RESPAWN_NO_DROP);
    public final Setting.Dbl phaseMultiplier = dbl("bots.multiplicador-duracao-fase", "phasemultiplier", "Duração das fases", Category.BOTS, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<BotDifficulty> defaultDifficulty = choice("bots.dificuldade-padrao", "defaultdifficulty", "Dificuldade padrão", Category.BOTS, BotDifficulty.class, BotDifficulty.TERMINATOR);
    public final Setting.Bool autoEat = bool("bots.comer-automaticamente", "autoeat", "Comer sozinho", Category.BOTS, true);
    public final Setting.Bool autoGapple = bool("bots.maca-dourada-automatica", "autogapple", "Maçã dourada em combate", Category.BOTS, true);
    public final Setting.Bool fireWaterEscape = bool("bots.fugir-do-fogo-com-agua", "firewaterescape", "Apagar fogo com água", Category.BOTS, true);
    public final Setting.Bool mobsAttackBots = bool("bots.mobs-atacam-bots", "mobtarget", "Mobs atacam os bots", Category.BOTS, true);
    public final Setting.Int aimSmoothness = integer("bots.suavidade-mira-ms", "smoothness", "Suavidade da mira (ms)", Category.BOTS, 0, 0, 200);

    // --- Combate ---
    public final Setting.Dbl targetPriority = dbl("combate.prioridade-alvo", "targetpriority", "Prioridade jogador × mob", Category.COMBATE, 2.0, -5.0, 5.0, 0.5);
    public final Setting.Enumerated<TargetMode> targetMode = choice("combate.modo-alvo", "targetmode", "Divisão dos alvos", Category.COMBATE, TargetMode.class, TargetMode.EQUAL_BOTS_PER_PLAYER);
    public final Setting.Bool allowTrident = bool("combate.tridente", "allowtrident", "Tridente", Category.COMBATE, true);
    public final Setting.Bool allowCrossbow = bool("combate.besta", "allowcrossbow", "Besta", Category.COMBATE, true);
    public final Setting.Bool allowSpear = bool("combate.lanca-a-cavalo", "allowspear", "Lança a cavalo", Category.COMBATE, true);
    public final Setting.Dbl projInaccuracy = dbl("combate.imprecisao-projeteis", "projaccuracy", "Imprecisão de flechas e tridente", Category.COMBATE, 1.0, 0.0, 2.0, 0.1);
    public final Setting.Dbl humanize = dbl("combate.humanizacao", "humanize", "Humanização", Category.COMBATE, 0.0, 0.0, 1.0, 0.1);
    public final Setting.Dbl meleeMissChance = dbl("combate.chance-errar-golpe", "meleemisschance", "Chance de errar o golpe", Category.COMBATE, 0.12, 0.0, 0.6, 0.02);
    public final Setting.Dbl meleeDelayJitter = dbl("combate.atraso-aleatorio-golpe", "meleedelayjitter", "Atraso aleatório entre golpes", Category.COMBATE, 4.0, 0.0, 12.0, 1.0);
    public final Setting.Dbl meleeReachVariance = dbl("combate.variacao-alcance", "meleereachvariance", "Variação do alcance", Category.COMBATE, 0.35, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeAimOffset = dbl("combate.desvio-mira", "meleeaimoffset", "Desvio da mira no golpe", Category.COMBATE, 0.25, 0.0, 1.5, 0.05);
    public final Setting.Dbl meleeSpamVariance = dbl("combate.variacao-ritmo-golpes", "meleespamvariance", "Variação do ritmo de golpes", Category.COMBATE, 3.0, 0.0, 10.0, 0.5);

    // --- Mobilidade ---
    public final Setting.Bool allowTowering = bool("mobilidade.construir-torres", "allowtowering", "Torres e pontes", Category.MOBILIDADE, true);
    public final Setting.Bool allowPearl = bool("mobilidade.perolas", "allowpearl", "Pérolas do End", Category.MOBILIDADE, true);
    public final Setting.Dbl pearlCooldownMult = dbl("mobilidade.multiplicador-recarga-perola", "pearlcooldownmult", "Recarga das pérolas", Category.MOBILIDADE, 1.0, 0.1, 5.0, 0.1);
    public final Setting.Enumerated<PearlDimension> pearlDimension = choice("mobilidade.dimensao-perolas", "pearldimension", "Onde usar pérolas", Category.MOBILIDADE, PearlDimension.class, PearlDimension.NETHER_ONLY);
    public final Setting.Bool allowHorse = bool("mobilidade.cavalos", "allowhorse", "Cavalos", Category.MOBILIDADE, true);
    public final Setting.Bool allowBoat = bool("mobilidade.barcos", "allowboat", "Barcos", Category.MOBILIDADE, true);
    public final Setting.Bool useElytra = bool("mobilidade.elytra", "botuseelytra", "Elytra", Category.MOBILIDADE, false);
    public final Setting.Bool allowRedistribution = bool("mobilidade.redistribuir", "allowredistribution", "Trazer bots que ficaram longe", Category.MOBILIDADE, true);
    public final Setting.Dbl redistDistance = dbl("mobilidade.distancia-redistribuicao", "redistdistance", "Distância para trazer bots", Category.MOBILIDADE, 200.0, 20.0, 500.0, 10.0);
    public final Setting.Int minNetherDelay = integer("mobilidade.atraso-min-dimensao-segundos", "min_sec_range_nether_entering", "Atraso mínimo para trocar de dimensão (s)", Category.MOBILIDADE, 30, 0, 600);
    public final Setting.Int maxNetherDelay = integer("mobilidade.atraso-max-dimensao-segundos", "max_sec_range_nether_entering", "Atraso máximo para trocar de dimensão (s)", Category.MOBILIDADE, 60, 0, 600);

    // --- Pathfinding ---
    public final Setting.Enumerated<PathMode> pathMode = choice("pathfinding.modo", "pathmode", "Planejador de caminho", Category.PATHFINDING, PathMode.class, PathMode.ASTAR);
    public final Setting.Dbl greediness = dbl("pathfinding.gulosidade", "greediness", "Gulosidade do A*", Category.PATHFINDING, 1.5, 1.0, 10.0, 0.1);
    public final Setting.Int nodeCap = integer("pathfinding.limite-nos", "nodes", "Limite de nós do A*", Category.PATHFINDING, 2500, 50, 5000);
    public final Setting.Bool dynamicNodes = bool("pathfinding.limite-dinamico", "dynamic", "Limite de nós automático", Category.PATHFINDING, true);
    public final Setting.Bool fallbackDumb = bool("pathfinding.fallback-dumb", "fallback", "Andar direto sem caminho", Category.PATHFINDING, true);
    public final Setting.Bool asyncPaths = bool("pathfinding.assincrono", "async", "Calcular caminhos em paralelo", Category.PATHFINDING, true);
    public final Setting.Bool debugPaths = bool("pathfinding.debug-particulas", "debugastar", "Caminho em partículas", Category.PATHFINDING, false);

    // --- Táticas ---
    public final Setting.Bool caveAmbush = bool("taticas.emboscada-caverna", "caveambush", "Emboscada em caverna", Category.TATICAS, true);
    public final Setting.Int caveAmbushCooldown = integer("taticas.recarga-emboscada-caverna-segundos", "caveambushcooldown", "Intervalo entre emboscadas (s)", Category.TATICAS, 1, 0, 3600);
    public final Setting.Bool autoCapacities = bool("taticas.capacidades-automaticas", "autocapacities", "TNT e fogos automáticos", Category.TATICAS, false);
    public final Setting.Bool fireworkCrossbow = bool("taticas.besta-com-fogos", "fireworkcrossbow", "Besta com fogos", Category.TATICAS, false);
    public final Setting.Bool bowPressure = bool("taticas.pressao-de-arco", "bowpressure", "Rajada de arco", Category.TATICAS, true);
    public final Setting.Bool silentDeaths = bool("taticas.mortes-silenciosas", "silentdeaths", "Mortes silenciosas", Category.TATICAS, true);
    public final Setting.Bool portalEntrance = bool("taticas.entrada-por-portal", "portalentrance", "Chegar por portal", Category.TATICAS, true);

    // --- Manhunt ---
    public final Setting.Enumerated<SpawnMode> spawnMode = choice("manhunt.modo-spawn", "spawnmode", "Modo de spawn", Category.MANHUNT, SpawnMode.class, SpawnMode.MANHUNT);
    public final Setting.Int startCountdown = integer("manhunt.contagem-segundos", "starttime", "Contagem antes da caçada (s)", Category.MANHUNT, 20, 0, 600);
    public final Setting.Bool countdownOnLeave = bool("manhunt.contagem-ao-sair-da-roda", "countdownonleave", "Contar só depois de sair da roda", Category.MANHUNT, true);
    public final Setting.Bool countdownInvulnerable = bool("manhunt.invulneravel-na-contagem", "countdowninvulnerable", "Bots invulneráveis na roda", Category.MANHUNT, true);
    public final Setting.Dbl ringRadius = dbl("manhunt.raio-roda", "ringradius", "Raio da roda (0 = automático)", Category.MANHUNT, 0.0, 0.0, 64.0, 1.0);
    public final Setting.Enumerated<RingCenter> ringCenter = choice("manhunt.centro-roda", "ringcenter", "Centro da roda", Category.MANHUNT, RingCenter.class, RingCenter.AUTOMATICO);
    public final Setting.Enumerated<LateJoin> lateJoin = choice("manhunt.quem-entra-no-meio", "latejoin", "Quem entra no meio da caçada", Category.MANHUNT, LateJoin.class, LateJoin.PROTEGIDO);
    public final Setting.Enumerated<PlayerListMode> playerListMode = choice("manhunt.lista-modo", "listmode", "Lista de jogadores", Category.MANHUNT, PlayerListMode.class, PlayerListMode.DESLIGADA);

    // --- Aparência ---
    public final Setting.Bool glowHunters = bool("aparencia.brilho-cacadores", "glowing", "Brilho nos caçadores", Category.APARENCIA, false);
    public final Setting.Enumerated<GlowColor> glowHuntersColor = choice("aparencia.cor-brilho-cacadores", "glowcolor", "Cor do brilho dos caçadores", Category.APARENCIA, GlowColor.class, GlowColor.RED);
    public final Setting.Bool glowEscapers = bool("aparencia.brilho-fugitivos", "glowingescape", "Brilho nos fugitivos", Category.APARENCIA, false);
    public final Setting.Enumerated<GlowColor> glowEscapersColor = choice("aparencia.cor-brilho-fugitivos", "glowcolorescape", "Cor do brilho dos fugitivos", Category.APARENCIA, GlowColor.class, GlowColor.AQUA);
    public final Setting.Bool showNameTag = bool("aparencia.mostrar-nome", "nametag", "Nome em cima do bot", Category.APARENCIA, true);

    /** Opções que já existiram e saem do config.yml ao salvar. */
    private static final List<String> REMOVED_PATHS = List.of("manhunt.liberar-ao-sair-da-roda");

    private volatile Set<EntityType> excludedMobs = Set.of();
    private volatile List<String> playerList = List.of();
    private volatile RingPoint ringPoint;

    /** Ponto da roda no modo manual (centro-roda: manual). */
    public record RingPoint(String world, double x, double y, double z) {
    }

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

    private Setting.Bool bool(String path, String key, String label, Category c, boolean def) {
        return register(new Setting.Bool(path, key, label, c, def));
    }

    private Setting.Int integer(String path, String key, String label, Category c, int def, int min, int max) {
        return register(new Setting.Int(path, key, label, c, def, min, max));
    }

    private Setting.Dbl dbl(String path, String key, String label, Category c, double def, double min, double max, double step) {
        return register(new Setting.Dbl(path, key, label, c, def, min, max, step));
    }

    private <E extends Enum<E> & Setting.Choice> Setting.Enumerated<E> choice(String path, String key, String label, Category c, Class<E> type, E def) {
        return register(new Setting.Enumerated<>(path, key, label, c, type, def));
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

    public RingPoint ringPoint() {
        return ringPoint;
    }

    public void setRingPoint(RingPoint point) {
        ringPoint = point;
    }

    /** Nomes da lista de jogadores (permitidos ou bloqueados, conforme a opção lista-modo). */
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
            case DESLIGADA -> true;
            case PERMITIDOS -> isListed(name);
            case BLOQUEADOS -> !isListed(name);
        };
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
        List<String> names = new ArrayList<>();
        for (String name : cfg.getStringList("manhunt.lista-jogadores")) {
            String n = name.trim();
            if (!n.isEmpty() && names.stream().noneMatch(n::equalsIgnoreCase)) {
                names.add(n);
            }
        }
        playerList = List.copyOf(names);
        ConfigurationSection point = cfg.getConfigurationSection("manhunt.ponto-roda");
        ringPoint = point == null || !point.isString("mundo") ? null
                : new RingPoint(point.getString("mundo"), point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
        if (minNetherDelay.value() > maxNetherDelay.value()) {
            maxNetherDelay.set(minNetherDelay.value());
        }
    }

    /**
     * Descrição de cada opção, tirada dos comentários de um config.yml (o que vem dentro do plugin),
     * sem as chaves de comando entre colchetes. Chave do mapa: caminho da opção.
     */
    public Map<String, String> descriptions(Reader configYml) throws IOException, InvalidConfigurationException {
        YamlConfiguration def = new YamlConfiguration();
        def.options().parseComments(true);
        def.load(configYml);
        Map<String, String> out = new LinkedHashMap<>();
        for (Setting<?> s : byKey.values()) {
            StringBuilder text = new StringBuilder();
            for (String line : def.getComments(s.path())) {
                if (line != null && !line.isBlank()) {
                    text.append(' ').append(line.trim());
                }
            }
            String clean = text.toString().replaceAll("\\s*\\[[^\\]]*\\]", "").trim();
            if (!clean.isEmpty()) {
                out.put(s.path(), clean);
            }
        }
        return out;
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
        cfg.set("manhunt.lista-jogadores", new ArrayList<>(playerList));
        RingPoint point = ringPoint;
        if (point == null) {
            cfg.set("manhunt.ponto-roda", null);
        } else {
            cfg.set("manhunt.ponto-roda.mundo", point.world());
            cfg.set("manhunt.ponto-roda.x", point.x());
            cfg.set("manhunt.ponto-roda.y", point.y());
            cfg.set("manhunt.ponto-roda.z", point.z());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            logger.warning("Não foi possível salvar o config.yml: " + e.getMessage());
        }
    }
}
