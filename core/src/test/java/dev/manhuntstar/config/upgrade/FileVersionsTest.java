package dev.manhuntstar.config.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.manhuntstar.config.SnakeYamlFiles;
import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The steps between versions: in order, from the file's version, and never on a newer file. */
class FileVersionsTest {

    private final YamlFiles yaml = new SnakeYamlFiles(Logger.getAnonymousLogger());

    private ConfigNode node(String text) {
        return yaml.load(new StringReader(text));
    }

    private final FileVersions versions = new FileVersions("v", 1, List.of(
            FileVersions.rename("a.old", "a.new"),
            FileVersions.all(FileVersions.remove("gone"), FileVersions.mapValues("mode", Map.of("both", "all"))),
            FileVersions.rename("section", "moved")));

    @Test
    void aFileWithoutTheKeyIsTheFirstVersion() {
        assertEquals(1, versions.versionOf(node("x: 1\n")));
        assertEquals(4, versions.latest());
    }

    @Test
    void stepsRunFromTheFileVersion() {
        ConfigNode n = node("a:\n  old: 5\ngone: true\nmode: both\nsection:\n  one: 1\n  deep:\n    two: 2\n");
        assertTrue(versions.upgrade(n));
        assertEquals(5, n.getInt("a.new", 0));
        assertFalse(n.contains("a.old"));
        assertNull(n.get("gone"));
        assertEquals("all", n.getString("mode"));
        assertEquals(1, n.getInt("moved.one", 0));
        assertEquals(2, n.getInt("moved.deep.two", 0));
        assertFalse(n.contains("section"));
        assertEquals(4, n.getInt("v", 0));
    }

    @Test
    void laterVersionsSkipTheEarlierSteps() {
        ConfigNode n = node("v: 3\na:\n  old: 5\nmode: both\n");
        assertTrue(versions.upgrade(n));
        assertEquals(5, n.getInt("a.old", 0));
        assertEquals("both", n.getString("mode"));
        assertEquals(4, n.getInt("v", 0));
    }

    @Test
    void aNewerFileIsNotTouched() {
        ConfigNode n = node("v: 9\na:\n  old: 5\n");
        assertFalse(versions.upgrade(n));
        assertEquals(5, n.getInt("a.old", 0));
        assertEquals(9, n.getInt("v", 0));
    }

    @Test
    void aFileThatDidntParseIsUnreadable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
        java.io.File file = dir.resolve("x.yml").toFile();
        java.nio.file.Files.writeString(file.toPath(), "a: [unclosed\n");
        assertTrue(FileVersions.unreadable(node(""), file));
        java.nio.file.Files.writeString(file.toPath(), "# only a comment\n\n");
        assertFalse(FileVersions.unreadable(node(""), file), "nothing to keep: not unreadable");
        java.nio.file.Files.writeString(file.toPath(), "a: 1\n");
        assertFalse(FileVersions.unreadable(node("a: 1\n"), file));
        assertFalse(FileVersions.unreadable(node(""), dir.resolve("missing.yml").toFile()));
    }

    @Test
    void aRenameKeepsAValueAlreadyAtTheNewPath() {
        ConfigNode n = node("a:\n  old: 5\n  new: 7\n");
        FileVersions.rename("a.old", "a.new").accept(n);
        assertEquals(7, n.getInt("a.new", 0));
        assertFalse(n.contains("a.old"));
    }

    @Test
    void aRenamedSectionKeepsTheValuesAlreadyInTheNewOne() {
        ConfigNode n = node("section:\n  a: 1\n  b: 2\nmoved:\n  b: 9\n");
        FileVersions.rename("section", "moved").accept(n);
        assertEquals(1, n.getInt("moved.a", 0));
        assertEquals(9, n.getInt("moved.b", 0), "the value already at the new path stays");
        assertFalse(n.contains("section"));
    }

    @Test
    void mapValuesLeavesAMissingValueAlone() {
        ConfigNode n = node("other: 1\n");
        FileVersions.mapValues("mode", Map.of("both", "all")).accept(n);
        assertFalse(n.contains("mode"));
        ConfigNode kept = node("mode: closest\n");
        FileVersions.mapValues("mode", Map.of("both", "all")).accept(kept);
        assertEquals("closest", kept.getString("mode"));
    }
}
