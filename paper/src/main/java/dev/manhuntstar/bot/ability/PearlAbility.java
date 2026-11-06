package dev.manhuntstar.bot.ability;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotDifficulty;
import dev.manhuntstar.bot.BotGroup;
import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.bot.Modes.PearlDimension;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Ender pearls: rescue (void/lava) and quick approach to the target (Terminator only). */
public final class PearlAbility {

    private final Bot bot;
    private int cooldown;

    public PearlAbility(Bot bot) {
        this.bot = bot;
    }

    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!bot.isAlive() || bot.capacityLocked() || !canUsePearl(bot)) {
            return;
        }
        BotDifficulty diff = bot.difficulty();
        if (shouldRescue()) {
            Pos safe = findSafeSpot();
            if (safe != null) {
                throwPearl(bot, Vec3.center(safe));
                setCooldown(diff, true);
            }
            return;
        }
        if (diff == BotDifficulty.TERMINATOR && isChasing()) {
            Player t = bot.target();
            if (t != null && t.getWorld().equals(bot.bukkitWorld()) && bot.distanceTo(t) > 15.0) {
                Vec3 dir = Locations.vec(t.getLocation()).subtract(bot.pos()).normalize();
                double throwDist = Math.max(5.0, bot.distanceTo(t) - 7.0);
                throwPearl(bot, Vec3.center(bot.pos().add(dir.scale(throwDist)).toPos()));
                setCooldown(diff, false);
            }
        }
    }

    /** A ring bot waits for the hunt to start and an escaper doesn't jump after the target. */
    private boolean isChasing() {
        if (bot.idle() != null && bot.idle().isActive()) {
            return false;
        }
        BotGroup g = bot.group();
        return g == null || g.role() != BotRole.ESCAPE;
    }

    /** Respects the {@code mobility.ender-pearls} and {@code mobility.pearl-dimension} options. */
    public static boolean canUsePearl(Bot bot) {
        if (!bot.settings().allowPearl.on() || bot.world() == null) {
            return false;
        }
        PearlDimension d = bot.settings().pearlDimension.get();
        Dimension env = bot.world().dimension();
        return switch (d) {
            case NETHER_ONLY -> env == Dimension.NETHER;
            case OVERWORLD_ONLY -> env == Dimension.NORMAL;
            case BOTH -> true;
        };
    }

    private boolean shouldRescue() {
        GameWorld w = bot.world();
        if (bot.pos().y() < w.minHeight() + 10) {
            return true;
        }
        if (bot.isInLava()) {
            return true;
        }
        return bot.velocity().y() < -0.5 && w.type(bot.blockPos().x(), bot.blockPos().y() - 3, bot.blockPos().z()) == Blocks.LAVA;
    }

    private Pos findSafeSpot() {
        GameWorld w = bot.world();
        Pos origin = bot.blockPos();
        for (int r = 2; r < 10; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    Pos target = origin.offset(x, 0, z);
                    for (int y = -5; y <= 5; y++) {
                        Pos check = target.above(y);
                        BlockType m = w.block(check).type();
                        if (!m.is(BlockTrait.AIR) && m != Blocks.LAVA && w.block(check.above()).type().is(BlockTrait.AIR)) {
                            return check.above();
                        }
                    }
                }
            }
        }
        return null;
    }

    private void setCooldown(BotDifficulty diff, boolean rescue) {
        int seconds = 60;
        if (rescue) {
            seconds = switch (diff) {
                case EASY -> 120;
                case MEDIUM -> 90;
                case HARD -> 60;
                case TERMINATOR -> 40;
            };
        }
        double mult = bot.progression().pearlCooldownMultiplier() * bot.settings().pearlCooldownMult.value();
        cooldown = (int) (seconds * 20 * mult);
    }

    /** Throws a pearl aimed at a point (like the player: looks, holds the pearl, throws with power 1.5). */
    public static void throwPearl(Bot bot, Vec3 target) {
        Mannequin me = bot.entity();
        if (me == null) {
            return;
        }
        bot.lookAt(target);
        ItemStack previous = bot.getItem(EquipmentSlot.HAND).clone();
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.ENDER_PEARL));
        Vec3 dir = target.subtract(bot.eyePos()).normalize();
        Vector vel = new Vector(dir.x(), dir.y() + 0.2, dir.z()).normalize().multiply(1.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        vel.add(new Vector(r.nextGaussian() * 0.0075, r.nextGaussian() * 0.0075, r.nextGaussian() * 0.0075).multiply(1.5));
        EnderPearl pearl = me.launchProjectile(EnderPearl.class, vel);
        pearl.setShooter(me);
        bot.swing();
        me.getWorld().playSound(me.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.NEUTRAL, 0.5f, 0.4f / (r.nextFloat() * 0.4f + 0.8f));
        Bukkit.getScheduler().runTask(bot.manager().plugin(), () -> {
            if (bot.isAlive()) {
                bot.setItem(EquipmentSlot.HAND, previous);
                bot.inventory().restorePreferredMainHand();
            }
        });
    }

    /** High-arc throw (used by the AI: escaping lava and offensive pearl). */
    public static void throwPearlArc(Bot bot, Vec3 target) {
        Mannequin me = bot.entity();
        if (me == null) {
            return;
        }
        Vec3 p = bot.pos();
        double dx = target.x() - p.x();
        double dz = target.z() - p.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y() - p.y() + horiz * 0.45;
        bot.lookAt(new Vec3(target.x(), p.y() + horiz * 0.45, target.z()));
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.ENDER_PEARL));
        Vector vel = new Vector(dx, dy, dz).normalize().multiply(1.5);
        EnderPearl pearl = me.launchProjectile(EnderPearl.class, vel);
        pearl.setShooter(me);
        me.getWorld().playSound(me.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        bot.swing();
        Bukkit.getScheduler().runTask(bot.manager().plugin(), () -> {
            if (bot.isAlive()) {
                bot.inventory().restorePreferredMainHand();
            }
        });
    }
}
