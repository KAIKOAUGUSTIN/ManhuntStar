package dev.manhuntstar.paper;

import dev.manhuntstar.platform.Effect;
import org.bukkit.potion.PotionEffectType;

/** Core effects as Bukkit {@link PotionEffectType}s (the same constants the code used before). */
public final class PaperEffects {

    private PaperEffects() {
    }

    public static PotionEffectType type(Effect effect) {
        return switch (effect) {
            case ABSORPTION -> PotionEffectType.ABSORPTION;
            case BLINDNESS -> PotionEffectType.BLINDNESS;
            case DOLPHINS_GRACE -> PotionEffectType.DOLPHINS_GRACE;
            case FIRE_RESISTANCE -> PotionEffectType.FIRE_RESISTANCE;
            case JUMP_BOOST -> PotionEffectType.JUMP_BOOST;
            case REGENERATION -> PotionEffectType.REGENERATION;
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case SPEED -> PotionEffectType.SPEED;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
        };
    }
}
