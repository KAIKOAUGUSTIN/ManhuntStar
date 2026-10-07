package dev.manhuntstar.bot.motor;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mannequin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Locomoção do bot. O servidor já simula gravidade, colisão, subir degraus, água, lava e escadas
 * para o Mannequin; aqui só aplicamos a "entrada" de um jogador (aceleração de andar/correr/agachar,
 * pulo com impulso de corrida, nado) usando as mesmas fórmulas do jogo, e detectamos colisão horizontal.
 * A velocidade final é aplicada uma vez por tick, depois que a IA decidiu o movimento.
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

    // ------------------------------------------------------------------ ciclo do tick

    /** Lê o estado deixado pela física do servidor no tick anterior. */
    public void beginTick() {
        Mannequin e = bot.entity();
        if (e == null) {
            return;
        }
        Vec3 pos = bot.pos();
        velocity = Vec3.of(e.getVelocity());
        // Se a física da entidade não rodou (chunk ainda não ativo), a velocidade volta igual à aplicada;
        // somar a aceleração de novo faria o bot "disparar" quando a física voltasse.
        // (o atrito sempre muda a velocidade horizontal quando a física roda; parado no chão ela é zero)
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

    /** Aplica a velocidade decidida neste tick à entidade. */
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
            // física parada: não reaplica (evita acumular) e espera o chunk voltar a tickar
            lastApplied = Vec3.of(e.getVelocity());
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

    // ------------------------------------------------------------------ comandos de movimento

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
     * Anda na direção (horizontal) como um jogador apertando W: aceleração = velocidade * 0,216/atrito³ no chão,
     * 0,02 (0,026 correndo) no ar e 0,02 na água/lava. O atrito e a gravidade são aplicados pelo servidor.
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

    /** Pulo: 0,42 + efeito de salto, com impulso de 0,2 para frente se estiver correndo (como o jogador). */
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

    /** Pulo com velocidade definida (usado por parkour e capacidades). */
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

    /** Nada em direção a um ponto (3D), como o jogador nadando rápido olhando para o alvo. */
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

    /** Ajustes de flutuação que o jogador faz segurando espaço perto da superfície. */
    public void tickWaterBuoyancy() {
        if (!bot.isInWater() || bot.pitch() >= 45f) {
            return;
        }
        boolean headInWater = bot.isEyeInWater();
        World w = bot.world();
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
        World w = bot.world();
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

    // ------------------------------------------------------------------ obstáculos e buracos

    /** Degrau de 1 bloco à frente com espaço acima: pula. */
    public void handleObstacles() {
        World w = bot.world();
        Pos front = bot.blockPos().relative(bot.facing());
        if (WorldUtil.isTrulySolid(w, front) && !WorldUtil.isTrulySolid(w, front.above()) && onGround) {
            sprinting = true;
            jump();
        }
    }

    public boolean shouldHandleGapAhead() {
        World w = bot.world();
        Pos ahead = bot.blockPos().relative(bot.facing());
        if (WorldUtil.isTrulySolid(w, ahead) && !WorldUtil.isTrulySolid(w, ahead.above())) {
            return false;
        }
        return !WorldUtil.isTrulySolid(w, ahead.below());
    }

    /** Buraco à frente: pula se houver chão a até 5 blocos, senão coloca um bloco (só perto do alvo). */
    public void handleGaps() {
        World w = bot.world();
        int now = Bukkit.getCurrentTick();
        Pos current = bot.blockPos();
        BlockFace facing = bot.facing();
        Pos ahead = current.relative(facing);
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
            Pos landing = current.relative(facing, i);
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

    /** Para de andar se não houver chão logo à frente. */
    public void avoidFalls() {
        Vec3 fwd = bot.forwardVector().scale(1.1);
        Pos below = bot.pos().add(fwd).add(0, -1, 0).toPos();
        if (!WorldUtil.isTrulySolid(bot.world(), below)) {
            stopWalk();
        }
    }
}
