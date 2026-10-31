package dev.manhuntstar.bot;

import dev.manhuntstar.config.Setting;
import java.util.List;
import java.util.Locale;

/**
 * Dificuldade do bot: fase inicial, chance de drop por fase, duração de cada fase,
 * multiplicador de dano recebido e de dano causado. O nome mostrado fica nos arquivos de idioma
 * ({@code difficulty.<nome>}).
 */
public enum BotDifficulty implements Setting.Choice {
    EASY(1, 1.0f, 300, 1.0f, 1.0f),
    MEDIUM(2, 0.5f, 180, 0.95f, 1.0f),
    HARD(4, 0.1f, 250, 0.9f, 1.1f),
    TERMINATOR(8, 0.0f, 600, 0.85f, 1.15f);

    private final int startPhase;
    private final float dropChance;
    private final int phaseDurationSeconds;
    private final float damageTakenMultiplier;
    private final float attackMultiplier;

    BotDifficulty(int startPhase, float dropChance, int phaseDurationSeconds, float damageTakenMultiplier, float attackMultiplier) {
        this.startPhase = startPhase;
        this.dropChance = dropChance;
        this.phaseDurationSeconds = phaseDurationSeconds;
        this.damageTakenMultiplier = damageTakenMultiplier;
        this.attackMultiplier = attackMultiplier;
    }

    public int startPhase() {
        return startPhase;
    }

    public float dropChance() {
        return dropChance;
    }

    public int phaseDurationTicks() {
        return phaseDurationSeconds * 20;
    }

    public float damageTakenMultiplier() {
        return damageTakenMultiplier;
    }

    public float attackMultiplier() {
        return attackMultiplier;
    }

    @Override
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> aliases() {
        String n = configName();
        return switch (this) {
            case EASY -> List.of(n, "difficulty.easy", "facil", "fácil");
            case MEDIUM -> List.of(n, "difficulty.medium", "medio", "médio");
            case HARD -> List.of(n, "difficulty.hard", "dificil", "difícil");
            case TERMINATOR -> List.of(n, "difficulty.terminator");
        };
    }

    public static BotDifficulty parse(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        for (BotDifficulty d : values()) {
            if (d.aliases().contains(s)) {
                return d;
            }
        }
        return null;
    }
}
