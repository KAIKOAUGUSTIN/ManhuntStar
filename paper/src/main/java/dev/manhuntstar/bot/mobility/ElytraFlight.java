package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;

/**
 * Elytra ({@code mobility.elytra} option, off by default): with the target more than 80 blocks away and open sky, the bot
 * puts on the elytra, jumps, opens the wings and uses fireworks to climb to a high point near the target;
 * lands 12 blocks from the point or 30 from the target and gives back the chestplate.
 */
public final class ElytraFlight {

    private static final int BOOST_TICKS = 30;

    private final Bot bot;
    private boolean pathing;
    private int launchDelay = -1;
    private Vec3 flightTarget;
    private GameItem savedChest = GameItem.empty();
    private int fireworkCooldown;
    private int boostTicks;

    public ElytraFlight(Bot bot) {
        this.bot = bot;
    }

    public boolean isFlying() {
        return bot.entity() != null && bot.entity().isGliding();
    }

    public void tick(GamePlayer target) {
        if (target == null || !bot.settings().useElytra.on() || !target.world().equals(bot.world())) {
            forceLand();
            return;
        }
        if (fireworkCooldown > 0) {
            fireworkCooldown--;
        }
        tickBoost();
        double dist = bot.distanceTo(target);
        if (isFlying()) {
            tickFlight(target, dist);
        } else {
            tickGround(target, dist);
        }
    }

    private void tickGround(GamePlayer target, double dist) {
        if (bot.getItem(EquipSlot.CHEST).type() == Items.ELYTRA && !pathing) {
            restoreChest();
            return;
        }
        if (dist <= 80.0 || WorldUtil.skyLight(bot.world(), bot.blockPos()) < 15) {
            if (!pathing) {
                launchDelay = -1;
            }
            if (pathing && launchDelay < 0) {
                pathing = false;
                restoreChest();
            }
            return;
        }
        if (!pathing) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vec3 dir = target.position().subtract(bot.pos()).horizontal().normalize();
            Vec3 perp = new Vec3(-dir.z(), 0, dir.x()).normalize();
            double forward = 45 + r.nextDouble() * 35;
            double lateral = (10 + r.nextDouble() * 40) * (r.nextBoolean() ? 1 : -1);
            Vec3 base = bot.pos().add(dir.scale(forward)).add(perp.scale(lateral));
            flightTarget = new Vec3(base.x(), target.position().y() + 10 + r.nextDouble() * 140, base.z());
            pathing = true;
            launchDelay = 3;
            GameItem chest = bot.getItem(EquipSlot.CHEST);
            savedChest = chest.type() == Items.ELYTRA ? GameItem.empty() : chest.copy();
            bot.setItem(EquipSlot.CHEST, GameItem.of(Items.ELYTRA));
            bot.motor().jump();
            return;
        }
        if (launchDelay > 0) {
            launchDelay--;
            return;
        }
        if (launchDelay == 0) {
            launchDelay = -1;
            bot.motor().setVelocity(bot.velocity().withY(0.5));
            BotBody e = bot.entity();
            if (e != null) {
                e.setGliding(true);
            }
            fireFirework();
        }
    }

    private void tickFlight(GamePlayer target, double dist) {
        if (!pathing || flightTarget == null) {
            land();
            return;
        }
        if (bot.pos().distanceTo(flightTarget) < 12.0 || dist < 30.0) {
            land();
            return;
        }
        Vec3 to = flightTarget.subtract(bot.pos()).normalize();
        if (!WorldUtil.clearLine(bot.world(), bot.eyePos(), bot.eyePos().add(to.scale(12)))) {
            to = new Vec3(to.x(), 1.2, to.z()).normalize();
        }
        bot.lookAt(bot.eyePos().add(to));
        Vec3 v = bot.velocity();
        boolean stalling = v.horizontalLength() < 0.5 || v.y() < -0.4;
        boolean needsAltitude = flightTarget.y() > bot.pos().y() + 5;
        if (fireworkCooldown <= 0 && (stalling || needsAltitude)) {
            fireFirework();
        }
    }

    /** Firework boost as in the game: v += look*0.1 + (look*1.5 - v)*0.5 per tick. */
    private void tickBoost() {
        if (boostTicks <= 0) {
            return;
        }
        boostTicks--;
        if (!isFlying()) {
            boostTicks = 0;
            return;
        }
        Vec3 look = bot.lookVector();
        Vec3 v = bot.velocity();
        bot.motor().setVelocity(v.add(look.x() * 0.1 + (look.x() * 1.5 - v.x()) * 0.5,
                look.y() * 0.1 + (look.y() * 1.5 - v.y()) * 0.5,
                look.z() * 0.1 + (look.z() * 1.5 - v.z()) * 0.5));
        if ((boostTicks & 1) == 0) {
            Vec3 p = bot.pos();
            bot.world().spawnParticle(dev.manhuntstar.platform.Particle.FIREWORK, p, 1, 0, 0, 0, 0.02);
        }
    }

    private void fireFirework() {
        GameItem previous = bot.getItem(EquipSlot.HAND).copy();
        bot.setItem(EquipSlot.HAND, GameItem.of(Items.FIREWORK_ROCKET));
        bot.swing();
        bot.world().playSound(bot.pos(), Sounds.ENTITY_FIREWORK_ROCKET_LAUNCH, dev.manhuntstar.platform.SoundCategory.AMBIENT, 3f, 1f);
        boostTicks = BOOST_TICKS;
        fireworkCooldown = 60;
        bot.setItem(EquipSlot.HAND, previous);
    }

    private void land() {
        pathing = false;
        launchDelay = -1;
        boostTicks = 0;
        BotBody e = bot.entity();
        if (e != null && e.isGliding()) {
            e.setGliding(false);
        }
        restoreChest();
    }

    private void forceLand() {
        pathing = false;
        launchDelay = -1;
        flightTarget = null;
        boostTicks = 0;
        BotBody e = bot.entity();
        if (e != null && e.isGliding()) {
            e.setGliding(false);
        }
        if (bot.getItem(EquipSlot.CHEST).type() == Items.ELYTRA || !savedChest.isEmpty()) {
            restoreChest();
        }
    }

    private void restoreChest() {
        if (bot.getItem(EquipSlot.CHEST).type() != Items.ELYTRA && savedChest.isEmpty()) {
            return;
        }
        GameItem restore = savedChest.isEmpty() ? bot.progression().chestplateForPhase() : savedChest;
        bot.setItem(EquipSlot.CHEST, restore);
        savedChest = GameItem.empty();
    }
}
