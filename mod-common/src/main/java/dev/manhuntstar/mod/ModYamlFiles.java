package dev.manhuntstar.mod;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/**
 * YAML files read and written the way Bukkit's {@code YamlConfiguration} does (same SnakeYAML options,
 * same handling of comments, header and footer), so the mod keeps config.yml, the language files and
 * bots.yml exactly as the plugin does.
 */
public final class ModYamlFiles implements YamlFiles {

    private final Logger logger;

    public ModYamlFiles(Logger logger) {
        this.logger = logger;
    }

    /** SnakeYAML's constructor with Bukkit's two helpers. */
    private static final class Constructor extends SafeConstructor {
        Constructor(LoaderOptions options) {
            super(options);
        }

        void flatten(MappingNode node) {
            flattenMapping(node);
        }

        Object construct(Node node) {
            return constructObject(node);
        }
    }

    /** SnakeYAML's representer that writes a section as a map. */
    private static final class SectionRepresenter extends Representer {
        SectionRepresenter(DumperOptions options) {
            super(options);
            this.multiRepresenters.put(ModConfigNode.class, new RepresentSection());
            this.multiRepresenters.remove(Enum.class);
        }

        private final class RepresentSection implements Represent {
            @Override
            public Node representData(Object data) {
                return SectionRepresenter.this.representMapping(Tag.MAP, ((ModConfigNode) data).values(), DumperOptions.FlowStyle.BLOCK);
            }
        }
    }

    /** One parser and printer per use, as each YamlConfiguration has its own. */
    private static final class Io {
        final DumperOptions dumper = new DumperOptions();
        final LoaderOptions loader = new LoaderOptions();
        final Constructor constructor;
        final SectionRepresenter representer;
        final Yaml yaml;

        Io() {
            dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumper.setIndent(2);
            dumper.setWidth(80);
            dumper.setProcessComments(true);
            loader.setMaxAliasesForCollections(Integer.MAX_VALUE);
            loader.setCodePointLimit(Integer.MAX_VALUE);
            loader.setNestingDepthLimit(100);
            loader.setProcessComments(true);
            constructor = new Constructor(loader);
            representer = new SectionRepresenter(dumper);
            representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            yaml = new Yaml(constructor, representer, dumper, loader);
        }
    }

