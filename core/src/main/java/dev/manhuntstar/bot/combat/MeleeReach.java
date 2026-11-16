package dev.manhuntstar.bot.combat;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Vec3;

/**
 * How far a bot's melee hit reaches, by the player's rules (the game's {@code Player.isWithinAttackRange}): from
 * the attacker's eyes to the nearest point of the target's hitbox, up to {@link #PLAYER_REACH} blocks.
 *
 * <p>Against a player the bot also has to reach the player's hitbox where it was {@link #VIEW_DELAY_TICKS}
 * ticks before. A person attacking sees the others that late (the game shows other players' movement a few
 * ticks behind), while the bot sees where the player really is: without this, a player running toward a bot was
 * hit as soon as they were 3 blocks away, before they could see the bot within reach and hit back. It never
 * reaches farther than the hitbox where the player is now.
 */
public final class MeleeReach {

    /** The player's reach for hits with a plain weapon (the {@code entity_interaction_range} attribute). */
    public static final double PLAYER_REACH = 3.0;
    /** How many ticks late a player's screen shows the other players (the game interpolates their movement over 3). */
    public static final int VIEW_DELAY_TICKS = 3;

    private MeleeReach() {
    }

    /** The hitbox is within {@code range} of the eyes. */
    public static boolean reaches(Vec3 eyes, Box hitbox, double range) {
        return hitbox.distanceTo(eyes) <= range;
    }

    /**
     * Against a player: reaches the hitbox where the player is now and also where they were
     * {@link #VIEW_DELAY_TICKS} ticks before ({@code seenAt}, the position then; null when it isn't known: the
     * player just appeared, changed world or teleported, and the bot doesn't hit yet).
     */
    public static boolean reachesPlayer(Vec3 eyes, Box hitbox, Vec3 position, Vec3 seenAt, double range) {
        return seenAt != null && reaches(eyes, hitbox, range) && reaches(eyes, hitbox.move(seenAt.subtract(position)), range);
    }
}
