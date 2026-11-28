package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animations of a bot standing in place, one after the other at random: the party of the ring and of the
 * bots that won the match (they jump, crouch, spin, wave at the sky and look at the closest player and at
 * their teammates) and the gloom of the bots that lost it (head down, slow crouches, shaking the head).
 */
public final class BotAnimations {

    /** Which set of animations. */
    public enum Mood {
        PARTY(6), GLOOM(4);

        private final int count;

        Mood(int count) {
            this.count = count;
        }
    }

    /** How far a bot looks at players. */
    static final double WATCH_RANGE = 32.0;

    private final Bot bot;
    private final Mood mood;
    private int behaviorType;
    private int behaviorTimer;
    private int behaviorLength;
    private int internalTick;

    public BotAnimations(Bot bot, Mood mood) {
        this.bot = bot;
        this.mood = mood;
        pickBehavior();
    }

    /** One tick of the current animation; {@code focus} is what the bot looks at (a player's feet). */
    public void tick(Vec3 focus, double eyeHeight) {
        internalTick++;
        behaviorTimer++;
        if (behaviorTimer > behaviorLength) {
            behaviorTimer = 0;
            internalTick = 0;
            pickBehavior();
            bot.setSneaking(false);
        }
        Vec3 focusEyes = focus.add(0, eyeHeight, 0);
        if (mood == Mood.PARTY) {
            party(focus, focusEyes);
        } else {
            gloom(focusEyes);
        }
    }

    private void pickBehavior() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        behaviorType = r.nextInt(mood.count);
        behaviorLength = 60 + r.nextInt(40);
    }

    private void party(Vec3 focus, Vec3 focusEyes) {
        switch (behaviorType) {
            case 0 -> {
                if (internalTick == 1 && bot.onGround()) {
                    bot.motor().jump();
                }
                if (internalTick == 2) {
                    bot.setSneaking(true);
                }
                if (internalTick == 6) {
                    bot.setSneaking(false);
                }
                bot.motor().stopWalk();
                bot.addYaw(5f);
            }
            case 1 -> {
                Bot buddy = nearestTeammate(bot);
                if (buddy != null) {
                    bot.lookAt(buddy.eyePos());
                }
                bot.motor().stopWalk();
                if (internalTick % 10 == 0) {
                    bot.setPitchNow(-90f);
                    bot.swing();
                }
            }
            case 2 -> {
                bot.setSneaking(true);
                bot.lookAt(focusEyes);
                Vec3 dir = focus.subtract(bot.pos()).normalize();
                bot.motor().walk(dir.scale((internalTick / 20) % 2 == 0 ? 0.3 : -0.3));
            }
            case 3 -> {
                bot.setSneaking(true);
                bot.addYaw(8f);
                bot.motor().stopWalk();
            }
            case 4 -> {
                if (bot.onGround() && internalTick % 15 == 0) {
                    bot.motor().jump();
                }
                bot.motor().stopWalk();
                bot.setPitchNow(-90f);
                if (internalTick % 5 == 0) {
                    bot.swing();
                }
            }
            default -> {
                bot.setSneaking(false);
                bot.lookAt(focusEyes);
                bot.motor().stopWalk();
            }
        }
    }

    private void gloom(Vec3 focusEyes) {
        bot.motor().stopWalk();
        switch (behaviorType) {
            case 0 -> {
                // head down, standing
                bot.setSneaking(false);
                bot.setPitchNow(75f);
            }
            case 1 -> {
                // shakes the head ("no...")
                bot.setPitchNow(35f);
                bot.addYaw((internalTick / 6) % 2 == 0 ? 6f : -6f);
            }
            case 2 -> {
                // slow crouches with the head down
                bot.setPitchNow(60f);
                bot.setSneaking((internalTick / 20) % 2 == 0);
            }
            default -> {
                // looks at the closest player for a moment, then down
                if (internalTick < 30) {
                    bot.lookAt(focusEyes);
                } else {
                    bot.setPitchNow(70f);
                }
            }
        }
    }

    /** The closest active player (online, alive, not a spectator) within range, or null. */
    static GamePlayer nearestPlayer(Bot bot) {
        GameWorld w = bot.world();
        if (w == null) {
            return null;
        }
        GamePlayer best = null;
        double bestDist = WATCH_RANGE * WATCH_RANGE;
        Vec3 p = bot.pos();
        for (GamePlayer pl : w.players()) {
            if (!BotManager.isActivePlayer(pl)) {
                continue;
            }
            double d = p.distanceToSqr(pl.position());
            if (d < bestDist) {
                bestDist = d;
                best = pl;
            }
        }
        return best;
    }

    private static Bot nearestTeammate(Bot bot) {
        for (Bot other : bot.manager().aliveBots()) {
            if (other != bot && other.world() == bot.world() && other.pos().distanceToSqr(bot.pos()) < 100.0) {
                return other;
            }
        }
        return null;
    }
}
