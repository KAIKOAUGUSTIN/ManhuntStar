package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.util.Vec3;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Cavalo (fase 4+): com o alvo a mais de 32 blocos, procura um cavalo adulto num raio de 40, monta,
 * doma (forçado depois de 3 s, como no mod), sela e cavalga até o alvo. Desce a 4 blocos do alvo e
 * só volta a montar depois de 60 s. Cavalo parado por 3 s fica marcado como inacessível por 30 s.
 *
 * <p>No jogo só um jogador comanda o cavalo. Com o bot montado, a IA do cavalo é desligada
 * ({@code setAware(false)}, ver {@code BotManager#takeHorseControl}) e o plugin aplica a entrada do
 * cavaleiro como o jogo faz: o cavalo vira para onde o cavaleiro olha, acelera com a velocidade do
 * atributo, pula com a força de pulo dele (com o impulso para a frente) e boia na água.
 */
public final class HorseRoutine {

    private static final double SEARCH_RADIUS = 40.0;
    private static final double INTERACT_RANGE = 2.8;
    private static final double MOUNT_START_DISTANCE = 32.0;
    private static final double DISMOUNT_DISTANCE = 4.0;
    private static final int TAME_TICKS = 60;
    private static final float MAX_TURN_PER_TICK = 30f;
    private static final double MAX_HORIZONTAL_SPEED = 1.5;

    /** Só nos testes em servidor ({@code -Dmanhuntstar.teste.cavalo=true}): mais detalhes no /bot info. */
    private static final boolean TEST_DETAIL = Boolean.getBoolean("manhuntstar.teste.cavalo");

    private final Bot bot;
    private int actionCooldown;
    private int knockbackGrace;
    private int noMoveTicks;
    private int jumpCooldown;
    private UUID targetHorse;
    private int lockTicks;
    private UUID untamedHorse;
    private int untamedTicks;
    private Vector lastSetVelocity;
    private String detail = "";

    public HorseRoutine(Bot bot) {
        this.bot = bot;
    }

    /** Detalhe para o /bot info (velocidade do cavalo, distância ao próximo ponto, domação). */
    public String detail() {
        return detail;
    }

    public void tickCooldowns() {
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (knockbackGrace > 0) {
            knockbackGrace--;
        }
        if (lockTicks > 0) {
            lockTicks--;
        }
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
    }

    /** @return true se o cavalo controlou o bot neste tick. */
    public boolean handle(LivingEntity target) {
        Entity vehicle = bot.vehicle();
        if (bot.isHorseRidingBlocked()) {
            if (vehicle instanceof Horse h) {
                dismount(h);
            }
            resetTarget();
            return false;
        }
        if (!bot.settings().allowHorse.on() || bot.progression().phase() < 4 || target == null || target.isDead()) {
            if (vehicle instanceof Horse h) {
                // sem alvo para perseguir: desce (montado o bot não anda nem luta direito)
                dismount(h);
                actionCooldown = Math.max(actionCooldown, 10);
            }
            return false;
        }
        if (vehicle instanceof Horse mounted) {
            return handleMounted(mounted, target);
        }
        Vec3 p = bot.pos();
        if (Math.hypot(p.x() - target.getX(), p.z() - target.getZ()) <= MOUNT_START_DISTANCE) {
            resetTarget();
            return false;
        }
        Horse horse = findCandidate();
        if (horse == null) {
            resetTarget();
            return false;
        }
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        double dist = bot.distanceTo(horse);
        if (dist > INTERACT_RANGE) {
            moveToward(Vec3.of(horse.getLocation()), horse);
            return true;
        }
        bot.motor().stopWalk();
        bot.lookAt(Vec3.of(horse.getEyeLocation()));
        if (!horse.isTamed()) {
            if (actionCooldown <= 0) {
                mount(horse);
            }
            return true;
        }
        if (!hasSaddle(horse)) {
            trySaddle(horse);
        }
        if (!bot.isRiding() && actionCooldown <= 0) {
            mount(horse);
        }
        return true;
    }

    private boolean handleMounted(Horse horse, LivingEntity target) {
        if (bot.distanceTo(target) <= DISMOUNT_DISTANCE) {
            dismount(horse);
            bot.blockHorseRidingForSeconds(60);
            resetTarget();
            actionCooldown = Math.max(actionCooldown, 10);
            return false;
        }
        targetHorse = horse.getUniqueId();
        lockTicks = 120;
        bot.manager().takeHorseControl(bot, horse);
        bot.motor().stopWalk();
        if (!horse.isTamed()) {
            tameTick(horse);
            return true;
        }
        if (!hasSaddle(horse)) {
            dismount(horse);
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.SADDLE));
            actionCooldown = Math.max(actionCooldown, 10);
            return true;
        }
        steer(horse, Vec3.of(target.getLocation()));
        if (noMoveTicks >= 60) {
            bot.manager().markHorseInaccessible(horse, 600);
            dismount(horse);
            resetTarget();
            actionCooldown = Math.max(actionCooldown, 10);
            return false;
        }
        return true;
    }

    /** Cavalo arisco: empina e se debate como faz com o jogador; domado à força depois de 3 s (mod). */
    private void tameTick(Horse horse) {
        if (!horse.getUniqueId().equals(untamedHorse)) {
            untamedHorse = horse.getUniqueId();
            untamedTicks = 0;
        }
        untamedTicks++;
        detail = "domando " + (untamedTicks * 100 / TAME_TICKS) + "%";
        if (untamedTicks >= TAME_TICKS) {
            horse.setRearing(false);
            horse.setDomestication(horse.getMaxDomestication());
            horse.setTamed(true);
            horse.getWorld().spawnParticle(Particle.HEART, horse.getLocation().add(0, 1.5, 0), 7, 0.5, 0.5, 0.5);
            untamedHorse = null;
            untamedTicks = 0;
            actionCooldown = Math.max(actionCooldown, 10);
            return;
        }
        int phase = untamedTicks % 20;
        if (phase == 1) {
            horse.setRearing(true);
            horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 1f, 1f);
        } else if (phase == 12) {
            horse.setRearing(false);
            float yaw = horse.getLocation().getYaw() + (float) ThreadLocalRandom.current().nextDouble(-35, 35);
            horse.setRotation(yaw, 0);
            horse.setBodyYaw(yaw);
        }
    }

    private static boolean hasSaddle(Horse horse) {
        ItemStack saddle = horse.getInventory().getSaddle();
        return saddle != null && saddle.getType() == Material.SADDLE;
    }

    private void mount(Horse horse) {
        if (horse.addPassenger(bot.entity())) {
            horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_SADDLE, SoundCategory.NEUTRAL, 0.5f, 1f);
        }
        noMoveTicks = 0;
        lastSetVelocity = null;
        actionCooldown = 10;
    }

    private void dismount(Horse horse) {
        horse.removePassenger(bot.entity());
        bot.manager().releaseHorse(horse);
        bot.motor().stopWalk();
        noMoveTicks = 0;
        lastSetVelocity = null;
    }

    private void trySaddle(Horse horse) {
        if (actionCooldown > 0) {
            return;
        }
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.SADDLE));
        bot.swing();
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_SADDLE, SoundCategory.NEUTRAL, 0.5f, 1f);
        actionCooldown = 8;
        Bukkit.getScheduler().runTaskLater(bot.manager().plugin(), () -> {
            if (bot.isAlive()) {
                bot.inventory().restorePreferredMainHand();
            }
        }, 2L);
    }

    /** Entrada do cavaleiro aplicada ao cavalo, como o jogo faz com um jogador montado. */
    private void steer(Horse horse, Vec3 chaseTarget) {
        Navigator nav = bot.brain().navigator();
        Vec3 navTarget = chaseTarget;
        if (bot.settings().pathMode.get().usesPlanner()) {
            Vec3 wp = nav.waypoint(chaseTarget);
            if (wp != null) {
                navTarget = wp;
            }
        }
        horse.setEatingGrass(false);
        horse.setEating(false);
        Location hl = horse.getLocation();
        Vector current = horse.getVelocity();
        // chunk sem física neste tick: não soma de novo (a velocidade acumularia e lançaria o cavalo)
        boolean stalled = lastSetVelocity != null && current.equals(lastSetVelocity)
                && lastSetVelocity.getX() * lastSetVelocity.getX() + lastSetVelocity.getZ() * lastSetVelocity.getZ() > 1.0E-8;
        Vec3 to = new Vec3(navTarget.x() - hl.getX(), 0, navTarget.z() - hl.getZ());
        detail = String.format(java.util.Locale.ROOT, "cavalo a %.1f blocos/s, próximo ponto a %.1f (dy %.1f)%s%s%s",
                Math.hypot(current.getX(), current.getZ()) * 20, to.length(), navTarget.y() - hl.getY(),
                noMoveTicks > 0 ? ", travado " + noMoveTicks : "", stalled ? ", sem física" : "",
                bot.mining().isMining() ? ", cavando" : "")
                + (TEST_DETAIL ? " ticks=" + horse.getTicksLived() + " chunk=" + hl.getChunk().getLoadLevel() + " ia=" + horse.isAware() : "");
        if (stalled) {
            noMoveTicks++; // conta como travado: depois de 3 s o bot desce e segue a pé
            return;
        }
        if (to.lengthSqr() < 0.09) {
            noMoveTicks = 0;
            return;
        }
        bot.lookAt(navTarget.add(0, 1.2, 0));
        if (horse.getNoDamageTicks() > 10) {
            knockbackGrace = 12;
        }
        if (knockbackGrace > 0) {
            noMoveTicks = 0;
            lastSetVelocity = null;
            return;
        }
        horse.setRearing(false);

        float delta = Bot.wrapDegrees(to.normalize().yaw() - hl.getYaw());
        float yaw = hl.getYaw() + Math.max(-MAX_TURN_PER_TICK, Math.min(MAX_TURN_PER_TICK, delta));
        horse.setRotation(yaw, 0);
        horse.setBodyYaw(yaw);
        double forward = Math.cos(Math.toRadians(delta)) <= -0.35 ? 0.45 : 1.0;

        double horizontalSpeed = Math.hypot(current.getX(), current.getZ());
        noMoveTicks = horizontalSpeed < 0.05 ? noMoveTicks + 1 : 0;

        boolean onGround = horse.isOnGround();
        boolean inWater = horse.isInWater();
        double accel;
        if (inWater) {
            accel = 0.02;
        } else if (onGround) {
            // jogo: velocidade do atributo × 0,98 (entrada para a frente) × 0,216 / atrito³
            float friction = hl.clone().subtract(0, 0.5, 0).getBlock().getType().getSlipperiness();
            accel = movementSpeed(horse) * 0.98 * (0.216 / (friction * friction * friction));
        } else {
            accel = movementSpeed(horse) * 0.98 * 0.1;
        }
        accel *= forward;
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        Vector v = current.clone().add(new Vector(fx * accel, 0, fz * accel));

        if (onGround && !inWater && jumpCooldown <= 0 && shouldJump(horse, hl, navTarget, to, fx, fz)) {
            // pulo carregado do jogador: força de pulo do cavalo e impulso de 0,4 para a frente
            v.setY(horse.getJumpStrength());
            v.add(new Vector(fx * 0.4 * forward, 0, fz * 0.4 * forward));
            horse.getWorld().playSound(hl, Sound.ENTITY_HORSE_JUMP, SoundCategory.NEUTRAL, 0.4f, 1f);
            jumpCooldown = 10;
        }
        if (inWater) {
            // nada como o cavalo com cavaleiro: fica na superfície
            v.setY(Math.min(v.getY() + 0.04, 0.3));
        }
        double h = Math.hypot(v.getX(), v.getZ());
        if (h > MAX_HORIZONTAL_SPEED) {
            v.setX(v.getX() / h * MAX_HORIZONTAL_SPEED);
            v.setZ(v.getZ() / h * MAX_HORIZONTAL_SPEED);
        }
        horse.setVelocity(v);
        lastSetVelocity = v.clone();
    }

    /**
     * Pula quando está travado num obstáculo, quando o próximo ponto do caminho é alto demais para o
     * passo do cavalo (1 bloco) ou quando há um vão logo à frente.
     */
    private boolean shouldJump(Horse horse, Location hl, Vec3 navTarget, Vec3 to, double fx, double fz) {
        if (to.lengthSqr() <= 1.0) {
            return false;
        }
        if (noMoveTicks >= 4) {
            return true;
        }
        if (navTarget.y() > hl.getY() + 1.15 && to.lengthSqr() < 9.0) {
            return true;
        }
        if (navTarget.y() < hl.getY() - 0.5) {
            return false;
        }
        Block ahead = hl.clone().add(fx * 1.2, -0.5, fz * 1.2).getBlock();
        Block ahead2 = ahead.getRelative(0, -1, 0);
        return ahead.isPassable() && ahead2.isPassable() && !ahead.isLiquid() && to.lengthSqr() > 4.0;
    }

    private static double movementSpeed(Horse horse) {
        AttributeInstance a = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        return a == null ? 0.225 : a.getValue();
    }

    private Horse findCandidate() {
        if (targetHorse != null && lockTicks > 0 && Bukkit.getEntity(targetHorse) instanceof Horse locked && eligible(locked)) {
            return locked;
        }
        Horse best = null;
        double bestDist = Double.MAX_VALUE;
        for (Horse h : bot.world().getNearbyEntitiesByType(Horse.class, bot.location(), SEARCH_RADIUS)) {
            if (!eligible(h)) {
                continue;
            }
            double d = bot.pos().distanceToSqr(Vec3.of(h.getLocation()));
            if (d < bestDist) {
                bestDist = d;
                best = h;
            }
        }
        if (best != null) {
            targetHorse = best.getUniqueId();
            lockTicks = 120;
        }
        return best;
    }

    private boolean eligible(Horse h) {
        if (h == null || h.isDead() || !h.isAdult() || bot.manager().isHorseInaccessible(h)) {
            return false;
        }
        return h.getPassengers().isEmpty() || h.getPassengers().getFirst() == bot.entity();
    }

    private void moveToward(Vec3 target, Horse horse) {
        Navigator nav = bot.brain().navigator();
        boolean hasPath = false;
        Vec3 dest = target;
        if (bot.settings().pathMode.get().usesPlanner()) {
            if (nav.isParkouring()) {
                nav.executeParkourTick();
                return;
            }
            Vec3 wp = nav.waypoint(target);
            if (wp != null) {
                dest = wp;
                hasPath = true;
            }
        }
        bot.brain().dumbStarMove(dest, hasPath, horse);
    }

    private void resetTarget() {
        targetHorse = null;
        lockTicks = 0;
        noMoveTicks = 0;
    }
}
