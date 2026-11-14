package dev.manhuntstar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.Modes.LateJoin;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.bot.Modes.PlayerListMode;
import dev.manhuntstar.bot.Modes.RingCenter;
import dev.manhuntstar.bot.Modes.TargetMode;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.YamlFiles;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What every {@link YamlFiles} must do, so config.yml, the languages and bots.yml are read the same way
 * on every platform. Each platform runs these tests with its own implementation.
 */
public abstract class YamlFilesContract {

    protected abstract YamlFiles yaml();

    @BeforeAll
    static void registerTypes() {
        EntityKind.register("CREEPER");
    }

    private ConfigNode read(String text) {
        return yaml().load(new StringReader(text));
    }

    /**
     * A value that can't be built (an unknown tag) after a key that could: the whole document comes back
     * empty, never with the keys read before the error, so a file that can't be read is never rewritten
     * with values missing (FileVersions.unreadable).
     */
    @Test
    void aFileThatFailsPartWayLoadsEmpty(@TempDir Path dir) throws java.io.IOException {
        String text = "general:\n  language: en\nbots: !!manhuntstar.Unknown {x: 1}\n";
        Path file = dir.resolve("config.yml");
        Files.writeString(file, text);
        ConfigNode cfg = yaml().load(file.toFile());
        assertEquals(Set.of(), cfg.keys(false));
        assertTrue(dev.manhuntstar.config.upgrade.FileVersions.unreadable(cfg, file.toFile()));
        assertEquals(Set.of(), read(text).keys(false));
    }

    @Test
    void readsValuesAndSections() {
        ConfigNode cfg = read("""
                a:
                  text: hello
                  number: 3
                  real: 2.5
                  flag: true
                  list: [x, 1, true]
                  inner:
                    deep: v
                top: 1
                """);
        assertTrue(cfg.isSection("a"));
        assertTrue(cfg.isString("a.text"));
        assertEquals("hello", cfg.getString("a.text"));
        assertEquals("3", cfg.getString("a.number"));
        assertNull(cfg.getString("a.missing"));
        assertEquals(3, cfg.getInt("a.number", 0));
        assertEquals(7, cfg.getInt("a.text", 7));
        assertEquals(2.5, cfg.getDouble("a.real", 0));
        assertEquals(3.0, cfg.getDouble("a.number", 0));
        assertTrue(cfg.getBoolean("a.flag", false));
        assertTrue(cfg.isList("a.list"));
        assertEquals(List.of("x", "1", "true"), cfg.getStringList("a.list"));
        assertEquals(List.of(), cfg.getStringList("a.text"));
        assertTrue(cfg.contains("a.inner.deep"));
        assertFalse(cfg.contains("a.inner.nope"));
        assertEquals("v", cfg.section("a").section("inner").getString("deep"));
        assertNull(cfg.section("a.text"));
        assertEquals(Set.of("a", "top"), cfg.keys(false));
        assertEquals(List.of("a", "a.text", "a.number", "a.real", "a.flag", "a.list", "a.inner", "a.inner.deep", "top"),
                List.copyOf(cfg.keys(true)));
        assertTrue(cfg.get("a.inner") instanceof ConfigNode);
    }

    @Test
    void writesAndRemoves() {
        ConfigNode cfg = yaml().create();
        cfg.set("x.y.z", 5);
        cfg.set("x.list", List.of("a", "b"));
        cfg.createSection("s").set("k", "v");
        assertEquals(5, cfg.getInt("x.y.z", 0));
        assertEquals("v", cfg.getString("s.k"));
        cfg.set("x.y", null);
        assertFalse(cfg.contains("x.y.z"));
        ConfigNode again = read(yaml().saveToString(cfg));
        assertEquals(List.of("a", "b"), again.getStringList("x.list"));
        assertEquals("v", again.getString("s.k"));
        assertFalse(again.contains("x.y"));
    }

