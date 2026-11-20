package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Vec3;

/**
 * A bot after the end of the match, until the next one: it stays where it is, doesn't hunt or fight, and
 * celebrates when the bots won (the party of the ring) or mopes when they lost ({@link BotAnimations}).
 */
public final class EndBehavior {

    private final Bot bot;
    private final BotAnimations animations;

    public EndBehavior(Bot bot, boolean botsWon) {
        this.bot = bot;
        this.animations = new BotAnimations(bot, botsWon ? BotAnimations.Mood.PARTY : BotAnimations.Mood.GLOOM);
    }

    public void tick() {
        GamePlayer watched = BotAnimations.nearestPlayer(bot);
        Vec3 focus;
        double eyeHeight;
        if (watched != null) {
            focus = watched.position();
            eyeHeight = watched.eyeHeight();
        } else {
            // nobody around: a point ahead of the bot, at its own height
            double yaw = Math.toRadians(bot.yaw());
            focus = bot.pos().add(-Math.sin(yaw) * 3.0, 0, Math.cos(yaw) * 3.0);
            eyeHeight = 1.6;
        }
        animations.tick(focus, eyeHeight);
    }
}
