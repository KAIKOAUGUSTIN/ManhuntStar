package dev.manhuntstar.platform;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

/** Reads and writes YAML files as {@link ConfigNode}s. */
public interface YamlFiles {

    /** Reads a file; a missing or invalid file gives an empty document (the error is logged). */
    ConfigNode load(File file);

    /** Reads a document from text (e.g. a file inside the jar). */
    ConfigNode load(Reader reader);

    /** A new empty document. */
    ConfigNode create();

    /** Writes the document, keeping the comments it had when it was read. */
    void save(ConfigNode node, File file) throws IOException;

    /** The document as YAML text. */
    String saveToString(ConfigNode node);
}
