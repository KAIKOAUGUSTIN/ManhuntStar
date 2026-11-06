package dev.manhuntstar.bot.motor;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mannequin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The bot's locomotion. The server already simulates gravity, collision, stepping up, water, lava and ladders
 * for the Mannequin; here we only apply a player's "input" (walk/sprint/sneak acceleration,
 * jump with sprint boost, swimming) using the same formulas as the game, and detect horizontal collision.
 * The final velocity is applied once per tick, after the AI decided the movement.
 */
public final class Motor {

    private static final int GAP_PLACE_RETRY_WINDOW_TICKS = 16;
    private static final int GAP_PLACE_COOLDOWN_TICKS = 10;
    private static final int GAP_STUCK_THRESHOLD_TICKS = 24;

    private final Bot bot;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 lastApplied;
    private Vec3 lastPos;
    private boolean horizontalCollision;
    private boolean onGround;
    private int groundTicks;
    private int jumpTicks;
    private int noFallTicks = 60;
    private boolean sprinting;
    private boolean shouldBeSwimming;
    private boolean wasInWater;
    private boolean stalled;

    private Pos lastGapPlacePos;
    private int lastGapPlaceTick = -200;
    private int repeatedGapFailures;
    private Pos lastStuckPos;
    private int stuckTicks;

    public Motor(Bot bot) {
        this.bot = bot;
    }

    // ------------------------------------------------------------------ tick cycle

    /** Reads the state left by the server's physics on the previous tick. */
    public void beginTick() {
        Mannequin e = bot.entity();
        if (e == null) {
            return;
        }
        Vec3 pos = bot.pos();
        velocity = Locations.vec(e.getVelocity());
        // If the entity's physics didn't run (chunk not active yet), the velocity comes back the same as applied;
        // adding the acceleration again would make the bot "shoot off" when the physics came back.
        // (friction always changes the horizontal velocity when the physics runs; standing on the ground it's zero)
        stalled = lastApplied != null && lastApplied.horizontalLengthSqr() > 1.0E-8
                && velocity.x() == lastApplied.x() && velocity.z() == lastApplied.z();
        onGround = e.isOnGround();
        horizontalCollision = false;
        if (lastApplied != null && lastPos != null) {
            Vec3 moved = pos.subtract(lastPos);
            horizontalCollision = blockedAxis(lastApplied.x(), moved.x()) || blockedAxis(lastApplied.z(), moved.z());
        }
        if (jumpTicks > 0) {
            jumpTicks--;
        }
        if (noFallTicks > 0) {
            noFallTicks--;
        }
        groundTicks = onGround ? Math.min(groundTicks + 1, 100) : 0;
        boolean inWater = bot.isInWater();
        if (!inWater && !bot.isInLava()) {
            if (shouldBeSwimming) {
                shouldBeSwimming = false;
                bot.setSwimmingPose(false);
            }
            if (wasInWater && velocity.y() > 0) {
                velocity = velocity.withY(0);
            }
        }
        wasInWater = inWater;
    }

    private static boolean blockedAxis(double requested, double moved) {
        return Math.abs(requested) > 1.0E-3 && Math.abs(moved) < Math.abs(requested) * 0.5 - 1.0E-4;
    }

    /** Applies the velocity decided this tick to the entity. */
    public void endTick() {
        Mannequin e = bot.entity();
        if (e == null) {
            return;
        }
        if (!velocity.isFinite()) {
            velocity = Vec3.ZERO;
        }
        if (bot.isRiding()) {
            velocity = Vec3.ZERO;
            lastApplied = null;
        } else if (stalled) {
            // physics stopped: don't reapply (avoids building up) and wait for the chunk to tick again
            lastApplied = Locations.vec(e.getVelocity());
        } else {
            e.setVelocity(new Vector(velocity.x(), velocity.y(), velocity.z()));
            lastApplied = velocity;
            spawnSprintParticles(e);
        }
        lastPos = bot.pos();
    }

