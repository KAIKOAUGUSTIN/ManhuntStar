package dev.manhuntstar.bot.world;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.paper.PaperBlock;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Block breaking by the bot, with time, crack animation, sound and drops like a player. */
public final class Mining {

    /** Maximum reach to keep breaking (the player's is 4.5 from the eyes). */
    private static final double MAX_REACH = 6.0;

    private final Bot bot;
    private Pos miningPos;
    private int ticksRemaining;
    private int totalTicks;

    public Mining(Bot bot) {
        this.bot = bot;
    }

    public boolean isMining() {
        return miningPos != null;
    }

    public Pos miningPos() {
        return miningPos;
    }

    public void tick() {
        if (bot.capacityLocked() || miningPos == null || bot.entity() == null) {
            return;
        }
        GameWorld w = bot.world();
        GameBlock b = w.block(miningPos);
        if (b.type().is(BlockTrait.AIR)) {
            clear(false);
            return;
        }
        if (bot.eyePos().distanceTo(Vec3.center(miningPos)) > MAX_REACH) {
            // teleported (/manhunt tp, pearl, redistribution) in the middle of breaking: doesn't break from afar
            clear(true);
            return;
        }
        bot.lookAtBlock(miningPos);
        equipBestTool(b);
        if (b.type() == Blocks.BEDROCK || b.type().hardness() < 0) {
            clear(true);
            return;
        }
        ticksRemaining--;
        int progress = 9 - Math.max(0, ticksRemaining * 9 / Math.max(1, totalTicks));
        sendCrack(w, miningPos, progress / 10.0f);
        if (ticksRemaining % 6 == 0) {
            w.playSound(Vec3.center(miningPos), b.soundGroup().hitSound(), dev.manhuntstar.platform.SoundCategory.BLOCKS, 0.8f, 1.0f);
            bot.swing();
        }
        if (ticksRemaining <= 0) {
            ItemStack tool = bot.getItem(EquipmentSlot.HAND);
            ((PaperBlock) b).bukkit().breakNaturally(tool, true);
            sendCrack(w, miningPos, 0f);
            bot.manager().plugin().pathService().invalidate(w, miningPos);
            miningPos = null;
            bot.inventory().restorePreferredMainHand();
        }
    }

