package dev.manhuntstar.config;

import dev.manhuntstar.platform.ConfigNode;
import dev.manhuntstar.platform.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;

/** Delegates reads and fails writes after checking the state at the save boundary. */
public record FailingSaveYamlFiles(YamlFiles delegate, Consumer<File> beforeFailure) implements YamlFiles {
    public ConfigNode load(File file) { return delegate.load(file); }
    public ConfigNode load(Reader reader) { return delegate.load(reader); }
    public ConfigNode create() { return delegate.create(); }
    public String saveToString(ConfigNode node) { return delegate.saveToString(node); }
    public void save(ConfigNode node, File file) throws IOException {
        beforeFailure.accept(file);
        throw new IOException("Simulated save failure");
    }
}
