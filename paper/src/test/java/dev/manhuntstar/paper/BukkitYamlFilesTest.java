package dev.manhuntstar.paper;

import dev.manhuntstar.config.YamlFilesContract;
import dev.manhuntstar.platform.YamlFiles;

/** Bukkit's YamlConfiguration behind the core interface does what core expects. */
class BukkitYamlFilesTest extends YamlFilesContract {

    private final YamlFiles yaml = new BukkitYamlFiles();

    @Override
    protected YamlFiles yaml() {
        return yaml;
    }
}
