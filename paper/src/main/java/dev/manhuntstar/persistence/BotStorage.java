package dev.manhuntstar.persistence;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.BotSnapshot;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.bot.PersistentGear;
import dev.manhuntstar.bot.SkinData;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.YamlFiles;
import dev.manhuntstar.util.Vec3;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Saves and reads the bots, groups, rings and the hunt in progress in {@code bots.yml}. The text is built on the main thread and
 * written on a separate thread, to a temporary file swapped in at once (it doesn't get corrupted if the server crashes).
 */
public final class BotStorage {

    private final File file;
    private final Logger logger;
    private final YamlFiles yaml;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ManhuntStar-Salvamento");
        t.setDaemon(true);
        return t;
    });
    private String lastWritten;
    /** Order of the saves (built on the main thread) and the last one that went to disk. */
    private long saveSeq;
    private long writtenSeq;

    /** @param manhunt rings and hunt in progress (null if there's none) */
    public record LoadResult(List<BotSnapshot> bots, List<BotGroup> groups, ConfigNode manhunt) {
    }

    public BotStorage(File dataFolder, Logger logger, YamlFiles yaml) {
        this.file = new File(dataFolder, "bots.yml");
        this.logger = logger;
        this.yaml = yaml;
    }

    /** @param manhunt writes the rings and the hunt in the "manhunt" section (null = not saved) */
    public void save(List<BotSnapshot> bots, List<BotGroup> groups, Consumer<ConfigNode> manhunt, boolean async) {
        ConfigNode y = yaml.create();
        y.set("versao", 1);
        if (manhunt != null) {
            manhunt.accept(y.createSection("manhunt"));
        }
        for (BotGroup g : groups) {
            ConfigNode s = y.createSection("grupos." + key(g.name()));
            s.set("nome", g.name());
            s.set("papel", g.role().name());
            s.set("rastrear-jogadores", g.trackPlayers());
            s.set("inimigos", new ArrayList<>(g.enemies()));
            if (g.allowedMobTargets() != null) {
                List<String> mobs = new ArrayList<>();
                for (EntityKind t : g.allowedMobTargets()) {
                    mobs.add(t.name());
                }
                s.set("mobs-permitidos", mobs);
            }
            if (g.spawnpoint() != null) {
                s.set("spawnpoint.mundo", g.spawnWorld());
                s.set("spawnpoint.x", g.spawnpoint().x());
                s.set("spawnpoint.y", g.spawnpoint().y());
                s.set("spawnpoint.z", g.spawnpoint().z());
            }
        }
        for (BotSnapshot b : bots) {
            ConfigNode s = y.createSection("bots." + b.id());
            s.set("nome", b.name());
            s.set("nome-visivel", b.visualName());
            if (b.skin() != null && b.skin().valid()) {
                s.set("skin.valor", b.skin().value());
                s.set("skin.assinatura", b.skin().signature());
            }
            s.set("mundo", b.world());
            s.set("x", b.x());
            s.set("y", b.y());
            s.set("z", b.z());
            s.set("yaw", b.yaw());
            s.set("pitch", b.pitch());
            s.set("vida", b.health());
            s.set("fome", b.food());
            s.set("saturacao", b.saturation());
            s.set("dificuldade", b.difficulty().name());
            s.set("fase", b.phase());
            s.set("progressao", b.progressionEnabled());
            List<String> inv = new ArrayList<>();
            for (GameItem it : b.inventory()) {
                inv.add(encode(it));
            }
            s.set("inventario", inv);
            s.set("equipamento.mao", encode(b.mainHand()));
            s.set("equipamento.mao-secundaria", encode(b.offHand()));
            s.set("equipamento.cabeca", encode(b.head()));
            s.set("equipamento.peito", encode(b.chest()));
            s.set("equipamento.pernas", encode(b.legs()));
            s.set("equipamento.pes", encode(b.feet()));
            PersistentGear g = b.gear();
            s.set("persistente.espada", encode(g.sword()));
            s.set("persistente.picareta", encode(g.pickaxe()));
            s.set("persistente.cabeca", encode(g.head()));
            s.set("persistente.peito", encode(g.chest()));
            s.set("persistente.pernas", encode(g.legs()));
            s.set("persistente.pes", encode(g.feet()));
            s.set("grupo", b.group());
            s.set("alvo", b.target() == null ? null : b.target().toString());
            s.set("renascer", b.shouldRespawn());
            s.set("cavalo-bloqueado-ticks", b.horseBlockedTicksLeft());
            s.set("totem", b.totemAvailable());
            s.set("agressividade", b.aggression());
            s.set("abates", b.kills());
            s.set("vivo", b.alive());
        }
        String text = yaml.saveToString(y);
        if (text.equals(lastWritten)) {
            return;
        }
        lastWritten = text;
        long seq = ++saveSeq;
        if (async) {
            writer.execute(() -> write(seq, text));
        } else {
            write(seq, text);
        }
    }

    private synchronized void write(long seq, String text) {
        // a direct save (shutdown, delete all) can jump ahead of one that was still in the
        // queue; the queued one, older, must not write over it
        if (seq <= writtenSeq) {
            return;
        }
        writtenSeq = seq;
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling("bots.yml.tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warning("Could not save bots.yml: " + e.getMessage());
        }
    }

    public LoadResult load() {
        List<BotSnapshot> bots = new ArrayList<>();
        List<BotGroup> groups = new ArrayList<>();
        if (!file.exists()) {
            return new LoadResult(bots, groups, null);
        }
        ConfigNode y = yaml.load(file);
        ConfigNode gs = y.section("grupos");
        if (gs != null) {
            for (String k : gs.keys(false)) {
                ConfigNode s = gs.section(k);
                if (s == null) {
                    continue;
                }
                BotGroup g = new BotGroup(s.getString("nome", k));
                try {
                    g.setRole(BotRole.valueOf(s.getString("papel", "HUNTER")));
                } catch (IllegalArgumentException ignored) {
                    g.setRole(BotRole.HUNTER);
                }
                g.setTrackPlayers(s.getBoolean("rastrear-jogadores", true));
                for (String e : s.getStringList("inimigos")) {
                    g.addEnemy(e);
                }
                if (s.isList("mobs-permitidos")) {
                    Set<EntityKind> types = new HashSet<>();
                    for (String m : s.getStringList("mobs-permitidos")) {
                        EntityKind kind = EntityKind.find(m);
                        if (kind != null) {
                            types.add(kind);
                        }
                        // otherwise a type removed from the game
                    }
                    g.setAllowedMobTargets(types);
                }
                if (s.isSection("spawnpoint")) {
                    g.setSpawnpoint(s.getString("spawnpoint.mundo"), new Vec3(s.getDouble("spawnpoint.x", 0), s.getDouble("spawnpoint.y", 0), s.getDouble("spawnpoint.z", 0)));
                }
                groups.add(g);
            }
        }
        ConfigNode bs = y.section("bots");
        if (bs != null) {
            for (String k : bs.keys(false)) {
                ConfigNode s = bs.section(k);
                if (s == null) {
                    continue;
                }
                try {
                    bots.add(readBot(UUID.fromString(k), s));
                } catch (RuntimeException e) {
                    logger.warning("Invalid saved bot (" + k + "): " + e.getMessage());
                }
            }
        }
        return new LoadResult(bots, groups, y.section("manhunt"));
    }

    private static BotSnapshot readBot(UUID id, ConfigNode s) {
        SkinData skin = s.isString("skin.valor") ? new SkinData(s.getString("skin.valor"), s.getString("skin.assinatura")) : null;
        List<GameItem> inv = new ArrayList<>();
        for (String e : s.getStringList("inventario")) {
            inv.add(decode(e));
        }
        PersistentGear gear = new PersistentGear(decode(s.getString("persistente.espada")), decode(s.getString("persistente.picareta")),
                decode(s.getString("persistente.cabeca")), decode(s.getString("persistente.peito")), decode(s.getString("persistente.pernas")),
                decode(s.getString("persistente.pes")));
        String target = s.getString("alvo");
        BotDifficulty diff = BotDifficulty.parse(s.getString("dificuldade", "TERMINATOR"));
        return new BotSnapshot(id, s.getString("nome", "Bot"), s.getString("nome-visivel", s.getString("nome", "Bot")), skin,
                s.getString("mundo"), s.getDouble("x", 0), s.getDouble("y", 0), s.getDouble("z", 0), (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0),
                s.getDouble("vida", 20), s.getInt("fome", 20), (float) s.getDouble("saturacao", 5), diff == null ? BotDifficulty.TERMINATOR : diff,
                s.getInt("fase", 1), s.getBoolean("progressao", true), inv,
                decode(s.getString("equipamento.mao")), decode(s.getString("equipamento.mao-secundaria")), decode(s.getString("equipamento.cabeca")),
                decode(s.getString("equipamento.peito")), decode(s.getString("equipamento.pernas")), decode(s.getString("equipamento.pes")),
                gear, s.getString("grupo"), target == null ? null : UUID.fromString(target), s.getBoolean("renascer", true),
                s.getLong("cavalo-bloqueado-ticks", 0), s.getBoolean("totem", true), s.getInt("agressividade", 0), s.getInt("abates", 0), s.getBoolean("vivo", true));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    private static String encode(GameItem s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(s.serialize());
    }

    private static GameItem decode(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return GameItem.empty();
        }
        try {
            return Platform.server().deserializeItem(Base64.getDecoder().decode(b64));
        } catch (RuntimeException e) {
            return GameItem.empty();
        }
    }

    public void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
