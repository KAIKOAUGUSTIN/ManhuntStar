package dev.manhuntstar.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Uma opção do plugin: caminho no config.yml (e o caminho em português das versões antigas, lido na
 * migração), chave usada nos comandos, valor padrão e validação. Nome e descrição ficam nos arquivos
 * de idioma ({@code options.<chave>}). O valor é volátil porque algumas opções (ex.: limite de nós
 * do A*) são lidas por threads de pathfinding.
 */
public abstract sealed class Setting<T> permits Setting.Bool, Setting.Int, Setting.Dbl, Setting.Enumerated, Setting.Text {

    private final String path;
    private final String legacyPath;
    private final String key;
    private final Category category;
    private final T defaultValue;
    private volatile T value;

    protected Setting(String path, String legacyPath, String key, Category category, T defaultValue) {
        this.path = path;
        this.legacyPath = legacyPath;
        this.key = key;
        this.category = category;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String path() {
        return path;
    }

    /** Caminho da opção no config.yml das versões com chaves em português (null se não existia). */
    public String legacyPath() {
        return legacyPath;
    }

    public String key() {
        return key;
    }

    public Category category() {
        return category;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        this.value = sanitize(newValue);
    }

    /** Converte um texto digitado em comando; lança {@link InvalidValue} se inválido. */
    public abstract T parse(String raw);

    /** Define a partir de texto (comando ou menu). */
    public void setFromText(String raw) {
        set(parse(raw));
    }

    public void reset() {
        value = defaultValue;
    }

    public abstract List<String> suggestions();

    protected T sanitize(T v) {
        return v == null ? defaultValue : v;
    }

    void load(ConfigurationSection cfg) {
        load(cfg, path);
    }

    /** Lê a opção de outro caminho (o antigo, na migração do config.yml). */
    void load(ConfigurationSection cfg, String from) {
        Object raw = from == null ? null : cfg.get(from);
        if (raw == null) {
            value = defaultValue;
            return;
        }
        try {
            value = sanitize(parse(String.valueOf(raw)));
        } catch (IllegalArgumentException e) {
            value = defaultValue;
        }
    }

    void save(ConfigurationSection cfg) {
        cfg.set(path, serialize(value));
    }

    protected Object serialize(T v) {
        return v;
    }

    public String display() {
        return String.valueOf(serialize(value));
    }

    public enum Category {
        GENERAL, BOTS, COMBAT, MOBILITY, PATHFINDING, TACTICS, MANHUNT, APPEARANCE
    }

    public static final class Bool extends Setting<Boolean> {
        public Bool(String path, String legacyPath, String key, Category category, boolean def) {
            super(path, legacyPath, key, category, def);
        }

        @Override
        public Boolean parse(String raw) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "true", "on", "sim", "s", "1", "yes", "ligado", "ativado" -> true;
                case "false", "off", "nao", "não", "n", "0", "no", "desligado", "desativado" -> false;
                default -> throw new InvalidValue("settings.error.boolean");
            };
        }

        @Override
        public List<String> suggestions() {
            return List.of("true", "false");
        }

        public boolean on() {
            return get();
        }
    }

    public static final class Int extends Setting<Integer> {
        private final int min;
        private final int max;

        public Int(String path, String legacyPath, String key, Category category, int def, int min, int max) {
            super(path, legacyPath, key, category, def);
            this.min = min;
            this.max = max;
        }

        @Override
        public Integer parse(String raw) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new InvalidValue("settings.error.integer", "min", min, "max", max);
            }
        }

        @Override
        protected Integer sanitize(Integer v) {
            return v == null ? defaultValue() : Math.max(min, Math.min(max, v));
        }

        @Override
        public List<String> suggestions() {
            return List.of(String.valueOf(defaultValue()));
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public int value() {
            return get();
        }
    }

    public static final class Dbl extends Setting<Double> {
        private final double min;
        private final double max;
        private final double step;

        public Dbl(String path, String legacyPath, String key, Category category, double def, double min, double max, double step) {
            super(path, legacyPath, key, category, def);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public Double parse(String raw) {
            try {
                return Double.parseDouble(raw.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new InvalidValue("settings.error.number", "min", min, "max", max);
            }
        }

        @Override
        protected Double sanitize(Double v) {
            if (v == null || v.isNaN()) {
                return defaultValue();
            }
            return Math.max(min, Math.min(max, v));
        }

        @Override
        public List<String> suggestions() {
            return List.of(String.valueOf(defaultValue()));
        }

        @Override
        public String display() {
            double v = get();
            return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        public double step() {
            return step;
        }

        public double value() {
            return get();
        }
    }

    public static final class Enumerated<E extends Enum<E> & Choice> extends Setting<E> {
        private final Class<E> type;

        public Enumerated(String path, String legacyPath, String key, Category category, Class<E> type, E def) {
            super(path, legacyPath, key, category, def);
            this.type = type;
        }

        @Override
        public E parse(String raw) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            for (E e : type.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(s) || e.aliases().contains(s)) {
                    return e;
                }
            }
            throw new InvalidValue("settings.error.choice", "options", String.join(", ", suggestions()));
        }

        @Override
        protected Object serialize(E v) {
            return v.configName();
        }

        @Override
        public List<String> suggestions() {
            List<String> out = new ArrayList<>();
            for (E e : type.getEnumConstants()) {
                out.add(e.configName());
            }
            return out;
        }

        public E next() {
            E[] all = type.getEnumConstants();
            return all[(get().ordinal() + 1) % all.length];
        }
    }

    public static final class Text extends Setting<String> {
        private final Supplier<List<String>> suggestions;
        private final boolean onlySuggestions;

        public Text(String path, String legacyPath, String key, Category category, String def, String... suggestions) {
            super(path, legacyPath, key, category, def);
            List<String> fixed = Arrays.asList(suggestions);
            this.suggestions = () -> fixed;
            this.onlySuggestions = false;
        }

        /**
         * Texto que só aceita uma das sugestões, que mudam com o tempo (ex.: os idiomas da pasta
         * lang/). Maiúsculas e "_" viram minúsculas e "-" ("PT_BR" = "pt-br").
         */
        public Text(String path, String legacyPath, String key, Category category, String def, Supplier<List<String>> suggestions) {
            super(path, legacyPath, key, category, def);
            this.suggestions = suggestions;
            this.onlySuggestions = true;
        }

        @Override
        public String parse(String raw) {
            String value = raw.trim();
            if (!onlySuggestions) {
                return value;
            }
            value = value.toLowerCase(Locale.ROOT).replace('_', '-');
            List<String> options = suggestions.get();
            if (!options.contains(value)) {
                throw new InvalidValue("settings.error.choice", "options", String.join(", ", options));
            }
            return value;
        }

        @Override
        public List<String> suggestions() {
            return suggestions.get();
        }
    }

    /**
     * Valor inválido digitado num comando ou no menu: a mensagem é a chave do arquivo de idioma, com
     * os placeholders em pares (nome, valor).
     */
    public static final class InvalidValue extends IllegalArgumentException {
        private final transient Object[] placeholders;

        public InvalidValue(String messageKey, Object... placeholders) {
            super(messageKey);
            this.placeholders = placeholders;
        }

        public Object[] placeholders() {
            return placeholders;
        }
    }

    /** Enums configuráveis expõem um nome para o config e apelidos aceitos nos comandos. */
    public interface Choice {
        String configName();

        default List<String> aliases() {
            return List.of(configName().toLowerCase(Locale.ROOT));
        }
    }
}
