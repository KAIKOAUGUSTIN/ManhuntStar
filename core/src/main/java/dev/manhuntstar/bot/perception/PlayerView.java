package dev.manhuntstar.bot.perception;

import dev.manhuntstar.platform.Effect;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;

/**
 * What the bots can notice about a player on one tick. {@code armor} and {@code particles} only matter for an
 * invisible player and are only read for one.
 */
public record PlayerView(UUID id, GameWorld world, Vec3 feet, double height, double eyeHeight, boolean sneaking,
                         boolean sprinting, boolean invisible, boolean armor, boolean particles) {

    private static final EquipSlot[] ARMOR = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};

    public static PlayerView of(GamePlayer p) {
        boolean invisible = p.isInvisible();
        boolean armor = false;
        boolean particles = false;
        if (invisible) {
            for (EquipSlot slot : ARMOR) {
                if (!p.item(slot).isEmpty()) {
                    armor = true;
                    break;
                }
            }
            particles = p.effectParticles(Effect.INVISIBILITY);
        }
        return new PlayerView(p.uuid(), p.world(), p.position(), p.height(), p.eyeHeight(), p.isSneaking(), p.isSprinting(),
                invisible, armor, particles);
    }

    public Vec3 eyes() {
        return feet.add(0, eyeHeight, 0);
    }

    public Vec3 chest() {
        return feet.add(0, Math.max(0.7, height * 0.5), 0);
    }

    /** Where the name shows, above the head. */
    public Vec3 nameTag() {
        return feet.add(0, height + 0.5, 0);
    }

    /** The body shows: the player isn't invisible, or is but wears armor (the game still draws the armor). */
    public boolean bodyShows() {
        return !invisible || armor;
    }
}
