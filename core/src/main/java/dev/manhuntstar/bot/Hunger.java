package dev.manhuntstar.bot;

/**
 * Simulated hunger with the same rules as the player's FoodData:
 * exhaustion uses up saturation/food, fast regeneration with saturation and a full food bar,
 * normal regeneration with food >= 18 and starvation damage when it reaches zero.
 */
public final class Hunger {

    private int foodLevel = 20;
    private float saturation = 5.0f;
    private float exhaustion;
    private int tickTimer;

    public int foodLevel() {
        return foodLevel;
    }

    public float saturation() {
        return saturation;
    }

    public void set(int food, float sat) {
        this.foodLevel = Math.max(0, Math.min(20, food));
        this.saturation = Math.max(0f, Math.min(sat, this.foodLevel));
    }

    public void addExhaustion(float amount) {
        exhaustion = Math.min(exhaustion + amount, 40f);
    }

    public void eat(int nutrition, float saturationModifier) {
        foodLevel = Math.min(foodLevel + nutrition, 20);
        saturation = Math.min(saturation + nutrition * saturationModifier * 2.0f, foodLevel);
    }

    /** Advances one tick. Returns the healing (positive) or starvation damage (negative) to apply. */
    public float tick(float health, float maxHealth, boolean naturalRegen, int difficultyOrdinal) {
        if (exhaustion > 4.0f) {
            exhaustion -= 4.0f;
            if (saturation > 0f) {
                saturation = Math.max(saturation - 1.0f, 0f);
            } else if (difficultyOrdinal > 0) {
                foodLevel = Math.max(foodLevel - 1, 0);
            }
        }
        boolean hurt = health < maxHealth;
        if (naturalRegen && saturation > 0f && hurt && foodLevel >= 20) {
            tickTimer++;
            if (tickTimer >= 10) {
                float heal = Math.min(saturation, 6.0f);
                addExhaustion(heal);
                tickTimer = 0;
                return heal / 6.0f;
            }
        } else if (naturalRegen && foodLevel >= 18 && hurt) {
            tickTimer++;
            if (tickTimer >= 80) {
                addExhaustion(6.0f);
                tickTimer = 0;
                return 1.0f;
            }
        } else if (foodLevel <= 0) {
            tickTimer++;
            if (tickTimer >= 80) {
                tickTimer = 0;
                // Same rule as the game: on easy it stops at 10 health, on normal at 1, on hard it kills.
                if (health > 10.0f || difficultyOrdinal >= 3 || (health > 1.0f && difficultyOrdinal == 2)) {
                    return -1.0f;
                }
            }
        } else {
            tickTimer = 0;
        }
        return 0f;
    }
}