    private void sendCrack(GameWorld w, Pos pos, float progress) {
        Location loc = new Location(PaperWorld.bukkit(w), pos.x(), pos.y(), pos.z());
        int id = bot.entity() == null ? bot.id().hashCode() : bot.entity().getEntityId();
        for (Player p : PaperWorld.bukkit(w).getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 64 * 64) {
                p.sendBlockDamage(loc, progress, id);
            }
        }
    }

    /** Stops breaking (for example when taking knockback or when controlled by an ability). */
    public void cancel() {
        if (miningPos != null && bot.world() != null) {
            sendCrack(bot.world(), miningPos, 0f);
        }
        miningPos = null;
        ticksRemaining = 0;
    }

    private void clear(boolean resetCrack) {
        if (resetCrack && miningPos != null) {
            sendCrack(bot.world(), miningPos, 0f);
        }
        miningPos = null;
        ticksRemaining = 0;
    }

    public void start(Pos pos) {
        if (startNoStop(pos)) {
            bot.motor().stopWalk();
        }
    }

    public boolean startNoStop(Pos pos) {
        GameWorld w = bot.world();
        if (w == null || !WorldUtil.isLoaded(w, pos)) {
            return false;
        }
        GameBlock b = w.block(pos);
        if (b.type().is(BlockTrait.AIR) || b.type() == Blocks.BEDROCK || b.type().hardness() < 0) {
            return false;
        }
        miningPos = pos;
        totalTicks = breakTimeTicks(b);
        ticksRemaining = totalTicks;
        return true;
    }

    /** The mod's break time: hardness * 30 / speed (8 with the right tool) * 2, at least 5 ticks. */
    private static int breakTimeTicks(GameBlock b) {
        float hardness = b.type().hardness();
        if (hardness < 0) {
            return Integer.MAX_VALUE;
        }
        if (hardness == 0) {
            return 1;
        }
        BlockType m = b.type();
        float toolSpeed = (m.is(BlockTrait.MINEABLE_PICKAXE) || m.is(BlockTrait.MINEABLE_AXE) || m.is(BlockTrait.MINEABLE_SHOVEL)) ? 8f : 1f;
        float ticks = hardness * 30f / toolSpeed * 2.0f;
        return Math.max(5, (int) Math.ceil(ticks));
    }

    private void equipBestTool(GameBlock b) {
        BlockType m = b.type();
        boolean toolBlock = m.is(BlockTrait.MINEABLE_PICKAXE) || m.is(BlockTrait.MINEABLE_SHOVEL) || m.is(BlockTrait.MINEABLE_AXE) || m.is(BlockTrait.MINEABLE_HOE);
        ItemStack tool = toolBlock ? bot.inventory().preferredPickaxe() : bot.inventory().preferredMainHand();
        if (!tool.isEmpty() && !tool.isSimilar(bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, tool);
        }
    }

    private boolean solid(Pos p) {
        return WorldUtil.isTrulySolid(bot.world(), p);
    }

    private Pos offsetToward(Pos origin, int sx, int sz) {
        if (sx == 0 && sz == 0) {
            return Locations.relative(origin, bot.facing());
        }
        return origin.offset(sx, 0, sz);
    }

    private boolean tryMineFirstSolid(Pos... candidates) {
        for (Pos p : candidates) {
            if (p != null && solid(p)) {
                bot.lookAtBlock(p);
                start(p);
                return true;
            }
        }
        return false;
    }

    /** "Stuck" mode: digs toward the target (staircase down, up or zigzag). */
    public void executeHardcodedMining(Player target) {
        // The breaking itself advances in tick(), called once per tick by Bot.
        if (miningPos != null) {
            return;
        }
        Pos botPos = bot.blockPos();
        Pos targetPos = Locations.pos(target.getLocation());
        double yDiff = targetPos.y() - botPos.y();
        int dx = targetPos.x() - botPos.x();
        int dz = targetPos.z() - botPos.z();
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        double horizontalDist = Math.hypot(dx, dz);
        bot.inventory().restorePreferredPickaxe();
        bot.lookAt(Locations.vec(target.getLocation()));
        if (yDiff < -1.5) {
            Pos toward = offsetToward(botPos, sx, sz);
            Pos downPrimary = Math.abs(dx) >= Math.abs(dz) ? botPos.offset(sx, -1, 0) : botPos.offset(0, -1, sz);
            Pos downSecondary = Math.abs(dx) >= Math.abs(dz) ? botPos.offset(0, -1, sz) : botPos.offset(sx, -1, 0);
            for (Pos p : new Pos[]{toward.below(), downPrimary, downSecondary, botPos.below(), toward}) {
                if (solid(p)) {
                    bot.setPitchNow(90f);
                    bot.lookAtBlock(p);
                    start(p);
                    return;
                }
            }
            Vec3 towardVec = new Vec3(dx, 0, dz);
            if (towardVec.lengthSqr() > 1.0E-4) {
                bot.motor().walk(towardVec.normalize());
                bot.motor().setSprinting(true);
            } else {
                bot.motor().stopWalk();
            }
            return;
        }
        if (yDiff >= 2.0) {
            Pos toward = offsetToward(botPos, sx, sz);
            Pos head = botPos.above();
            if (tryMineFirstSolid(toward.above(2), toward.above(), head.above(), head, toward)) {
                bot.setPitchNow(-90f);
                return;
            }
            if (horizontalDist > 5.0) {
                Vec3 towardVec = new Vec3(dx, 0, dz);
                if (towardVec.lengthSqr() > 1.0E-4) {
                    bot.lookAt(Locations.vec(target.getLocation()));
                    bot.motor().walk(towardVec.normalize());
                    bot.motor().setSprinting(true);
                    if (bot.horizontalCollision() && bot.onGround()) {
                        bot.motor().jump();
                    }
                } else {
                    bot.motor().stopWalk();
                }
                return;
            }
            bot.setPitchNow(-90f);
            bot.motor().stopWalk();
            if (bot.onGround()) {
                bot.motor().jump();
                placePillarSupport(botPos);
            }
            return;
        }
        if (Math.abs(dx) > 0 && Math.abs(dz) > 0) {
            Pos[] zigzag = {botPos.offset(sx, 1, 0), botPos.offset(sx, 0, 0), botPos.offset(0, 1, sz), botPos.offset(0, 0, sz), botPos.offset(sx, 1, sz), botPos.offset(sx, 0, sz)};
            for (Pos p : zigzag) {
                if (solid(p)) {
                    bot.lookAtBlock(p);
                    start(p);
                    return;
                }
            }
            bot.motor().walk(new Vec3(sx, 0, sz).normalize());
            bot.motor().setSprinting(true);
        } else {
            Pos front = Locations.relative(botPos, bot.facing());
            if (solid(front.above())) {
                start(front.above());
                return;
            }
            if (solid(front)) {
                start(front);
                return;
            }
            bot.motor().walk(bot.lookVector().normalize());
            bot.motor().setSprinting(true);
        }
        if (bot.onGround() && WorldUtil.blocksMotion(bot.world(), botPos.above(2))) {
            bot.motor().jump();
        } else if (bot.horizontalCollision() && bot.onGround()) {
            bot.motor().jump();
        }
    }

    /** Digs to the player when they're above/below/ahead (used when the A* has no path). */
    public void handleMiningTowards(Player target) {
        if (bot.isInWater() || miningPos != null) {
            return;
        }
        Pos botPos = bot.blockPos();
        Pos player = Locations.pos(target.getLocation());
        bot.inventory().restorePreferredPickaxe();
        Pos front = Locations.relative(botPos, bot.facing());
        if (player.y() > botPos.y() + 1) {
            int sx = Integer.signum(player.x() - botPos.x());
            int sz = Integer.signum(player.z() - botPos.z());
            Pos toward = offsetToward(botPos, sx, sz);
            if (tryMineFirstSolid(toward.above(2), toward.above(), botPos.above(2), botPos.above(), toward)) {
                bot.setPitchNow(-90f);
                return;
            }
            bot.motor().stopWalk();
            if (bot.onGround()) {
                bot.setPitchNow(-90f);
                bot.motor().jump();
                placePillarSupport(botPos);
            }
            return;
        }
        if (player.y() >= botPos.y()) {
            if (solid(front.above())) {
                start(front.above());
                return;
            }
            if (solid(front)) {
                start(front);
                return;
            }
        }
        if (player.y() < botPos.y()) {
            if (Math.abs(player.x() - botPos.x()) <= 1 && Math.abs(player.z() - botPos.z()) <= 1 && solid(botPos.below())) {
                start(botPos.below());
                return;
            }
            if (solid(front.below())) {
                start(front.below());
                return;
            }
            if (solid(front)) {
                start(front);
            }
        }
    }

    /** After the jump, places a block where the feet were (pillar) when they pass the top of the block. */
    private void placePillarSupport(Pos feet) {
        if (!bot.brain().canPlaceToweringBlocksNearTarget() || bot.hasPendingPillar()) {
            return;
        }
        if (WorldUtil.canBeReplaced(bot.world(), feet) && solid(feet.below())) {
            bot.queuePillarBlock(feet, null);
        }
    }
}
