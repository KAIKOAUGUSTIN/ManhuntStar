package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.manhunt.Ring;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;

/**
 * Bots waiting in the ring (MANHUNT mode): they jump, crouch, spin, look at the closest player and
 * at their teammates ({@link BotAnimations}). {@link dev.manhuntstar.manhunt.RingService} releases them
 * when someone leaves the ring and the countdown ends.
 */
public final class IdleBehavior {

    private final Bot bot;
    private final UUID ringId;
    private final BotAnimations animations;
    private boolean active = true;

    public IdleBehavior(Bot bot, UUID ringId) {
        this.bot = bot;
        this.ringId = ringId;
        this.animations = new BotAnimations(bot, BotAnimations.Mood.PARTY);
    }

    public boolean isActive() {
        return active;
    }

    public UUID ringId() {
        return ringId;
    }

    /** Leaves the ring for good: the rest of the plugin no longer treats the bot as "waiting". */
    public void release() {
        if (active) {
            active = false;
            bot.setSneaking(false);
        }
        if (bot.idle() == this) {
            bot.setIdle(null);
        }
    }

    /** @return true if the bot stayed still in the ring this tick (the normal AI doesn't run). */
    public boolean tick() {
        if (!active) {
            return false;
        }
        Ring ring = bot.manager().rings().ring(ringId);
        if (ring == null) {
            // the ring ended without going through this bot
            release();
            return false;
        }
        GamePlayer watched = BotAnimations.nearestPlayer(bot);
        Vec3 focus = watched != null ? watched.position() : ring.center();
        double eyeHeight = watched != null ? watched.eyeHeight() : 1.6;
        animations.tick(focus, eyeHeight);
        return true;
    }
}