    private void spawnSprintParticles(Mannequin e) {
        if (!sprinting || !onGround || velocity.horizontalLengthSqr() < 0.01 || bot.isInWater()) {
            return;
        }
        if ((bot.aliveTicks() & 1) != 0) {
            return;
        }
        Block below = e.getLocation().subtract(0, 0.2, 0).getBlock();
        if (below.getType().isAir() || below.isLiquid()) {
            return;
        }
        Vec3 p = bot.pos();
        e.getWorld().spawnParticle(Particle.BLOCK, p.x(), p.y() + 0.1, p.z(), 1,
                -velocity.x() * 4.0, 1.5, -velocity.z() * 4.0, 0, below.getBlockData());
    }

    // ------------------------------------------------------------------ movement commands

    public Vec3 velocity() {
        return velocity;
    }

    public void setVelocity(Vec3 v) {
        velocity = v;
    }

    public void addVelocity(Vec3 v) {
        velocity = velocity.add(v);
    }

    public void addFriction(double factor) {
        velocity = new Vec3(velocity.x() * factor, velocity.y(), velocity.z() * factor);
    }

    public void stopWalk() {
        velocity = new Vec3(0, velocity.y(), 0);
    }

    public boolean onGround() {
        return onGround;
    }

    public boolean horizontalCollision() {
        return horizontalCollision;
    }

    public int groundTicks() {
        return groundTicks;
    }

    public int noFallTicks() {
        return noFallTicks;
    }

    public boolean sprinting() {
        return sprinting;
    }

    public void setSprinting(boolean s) {
        sprinting = s;
    }

    public boolean shouldBeSwimming() {
        return shouldBeSwimming;
    }

    public void setShouldBeSwimming(boolean v) {
        shouldBeSwimming = v;
    }

    /**
     * Walks in the (horizontal) direction like a player holding W: acceleration = speed * 0.216/friction³ on the
     * ground, 0.02 (0.026 sprinting) in the air and 0.02 in water/lava. Friction and gravity are applied by the server.
     */
    public void walk(Vec3 dir) {
        Vec3 horiz = dir.horizontal();
        double lenSq = horiz.lengthSqr();
        if (lenSq < 1.0E-4) {
            return;
        }
        double magnitude = Math.min(1.0, Math.sqrt(lenSq));
        horiz = horiz.normalize().scale(magnitude * 0.98);
        double base = 0.1 * speedEffectFactor();
        if (bot.isSneaking()) {
            base *= 0.3;
        } else if (sprinting) {
            base *= 1.3;
        }
        double accel;
        if (onGround) {
            float friction = blockFriction();
            accel = base * (0.21600002 / (friction * friction * friction));
        } else if (bot.isInWater() || bot.isInLava()) {
            accel = 0.02;
        } else {
            accel = sprinting ? 0.026 : 0.02;
        }
        velocity = velocity.add(horiz.x() * accel, 0, horiz.z() * accel);
    }

