package dev.manhuntstar.bot;

import dev.manhuntstar.config.Choice;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;

/** Mode enums used in the plugin's options. */
public final class Modes {

    private Modes() {
    }

    /** Path planner. */
    public enum PathMode implements Choice {
        ASTAR, DUMB;

        @Override
        public String configName() {
            return name();
        }

        @Override
        public List<String> aliases() {
            return switch (this) {
                case ASTAR -> List.of("astar", "a*");
                case DUMB -> List.of("dumb", "dumb*");
            };
        }

        public boolean usesPlanner() {
            return this != DUMB;
        }
    }

    /** NORMAL: spawns exactly at the point. MANHUNT: spawns in a ring and stays still until the hunt starts. */
    public enum SpawnMode implements Choice {
        NORMAL, MANHUNT;

        @Override
        public String configName() {
            return name();
        }

        @Override
        public List<String> aliases() {
            return List.of(name().toLowerCase(Locale.ROOT));
        }
    }

    /** Ring center: AUTO = where the ring is created; MANUAL = point set with /manhunt ring point. */
    public enum RingCenter implements Choice {
        AUTO("auto", "automatic", "automatico", "automático"), MANUAL("manual", "fixed", "fixo");

        private final List<String> aliases;

        RingCenter(String... aliases) {
            this.aliases = List.of(aliases);
        }

        @Override
        public String configName() {
            return aliases.getFirst();
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    /** Players who join the server while a hunt is in progress. */
    public enum LateJoin implements Choice {
        HUNTED("hunted", "cacado", "caçado"), PROTECTED("protected", "protegido", "protecao", "proteção"), OUT("out", "ignored", "fora", "ignorado");

        private final List<String> aliases;

        LateJoin(String... aliases) {
            this.aliases = List.of(aliases);
        }

        @Override
        public String configName() {
            return aliases.getFirst();
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    /** Player list: OFF = everyone; ALLOWLIST = only those on the list; BLOCKLIST = everyone except those on the list. */
    public enum PlayerListMode implements Choice {
        OFF("off", "false", "desligada", "nenhuma"), ALLOWLIST("allowlist", "whitelist", "permitidos"), BLOCKLIST("blocklist", "blacklist", "bloqueados");

        private final List<String> aliases;

        PlayerListMode(String... aliases) {
            this.aliases = List.of(aliases);
        }

        @Override
        public String configName() {
            return aliases.getFirst();
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    /** How the bots pick the player to chase. */
    public enum TargetMode implements Choice {
        CLOSEST_PLAYER("closest", "nearest", "perto", "proximo"),
        EQUAL_BOTS_PER_PLAYER("equal", "balanced", "balance", "dividir", "igual"),
        FARTHEST_PLAYER("farthest", "farest", "longe");

        private final List<String> aliases;

        TargetMode(String... aliases) {
            this.aliases = List.of(aliases);
        }

        @Override
        public String configName() {
            return aliases.getFirst();
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    /** What happens to upgraded sword, pickaxe and armor when the bot dies. */
    public enum GearPersistence implements Choice {
        KEEP_ON_RESPAWN_NO_DROP, KEEP_AND_DROP, DROP_AND_LOSE;

        @Override
        public String configName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean keeps() {
            return this != DROP_AND_LOSE;
        }

        public boolean drops() {
            return this != KEEP_ON_RESPAWN_NO_DROP;
        }
    }

    /** In which dimensions the bots can throw ender pearls. */
    public enum PearlDimension implements Choice {
        NETHER_ONLY("nether", "0"), BOTH("both", "ambas", "1"), OVERWORLD_ONLY("overworld", "2");

        private final List<String> aliases;

        PearlDimension(String... aliases) {
            this.aliases = List.of(aliases);
        }

        @Override
        public String configName() {
            return aliases.getFirst();
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    /** Group role: HUNTER chases, ESCAPE runs away from the targets. */
    public enum BotRole {
        HUNTER, ESCAPE
    }

    /** Possible colors for the bots' glow. */
    public enum GlowColor implements Choice {
        RED(NamedTextColor.RED), DARK_RED(NamedTextColor.DARK_RED), GOLD(NamedTextColor.GOLD), YELLOW(NamedTextColor.YELLOW),
        GREEN(NamedTextColor.GREEN), DARK_GREEN(NamedTextColor.DARK_GREEN), AQUA(NamedTextColor.AQUA),
        DARK_AQUA(NamedTextColor.DARK_AQUA), BLUE(NamedTextColor.BLUE), DARK_BLUE(NamedTextColor.DARK_BLUE),
        LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE), DARK_PURPLE(NamedTextColor.DARK_PURPLE), WHITE(NamedTextColor.WHITE),
        GRAY(NamedTextColor.GRAY), DARK_GRAY(NamedTextColor.DARK_GRAY), BLACK(NamedTextColor.BLACK);

        private final NamedTextColor color;

        GlowColor(NamedTextColor color) {
            this.color = color;
        }

        public NamedTextColor color() {
            return color;
        }

        @Override
        public String configName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
