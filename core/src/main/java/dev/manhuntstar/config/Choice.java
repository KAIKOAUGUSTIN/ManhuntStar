package dev.manhuntstar.config;

import java.util.List;
import java.util.Locale;

/** A configurable enum: the name it has in the config and the aliases accepted in commands. */
public interface Choice {
    String configName();

    default List<String> aliases() {
        return List.of(configName().toLowerCase(Locale.ROOT));
    }
}
