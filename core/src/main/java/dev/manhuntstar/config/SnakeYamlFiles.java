package dev.manhuntstar.config;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * YAML files with SnakeYAML, for the platforms that don't have Bukkit's YamlConfiguration. Like it, it
 * keeps the comments of the file it read when it saves: the ones above each key, the ones at the end
 * of a line and the ones at the end of a section.
 */
public final class SnakeYamlFiles implements YamlFiles {

    private final Logger logger;

    public SnakeYamlFiles(Logger logger) {
        this.logger = logger;
    }

    private static DumperOptions dumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setProcessComments(true);
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        return options;
    }

    private static LoaderOptions loaderOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        options.setMaxAliasesForCollections(Integer.MAX_VALUE);
        options.setCodePointLimit(Integer.MAX_VALUE);
        return options;
    }

    /** SafeConstructor with {@code constructObject} open, to turn each value node into a Java value. */
    private static final class Constructor extends SafeConstructor {
        Constructor() {
            super(loaderOptions());
        }

        Object construct(Node node) {
            return constructObject(node);
        }

        void flatten(MappingNode node) {
            flattenMapping(node);
        }
    }

    @Override
    public ConfigNode load(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException | RuntimeException e) {
            if (file.exists()) {
                logger.log(Level.SEVERE, "Cannot load " + file, e);
            }
            return create();
        }
    }

    @Override
    public ConfigNode load(Reader reader) {
        try {
            return read(reader);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Cannot load configuration from stream", e);
            return create();
        }
    }

    private ConfigNode read(Reader reader) {
        Constructor constructor = new Constructor();
        DumperOptions dumper = dumperOptions();
        Yaml yaml = new Yaml(constructor, new Representer(dumper), dumper, loaderOptions());
        Node root = yaml.compose(reader);
        SnakeYamlNode section = new SnakeYamlNode();
        if (root == null) {
            return section;
        }
        if (!(root instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("Top level is not a Map.");
        }
        fill(section, mapping, constructor);
        return section;
    }

    private static void fill(SnakeYamlNode section, MappingNode mapping, Constructor constructor) {
        constructor.flatten(mapping);
        for (NodeTuple tuple : mapping.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();
            String key = String.valueOf(constructor.construct(keyNode));
            Object value;
            if (valueNode instanceof MappingNode child) {
                SnakeYamlNode sub = new SnakeYamlNode();
                fill(sub, child, constructor);
                value = sub;
            } else {
                value = constructor.construct(valueNode);
            }
            SnakeYamlNode.Entry entry = section.put(key, value);
            entry.block = keyNode.getBlockComments();
            entry.keyInline = keyNode.getInLineComments();
            entry.valueInline = valueNode.getInLineComments();
        }
        section.endComments = mapping.getEndComments();
    }

    @Override
    public ConfigNode create() {
        return new SnakeYamlNode();
    }

    @Override
    public void save(ConfigNode node, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            write(node, writer);
        }
    }

    @Override
    public String saveToString(ConfigNode node) {
        StringWriter writer = new StringWriter();
        write(node, writer);
        return writer.toString();
    }

    private static void write(ConfigNode node, Writer writer) {
        DumperOptions dumper = dumperOptions();
        Representer representer = new Representer(dumper);
        Yaml yaml = new Yaml(new Constructor(), representer, dumper, loaderOptions());
        MappingNode root = toNode((SnakeYamlNode) node, representer);
        if (root.getValue().isEmpty() && root.getEndComments() == null) {
            return;
        }
        yaml.serialize(root, writer);
    }

    private static MappingNode toNode(SnakeYamlNode section, Representer representer) {
        List<NodeTuple> tuples = new ArrayList<>();
        for (Map.Entry<String, SnakeYamlNode.Entry> e : section.entries().entrySet()) {
            SnakeYamlNode.Entry entry = e.getValue();
            Node keyNode = new ScalarNode(Tag.STR, e.getKey(), null, null, DumperOptions.ScalarStyle.PLAIN);
            Node valueNode = entry.value instanceof SnakeYamlNode child
                    ? toNode(child, representer)
                    : representer.represent(entry.value);
            keyNode.setBlockComments(entry.block);
            keyNode.setInLineComments(entry.keyInline);
            valueNode.setInLineComments(entry.valueInline);
            tuples.add(new NodeTuple(keyNode, valueNode));
        }
        MappingNode mapping = new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
        mapping.setEndComments(section.endComments == null ? null : new ArrayList<CommentLine>(section.endComments));
        return mapping;
    }
}