    private double speedEffectFactor() {
        Mannequin e = bot.entity();
        if (e == null) {
            return 1;
        }
        double f = 1;
        PotionEffect speed = e.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            f += 0.2 * (speed.getAmplifier() + 1);
        }
        PotionEffect slow = e.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow != null) {
            f -= 0.15 * (slow.getAmplifier() + 1);
        }
        return Math.max(0, f);
    }

    private float blockFriction() {
        Mannequin e = bot.entity();
        if (e == null) {
            return 0.6f;
        }
        Material below = e.getLocation().subtract(0, 0.5000001, 0).getBlock().getType();
        if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.FROSTED_ICE) {
            return 0.98f;
        }
        if (below == Material.BLUE_ICE) {
            return 0.989f;
        }
        if (below == Material.SLIME_BLOCK) {
            return 0.8f;
        }
        return 0.6f;
    }

    /** Jump: 0.42 + jump boost, with a 0.2 forward push when sprinting (like the player). */
    public void jump() {
        if (bot.isInWater() || bot.isInLava()) {
            swimUp();
            return;
        }
        if (isOnClimbable()) {
            velocity = velocity.withY(0.2);
            return;
        }
        if (groundTicks > 0 && jumpTicks == 0) {
            jumpTicks = 4;
            double power = 0.42;
            Mannequin e = bot.entity();
            if (e != null) {
                PotionEffect boost = e.getPotionEffect(PotionEffectType.JUMP_BOOST);
                if (boost != null) {
                    power += 0.1 * (boost.getAmplifier() + 1);
                }
            }
            velocity = velocity.withY(power);
            if (sprinting) {
                double yawRad = Math.toRadians(bot.yaw());
                velocity = velocity.add(-Math.sin(yawRad) * 0.2, 0, Math.cos(yawRad) * 0.2);
            }
            groundTicks = 0;
        }
    }

    /** Jump with a given velocity (used by parkour and abilities). */
    public void jump(Vec3 vel) {
        if (groundTicks > 1 && jumpTicks == 0) {
            jumpTicks = 4;
            velocity = vel;
        }
    }

    public void swimUp() {
        if (bot.isInWater() || bot.isInLava()) {
            velocity = velocity.add(0, 0.04, 0);
        }
    }

    /** Swims toward a point (3D), like the player swimming fast while looking at the target. */
    public void swimTowardNode(Vec3 waypoint) {
        Vec3 target = waypoint.add(0, 0.5, 0);
        Vec3 p = bot.pos();
        double dx = target.x() - p.x();
        double dy = target.y() - p.y();
        double dz = target.z() - p.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double total = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (total < 0.01) {
            return;
        }
        boolean fast = bot.isInWater() && sprinting && shouldBeSwimming;
        boolean dolphin = bot.entity() != null && bot.entity().hasPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        double accel = dolphin ? 0.03 : 0.02;
        if (horiz > 0.01) {
            velocity = velocity.add(dx / horiz * accel, 0, dz / horiz * accel);
        }
        boolean wantsUp = dy > 0.1;
        if (fast) {
            double lookY = dy / total;
            double mult = lookY < -0.2 ? 0.085 : 0.06;
            boolean headInWater = bot.isEyeInWater();
            if (lookY <= 0 || wantsUp || headInWater) {
                velocity = velocity.add(0, (lookY - velocity.y()) * mult, 0);
            }
        } else if (wantsUp) {
            velocity = velocity.add(0, 0.04, 0);
        }
    }

    /** Buoyancy adjustments the player makes by holding space near the surface. */
    public void tickWaterBuoyancy() {
        if (!bot.isInWater() || bot.pitch() >= 45f) {
            return;
        }
        boolean headInWater = bot.isEyeInWater();
        GameWorld w = bot.world();
        Vec3 p = bot.pos();
        boolean nearSurface = headInWater || WorldUtil.hasFluid(w, new Vec3(p.x(), p.y() + 0.15, p.z()).toPos());
        if (nearSurface) {
            boolean holdUp = (Bukkit.getCurrentTick() % 12) < 6;
            if (holdUp && velocity.y() < 0.05) {
                velocity = velocity.add(0, 0.028, 0);
            } else if (!holdUp && velocity.y() > 0) {
                velocity = velocity.withY(velocity.y() * 0.55);
            }
        } else if (velocity.y() < 0.05) {
            velocity = velocity.add(0, 0.04, 0);
        }
        if (horizontalCollision) {
            if (isOnClimbable()) {
                velocity = velocity.withY(0.2);
            } else if (WorldUtil.isWater(w, bot.blockPos().above()) || velocity.y() > 0 || bot.lookVector().y() > 0) {
                velocity = velocity.withY(0.3);
            }
        }
    }

    public boolean isOnClimbable() {
        GameWorld w = bot.world();
        return w != null && WorldUtil.isClimbable(w, bot.blockPos());
    }

    public void resetFallTracking() {
        Mannequin e = bot.entity();
        if (e != null) {
            e.setFallDistance(0);
        }
    }

    public void onRespawn() {
        velocity = Vec3.ZERO;
        lastApplied = null;
        lastPos = null;
        noFallTicks = 60;
        groundTicks = 0;
        jumpTicks = 0;
        shouldBeSwimming = false;
    }

    // ------------------------------------------------------------------ obstacles and holes

    /** 1-block step ahead with room above: jumps. */
    public void handleObstacles() {
        GameWorld w = bot.world();
        Pos front = Locations.relative(bot.blockPos(), bot.facing());
        if (WorldUtil.isTrulySolid(w, front) && !WorldUtil.isTrulySolid(w, front.above()) && onGround) {
            sprinting = true;
            jump();
        }
    }

    public boolean shouldHandleGapAhead() {
        GameWorld w = bot.world();
        Pos ahead = Locations.relative(bot.blockPos(), bot.facing());
        if (WorldUtil.isTrulySolid(w, ahead) && !WorldUtil.isTrulySolid(w, ahead.above())) {
            return false;
        }
        return !WorldUtil.isTrulySolid(w, ahead.below());
    }

    /** Hole ahead: jumps if there is ground within 5 blocks, otherwise places a block (only near the target). */
    public void handleGaps() {
        GameWorld w = bot.world();
        int now = Bukkit.getCurrentTick();
        Pos current = bot.blockPos();
        BlockFace facing = bot.facing();
        Pos ahead = Locations.relative(current, facing);
        Pos placePos = ahead.below();
        if (handleGapLoopEscape(now, current)) {
            return;
        }
        if (!shouldHandleGapAhead()) {
            repeatedGapFailures = 0;
            return;
        }
        if (!bot.brain().canPlaceToweringBlocksNearTarget()) {
            repeatedGapFailures = 0;
            return;
        }
        if (WorldUtil.isTrulySolid(w, ahead) && !WorldUtil.isTrulySolid(w, ahead.above())) {
            handleObstacles();
            return;
        }
        boolean canJump = false;
        for (int i = 2; i <= 5; i++) {
            Pos landing = Locations.relative(current, facing, i);
            boolean hasLanding = WorldUtil.isTrulySolid(w, landing.below()) || WorldUtil.isTrulySolid(w, landing.below(2));
            if (hasLanding && WorldUtil.hasBodySpace(w, landing)) {
                canJump = true;
                break;
            }
        }
        if (canJump) {
            sprinting = true;
            if (onGround) {
                jump();
            }
            repeatedGapFailures = 0;
            return;
        }
        if (!onGround) {
            return;
        }
        if (!WorldUtil.canBeReplaced(w, placePos) || WorldUtil.hasFluid(w, placePos)) {
            repeatedGapFailures++;
            return;
        }
        if (!WorldUtil.hasBodySpace(w, ahead)) {
            repeatedGapFailures++;
            return;
        }
        if (lastGapPlacePos != null && lastGapPlacePos.equals(placePos) && now - lastGapPlaceTick <= GAP_PLACE_RETRY_WINDOW_TICKS) {
            repeatedGapFailures++;
            return;
        }
        if (now - lastGapPlaceTick < GAP_PLACE_COOLDOWN_TICKS) {
            return;
        }
        lastGapPlacePos = placePos;
        lastGapPlaceTick = now;
        repeatedGapFailures = 0;
        bot.setPitchNow(90f);
        bot.placeBlock(placePos, Material.COBBLESTONE, false);
        velocity = velocity.scale(0.5);
    }

    private boolean handleGapLoopEscape(int now, Pos current) {
        if (lastStuckPos != null && lastStuckPos.equals(current)) {
            stuckTicks++;
        } else {
            lastStuckPos = current;
            stuckTicks = 0;
        }
        boolean recentlyTried = now - lastGapPlaceTick <= 30 || repeatedGapFailures > 1;
        if (recentlyTried && stuckTicks >= GAP_STUCK_THRESHOLD_TICKS) {
            sprinting = false;
            stopWalk();
            velocity = velocity.scale(0.35);
            repeatedGapFailures = 0;
            stuckTicks = 0;
            lastGapPlacePos = null;
            lastGapPlaceTick = now;
            return true;
        }
        return false;
    }

    /** Stops walking if there is no ground right ahead. */
    public void avoidFalls() {
        Vec3 fwd = bot.forwardVector().scale(1.1);
        Pos below = bot.pos().add(fwd).add(0, -1, 0).toPos();
        if (!WorldUtil.isTrulySolid(bot.world(), below)) {
            stopWalk();
        }
    }
}
