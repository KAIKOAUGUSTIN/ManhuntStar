package dev.manhuntstar.mod;

import dev.manhuntstar.config.YamlFilesContract;
import dev.manhuntstar.platform.YamlFiles;
import java.util.logging.Logger;

/** The mods' YAML files behind the core interface do what core expects, like Paper's. */
class ModYamlFilesTest extends YamlFilesContract {

    private final YamlFiles yaml = new ModYamlFiles(Logger.getAnonymousLogger());

    @Override
    protected YamlFiles yaml() {
        return yaml;
    }
}
