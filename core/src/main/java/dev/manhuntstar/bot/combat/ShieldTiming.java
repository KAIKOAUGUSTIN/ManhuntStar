package dev.manhuntstar.bot.combat;

/**
 * When a bot's raised shield blocks and how long a hit that disables shields keeps it down, by the game's rules for
 * a player: the shield blocks once it has been up for {@link #BLOCK_DELAY_TICKS} ticks (its {@code blocks_attacks}
 * component's 0.25 s), and a hit with a weapon that disables blocking (an axe: 5 s, its {@code weapon} component)
 * takes it down for that long.
 */
final class ShieldTiming {

    /** Ticks a raised shield takes to block. */
    static final int BLOCK_DELAY_TICKS = 5;

    private long raisedAt;
    private long disabledUntil;

    /** The shield was raised on this tick. */
    void raised(long now) {
        raisedAt = now;
    }

    /** A shield raised (and not lowered since) blocks on this tick. */
    boolean blocks(long now) {
        return now - raisedAt >= BLOCK_DELAY_TICKS;
    }

    /** The shield was taken down and can't be raised on this tick. */
    boolean disabled(long now) {
        return now < disabledUntil;
    }

    /**
     * A hit with a weapon that disables blocking for {@code seconds}: the shield goes down for that long (the game
     * rounds it to ticks). Returns false for a weapon that doesn't disable it.
     */
    boolean disable(long now, double seconds) {
        long ticks = Math.round(seconds * 20.0);
        if (ticks <= 0) {
            return false;
        }
        disabledUntil = Math.max(disabledUntil, now + ticks);
        return true;
    }
}
