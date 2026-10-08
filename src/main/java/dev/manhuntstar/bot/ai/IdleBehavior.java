package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.manhunt.Ring;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;

/**
 * Bots esperando na roda (modo MANHUNT): pulam, agacham, giram, olham para o jogador mais perto e
 * para os colegas. Quem solta é a {@link dev.manhuntstar.manhunt.RingService}, quando alguém sai da
 * roda e a contagem acaba.
 */
public final class IdleBehavior {

    /** Distância até onde o bot da roda olha para jogadores. */
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

    /** Sai da roda de vez: o bot deixa de ser tratado como "esperando" pelo resto do plugin. */
    public void release() {
        if (active) {
            active = false;
            bot.setSneaking(false);
        }
        if (bot.idle() == this) {
            bot.setIdle(null);
        }
    }

    /** @return true se o bot ficou parado na roda neste tick (a IA normal não roda). */
    public boolean tick() {
        if (!active) {
            return false;
        }
        Ring ring = bot.manager().rings().ring(ringId);
        if (ring == null) {
            // a roda acabou sem passar por este bot
            release();
            return false;
        }
        Player watched = nearestPlayer();
        Vec3 focus = watched != null ? Vec3.of(watched.getLocation()) : ring.center();
        double eyeHeight = watched != null ? watched.getEyeHeight() : 1.6;
        executeBehavior(focus, eyeHeight);
        return true;
    }

    private Player nearestPlayer() {
        Player best = null;
        double bestDist = WATCH_RANGE * WATCH_RANGE;
        Vec3 p = bot.pos();
        for (Player pl : bot.world().getPlayers()) {
            if (!BotManager.isActivePlayer(pl)) {
                continue;
            }
            double d = p.distanceToSqr(Vec3.of(pl.getLocation()));
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
