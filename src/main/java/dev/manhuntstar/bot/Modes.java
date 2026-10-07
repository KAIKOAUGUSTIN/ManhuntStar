package dev.manhuntstar.bot;

import dev.manhuntstar.config.Setting;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;

/** Enums de modo usados nas opções do plugin. */
public final class Modes {

    private Modes() {
    }

    /** Planejador de caminho. */
    public enum PathMode implements Setting.Choice {
        ASTAR, DIJKSTRA, DUMB;

        @Override
        public String configName() {
            return name();
        }

        @Override
        public List<String> aliases() {
            return switch (this) {
                case ASTAR -> List.of("astar", "a*");
                case DIJKSTRA -> List.of("dijkstra");
                case DUMB -> List.of("dumb", "dumb*");
            };
        }

        public boolean usesPlanner() {
            return this != DUMB;
        }
    }

    /** NORMAL: nasce exatamente no ponto. MANHUNT: nasce em roda em volta do jogador e fica parado até a caçada começar. */
    public enum SpawnMode implements Setting.Choice {
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

    /** Como os bots escolhem o jogador a perseguir. */
    public enum TargetMode implements Setting.Choice {
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

    /** O que acontece com espada, picareta e armadura melhoradas quando o bot morre. */
    public enum GearPersistence implements Setting.Choice {
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

    /** Em quais dimensões os bots podem jogar pérolas do End. */
    public enum PearlDimension implements Setting.Choice {
        NETHER_ONLY("nether", "0"), BOTH("ambas", "both", "1"), OVERWORLD_ONLY("overworld", "2");

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

    /** Papel do grupo: HUNTER persegue, ESCAPE foge dos alvos. */
    public enum BotRole {
        HUNTER, ESCAPE
    }

    /** Cores possíveis para o brilho dos bots. */
    public enum GlowColor implements Setting.Choice {
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
