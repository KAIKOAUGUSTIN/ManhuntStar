package dev.manhuntstar.bot.ai;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bots esperando na roda em volta de quem os criou (modo MANHUNT): pulam, agacham, giram, olham
 * para o jogador e para os colegas. Sem contagem, soltam quando o jogador começa a correr para
 * longe; com contagem, ficam na roda até o tempo acabar (ou até o jogador sair, se configurado).
 */
public final class IdleBehavior {

    private static final int OUTSIDE_RELEASE_TICKS = 4;
    private static final double RUNNING_SPEED_SQR = 0.035;

    private final Bot bot;
    private final UUID creatorId;
    private final double radius;
    private final Vec3 creatorSpawn;
    private Vec3 creatorLastPos;
    private int outsideTimer;
    private boolean active = true;
    private int behaviorType;
    private int behaviorTimer;
    private int behaviorLength;
    private int internalTick;

    public IdleBehavior(Bot bot, Player creator, double radius) {
        this.bot = bot;
        this.creatorId = creator.getUniqueId();
        this.radius = radius;
        this.creatorSpawn = Vec3.of(creator.getLocation());
        this.creatorLastPos = creatorSpawn;
        pickBehavior();
    }

    public boolean isActive() {
        return active;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public void release() {
        if (active) {
            active = false;
            bot.setSneaking(false);
        }
    }

    /** @return true se o bot ficou parado na roda neste tick (a IA normal não roda). */
    public boolean tick() {
        if (!active) {
            return false;
        }
        boolean countdown = bot.manager().countdown().isRunning();
        Player creator = Bukkit.getPlayer(creatorId);
        if (creator == null || creator.isDead() || !creator.getWorld().equals(bot.world())) {
            if (countdown) {
                bot.motor().stopWalk();
                return true;
            }
            release();
            return false;
        }
        Vec3 creatorPos = Vec3.of(creator.getLocation());
        Vec3 creatorDelta = creatorPos.subtract(creatorLastPos);
        creatorLastPos = creatorPos;
        double dist = bot.pos().distanceTo(creatorPos);
        if (countdown) {
            if (dist > radius + 2.0) {
                outsideTimer++;
                if (bot.settings().releaseOnLeaveCircle.on() && outsideTimer >= OUTSIDE_RELEASE_TICKS) {
                    bot.manager().countdown().finishEarly(creator);
                    release();
                    return false;
                }
            } else {
                outsideTimer = 0;
            }
            executeBehavior(creator, creatorPos);
            return true;
        }
        if (shouldReleaseToChase(creatorPos, creatorDelta, dist)) {
            release();
            return false;
        }
        if (dist <= radius + 2.0) {
            outsideTimer = 0;
            executeBehavior(creator, creatorPos);
            return true;
        }
        outsideTimer++;
        bot.lookAt(creatorPos.add(0, creator.getEyeHeight(), 0));
        bot.motor().stopWalk();
        if (outsideTimer >= OUTSIDE_RELEASE_TICKS || dist > 32.0) {
            release();
            return false;
        }
        return true;
    }

    private boolean shouldReleaseToChase(Vec3 creatorPos, Vec3 creatorDelta, double distToCreator) {
        if (creatorDelta.horizontalLengthSqr() < RUNNING_SPEED_SQR) {
            return false;
        }
        double moved = creatorPos.distanceTo(creatorSpawn);
        double releaseDistance = Math.max(3.0, Math.min(radius * 0.75, 5.0));
        return moved >= releaseDistance || distToCreator > radius;
    }

    private void pickBehavior() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        behaviorType = r.nextInt(6);
        behaviorLength = 60 + r.nextInt(40);
    }

    private void executeBehavior(Player creator, Vec3 creatorPos) {
        internalTick++;
        behaviorTimer++;
        if (behaviorTimer > behaviorLength) {
            behaviorTimer = 0;
            internalTick = 0;
            pickBehavior();
            bot.setSneaking(false);
        }
        Vec3 creatorEyes = creatorPos.add(0, creator.getEyeHeight(), 0);
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
                bot.lookAt(creatorEyes);
                Vec3 dir = creatorPos.subtract(bot.pos()).normalize();
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
                bot.lookAt(creatorEyes);
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
