package dev.manhuntstar.config.upgrade;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The versions of the languages inside the jar ({@code version} in lang/*.yml), the same for all of
 * them. When a file in lang/ is older, it is updated keeping the texts the owner edited (Messages): the
 * steps rename or remove keys, and {@link #RESET} lists the texts whose default changed so much that the
 * new one replaces the owner's.
 *
 * <p>Versions: 1 is 2.0.0; 2 adds the FastStats options' texts; 3 asks for the menu's names in a dialog
 * instead of the chat.
 */
public final class LanguageUpgrade {

    public static final FileVersions VERSIONS = new FileVersions("version", 1, List.of(
            // 1 -> 2: settings.statistics and settings.errorreports (new texts only)
            FileVersions.nothing(),
            // 2 -> 3: menu.dialog.* in place of menu.type-name, type-skin, type-player, cancel-word and
            // cancelled, which the update leaves out because they no longer exist
            FileVersions.nothing()));

    /** For each version, the texts whose new default replaces the owner's. */
    private static final Map<Integer, Set<String>> RESET = Map.of(
            // the name is typed in a dialog, no longer in the chat
            3, Set.of("menu.create.name-lore"));

    private LanguageUpgrade() {
    }

    /** The texts reset by the versions after {@code from}, up to the latest. */
    public static Set<String> resetSince(int from) {
        Set<String> out = new HashSet<>();
        for (int v = from + 1; v <= VERSIONS.latest(); v++) {
            out.addAll(RESET.getOrDefault(v, Set.of()));
        }
        return out;
    }
}