    @Override
    public ConfigNode load(File file) {
        ModConfigNode doc = new ModConfigNode();
        try {
            loadInto(doc, read(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)));
        } catch (FileNotFoundException ignored) {
            // a missing file is an empty document
        } catch (IOException | YAMLException | ClassCastException e) {
            logger.log(Level.SEVERE, "Cannot load " + file, e);
            // not the keys read before the error: an empty document is how a file that can't be read
            // looks to the callers (FileVersions.unreadable), and they then don't rewrite it
            return new ModConfigNode();
        }
        return doc;
    }

    @Override
    public ConfigNode load(Reader reader) {
        ModConfigNode doc = new ModConfigNode();
        try {
            loadInto(doc, read(reader));
        } catch (IOException | YAMLException | ClassCastException e) {
            logger.log(Level.SEVERE, "Cannot load configuration from stream", e);
            return new ModConfigNode();
        }
        return doc;
    }

    @Override
    public ConfigNode create() {
        return new ModConfigNode();
    }

    @Override
    public void save(ConfigNode node, File file) throws IOException {
        File parent = file.getCanonicalFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            w.write(saveToString(node));
        }
    }

    @Override
    public String saveToString(ConfigNode node) {
        ModConfigNode doc = ((ModConfigNode) node).root();
        Io io = new Io();
        MappingNode tree = toNodeTree(io, doc);
        tree.setBlockComments(commentLines(saveHeader(doc.header), CommentType.BLOCK));
        tree.setEndComments(commentLines(doc.footer, CommentType.BLOCK));
        StringWriter out = new StringWriter();
        if (tree.getBlockComments().isEmpty() && tree.getEndComments().isEmpty() && tree.getValue().isEmpty()) {
            return "";
        }
        if (tree.getValue().isEmpty()) {
            tree.setFlowStyle(DumperOptions.FlowStyle.FLOW);
        }
        io.yaml.serialize(tree, out);
        return out.toString();
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static void loadInto(ModConfigNode doc, String contents) throws IOException {
        Io io = new Io();
        MappingNode node;
        try (Reader r = new UnicodeReader(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)))) {
            Node raw = io.yaml.compose(r);
            if (raw != null && !(raw instanceof MappingNode)) {
                throw new YAMLException("Top level is not a Map.");
            }
            node = (MappingNode) raw;
        }
        doc.map.clear();
        if (node != null) {
            adjustNodeComments(node);
            doc.header = loadHeader(commentLines(node.getBlockComments()));
            doc.footer = commentLines(node.getEndComments());
            fromNodeTree(io, node, doc);
        }
    }

    /** The comments above the first key up to the last blank line are the document's header. */
    private static void adjustNodeComments(MappingNode node) {
        if (node.getBlockComments() == null && !node.getValue().isEmpty()) {
            Node first = node.getValue().get(0).getKeyNode();
            List<CommentLine> lines = first.getBlockComments();
            if (lines != null) {
                int index = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).getCommentType() == CommentType.BLANK_LINE) {
                        index = i;
                    }
                }
                if (index != -1) {
                    node.setBlockComments(lines.subList(0, index + 1));
                    first.setBlockComments(lines.subList(index + 1, lines.size()));
                }
            }
        }
    }

    private static void fromNodeTree(Io io, MappingNode input, ModConfigNode section) {
        io.constructor.flatten(input);
        for (NodeTuple t : input.getValue()) {
            Node key = t.getKeyNode();
            String k = String.valueOf(io.constructor.construct(key));
            Node value = t.getValueNode();
            while (value instanceof AnchorNode a) {
                value = a.getRealNode();
            }
            if (value instanceof MappingNode m) {
                fromNodeTree(io, m, section.createSection(k));
            } else {
                section.set(k, io.constructor.construct(value));
            }
            ModConfigNode.Entry e = section.map.get(k);
            if (e != null) {
                e.comments = unmodifiable(commentLines(key.getBlockComments()));
                List<String> inline = !(value instanceof MappingNode) && !(value instanceof SequenceNode)
                        ? commentLines(value.getInLineComments()) : commentLines(key.getInLineComments());
                e.inlineComments = unmodifiable(inline);
            }
        }
    }

    /** A fixed copy that keeps the nulls (blank lines), which {@code List.copyOf} refuses. */
    private static List<String> unmodifiable(List<String> lines) {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    private static MappingNode toNodeTree(Io io, ModConfigNode section) {
        List<NodeTuple> tuples = new ArrayList<>();
        for (Map.Entry<String, ModConfigNode.Entry> entry : section.map.entrySet()) {
            Node key = io.representer.represent(entry.getKey());
            Object data = entry.getValue().data;
            Node value = data instanceof ModConfigNode sub ? toNodeTree(io, sub) : io.representer.represent(data);
            key.setBlockComments(commentLines(entry.getValue().comments, CommentType.BLOCK));
            if (!(value instanceof MappingNode) && !(value instanceof SequenceNode)) {
                value.setInLineComments(commentLines(entry.getValue().inlineComments, CommentType.IN_LINE));
            } else {
                key.setInLineComments(commentLines(entry.getValue().inlineComments, CommentType.IN_LINE));
            }
            tuples.add(new NodeTuple(key, value));
        }
        return new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
    }

    private static List<String> commentLines(List<CommentLine> comments) {
        List<String> lines = new ArrayList<>();
        if (comments != null) {
            for (CommentLine c : comments) {
                if (c.getCommentType() == CommentType.BLANK_LINE) {
                    lines.add(null);
                } else {
                    String line = c.getValue();
                    lines.add(line.startsWith(" ") ? line.substring(1) : line);
                }
            }
        }
        return lines;
    }

    private static List<CommentLine> commentLines(List<String> comments, CommentType type) {
        List<CommentLine> lines = new ArrayList<>();
        for (String c : comments) {
            if (c == null) {
                lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            } else {
                lines.add(new CommentLine(null, null, c.isEmpty() ? c : " " + c, type));
            }
        }
        return lines;
    }

    private static List<String> loadHeader(List<String> header) {
        LinkedList<String> list = new LinkedList<>(header);
        if (!list.isEmpty()) {
            list.removeLast();
        }
        while (!list.isEmpty() && list.peek() == null) {
            list.remove();
        }
        return list;
    }

    private static List<String> saveHeader(List<String> header) {
        LinkedList<String> list = new LinkedList<>(header);
        if (!list.isEmpty()) {
            list.add(null);
        }
        return list;
    }
}