    /** Saving a file that was read keeps its comments, including when a value changes. */
    @Test
    void keepsComments(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("c.yml");
        Files.writeString(file, """
                # header
                general:
                  # the language
                  language: en
                  count: 2 # inline
                """);
        ConfigNode cfg = yaml().load(file.toFile());
        cfg.set("general.language", "pt-br");
        cfg.set("general.added", true);
        yaml().save(cfg, file.toFile());
        String text = Files.readString(file);
        assertTrue(text.contains("# header"), text);
        assertTrue(text.contains("# the language"), text);
        assertTrue(text.contains("# inline"), text);
        ConfigNode again = yaml().load(file.toFile());
        assertEquals("pt-br", again.getString("general.language"));
        assertEquals(2, again.getInt("general.count", 0));
        assertTrue(again.getBoolean("general.added", false));
    }

    @Test
    void missingFileIsEmpty(@TempDir Path dir) {
        ConfigNode cfg = yaml().load(dir.resolve("nope.yml").toFile());
        assertTrue(cfg.keys(true).isEmpty());
    }

    /** A config.yml from the versions with Portuguese keys is read from the old paths, and its Portuguese values are converted. */
    @Test
    void legacyConfigIsMigrated(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                geral:
                  tab-list: false
                bots:
                  vida-maxima: 40
                  dificuldade-padrao: dificil
                  mobs-que-nao-atacam-bots: [CREEPER]
                combate:
                  modo-alvo: perto
                mobilidade:
                  perolas: "nao"
                  dimensao-perolas: ambas
                manhunt:
                  centro-roda: fixo
                  quem-entra-no-meio: cacado
                  lista-modo: permitidos
                  lista-jogadores: [Steve]
                  ponto-roda: {mundo: world, x: 1.0, y: 64.0, z: -2.0}
                """);
        assertTrue(Settings.isLegacy(yaml(), file.toFile()));
        Settings s = new Settings();
        s.loadLegacy(yaml(), file.toFile(), Logger.getAnonymousLogger());
        assertFalse(s.tabList.on());
        assertEquals(40.0, s.botHealth.value());
        assertSame(BotDifficulty.HARD, s.defaultDifficulty.get());
        assertSame(TargetMode.CLOSEST_PLAYER, s.targetMode.get());
        assertFalse(s.allowPearl.on());
        assertSame(PearlDimension.BOTH, s.pearlDimension.get());
        assertSame(RingCenter.MANUAL, s.ringCenter.get());
        assertSame(LateJoin.HUNTED, s.lateJoin.get());
        assertSame(PlayerListMode.ALLOWLIST, s.playerListMode.get());
        assertTrue(s.isListed("steve"));
        assertTrue(s.excludedMobs().contains(EntityKind.of("CREEPER")));
        assertEquals(new Settings.RingPoint("world", 1.0, 64.0, -2.0), s.ringPoint());
        assertEquals("pt-br", s.language.get());

        Path migrated = dir.resolve("new.yml");
        s.save(yaml(), migrated.toFile(), Logger.getAnonymousLogger());
        assertFalse(Settings.isLegacy(yaml(), migrated.toFile()));
        Settings again = new Settings();
        again.load(yaml(), migrated.toFile(), Logger.getAnonymousLogger());
        assertEquals(40.0, again.botHealth.value());
        assertSame(PlayerListMode.ALLOWLIST, again.playerListMode.get());
        assertEquals(new Settings.RingPoint("world", 1.0, 64.0, -2.0), again.ringPoint());
        assertTrue(again.excludedMobs().contains(EntityKind.of("CREEPER")));
        assertEquals("pt-br", again.language.get());
    }

    /** Unknown mob types in the list are left out (with a warning) instead of failing the whole file. */
    @Test
    void unknownMobTypeIsSkipped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                bots:
                  mobs-that-ignore-bots: [creeper, NOT_A_MOB]
                """);
        Settings s = new Settings();
        s.load(yaml(), file.toFile(), Logger.getAnonymousLogger());
        assertEquals(Set.of(EntityKind.of("CREEPER")), s.excludedMobs());
    }
}
