package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.manhunt.Ring;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bots waiting in the ring (MANHUNT mode): they jump, crouch, spin, look at the closest player and
 * at their teammates. {@link dev.manhuntstar.manhunt.RingService} releases them when someone leaves the
 * ring and the countdown ends.
 */
public final class IdleBehavior {

    /** How far a ring bot looks at players. */
    private static final double WATCH_RANGE = 32.0;

    private final Bot bot;
    private final UUID ringId;
    private boolean active = true;
    private int behaviorType;
    private int behaviorTimer;
    private int behaviorLength;
    private int internalTick;

    public IdleBehavior(Bot bot, UUID ringId) {
        this.bot = bot;
        this.ringId = ringId;
        pickBehavior();
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
        GamePlayer watched = nearestPlayer();
        Vec3 focus = watched != null ? watched.position() : ring.center();
        double eyeHeight = watched != null ? watched.eyeHeight() : 1.6;
        executeBehavior(focus, eyeHeight);
        return true;
    }

    private GamePlayer nearestPlayer() {
        GamePlayer best = null;
        double bestDist = WATCH_RANGE * WATCH_RANGE;
        Vec3 p = bot.pos();
        for (GamePlayer pl : bot.world().players()) {
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

    private void pickBehavior() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        behaviorType = r.nextInt(6);
        behaviorLength = 60 + r.nextInt(40);
    }

    private void executeBehavior(Vec3 focus, double eyeHeight) {
        internalTick++;
        behaviorTimer++;
        if (behaviorTimer > behaviorLength) {
            behaviorTimer = 0;
            internalTick = 0;
            pickBehavior();
            bot.setSneaking(false);
        }
        Vec3 focusEyes = focus.add(0, eyeHeight, 0);
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
                Bot buddy = nearestTeammate();
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

    private Bot nearestTeammate() {
        for (Bot other : bot.manager().aliveBots()) {
            if (other != bot && other.world() == bot.world() && other.pos().distanceToSqr(bot.pos()) < 100.0) {
                return other;
            }
        }
        return null;
    }
}
