package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.GearScoring;
import dev.manhuntstar.bot.Progression;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.util.Vec3;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Cavalo (fase 4+): com o alvo a mais de 32 blocos, procura um cavalo adulto num raio de 40, monta,
 * doma (forçado depois de 3 s, como no mod), sela e cavalga até o alvo. Desce a 4 blocos do alvo e
 * só volta a montar depois de 60 s. Cavalo parado por 3 s fica marcado como inacessível por 30 s.
 *
 * <p>Com a lança a cavalo ligada ({@code combate.lanca-a-cavalo}) ele não desce: carrega contra o alvo
 * com a lança da fase, passa por ele, segue reto, dá a volta e carrega de novo (ver {@link #joust}).
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
    /**
     * Desvio máximo (ir até o cavalo e dele até o alvo, menos ir direto) para buscar um cavalo. O cavalo
     * ganha ~0,075 s por bloco sobre correr; 16 blocos de desvio (~3 s) mais a doma (3 s) já empatam
     * com o ganho numa perseguição de ~100 blocos.
     */
    private static final double MAX_DETOUR = 16.0;

    /** Ergue a lança quando faltam até (atraso da lança + isto) ticks para chegar ao alvo. */
    private static final int LANCE_RAISE_MARGIN = 6;
    /** Ticks seguindo reto depois de passar pelo alvo, para ganhar distância antes de voltar (~9 blocos a galope). */
    private static final int LANCE_RUNOUT_TICKS = 14;
    /** Passadas seguidas sem acertar antes de largar a lança e descer para lutar a pé. */
    private static final int LANCE_MAX_MISSES = 3;
    /** Lança erguida por mais que isso sem passar pelo alvo (ele fugiu de lado): abaixa e conta como erro. */
    private static final int LANCE_MAX_RAISED_TICKS = 100;
    /** Alvo mais alto ou mais baixo que isso (em cima de um pilar, num buraco): desce e luta a pé. */
    private static final double LANCE_MAX_DY = 2.5;
    /** Atraso da carga das lanças do jogo, caso o item não traga o componente. */
    private static final int DEFAULT_LANCE_DELAY = 12;

    /** Só nos testes em servidor ({@code -Dmanhuntstar.teste.cavalo=true}): mais detalhes no /manhunt info. */
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
    /** Ticks com a lança erguida (0 = abaixada). */
    private int lanceRaisedTicks;
    private boolean lanceHit;
    private int lanceMisses;
    private int runoutTicks;
    private double runoutX;
    private double runoutZ;
    private ItemStack lance = ItemStack.empty();
    private int lancePhase = -1;
    private int lanceDelay = DEFAULT_LANCE_DELAY;

    public HorseRoutine(Bot bot) {
        this.bot = bot;
    }

    /** Detalhe para o /manhunt info (velocidade do cavalo, distância ao próximo ponto, domação). */
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

    /** A pé nada de lança: se o bot desceu (ou foi derrubado) do cavalo com ela na mão, volta para a espada. */
    public void tickLance() {
        if (!bot.isAlive() || bot.vehicle() instanceof Horse) {
            return;
        }
        if (lanceRaisedTicks > 0 || runoutTicks > 0 || GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            stowLance();
            resetJoust();
        }
    }

    /** A carga de lança deste bot acertou alguém (ver BotEntityListener). */
    public void onLanceHit() {
        if (lanceRaisedTicks > 0) {
            lanceHit = true;
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
        Horse horse = findCandidate(target);
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
        boolean lanceOn = useLance(horse, target);
        if (!lanceOn && bot.distanceTo(target) <= DISMOUNT_DISTANCE) {
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
        if (lanceOn) {
            joust(horse, target);
        } else {
            stowLance();
            steer(horse, Vec3.of(target.getLocation()), true);
        }
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
        stowLance();
        resetJoust();
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

    /**
     * Entrada do cavaleiro aplicada ao cavalo, como o jogo faz com um jogador montado. Sem
     * {@code usePlanner} vai reto até o ponto (a corrida depois de passar pelo alvo na carga de lança).
     */
    private void steer(Horse horse, Vec3 chaseTarget, boolean usePlanner) {
        Navigator nav = bot.brain().navigator();
        Vec3 navTarget = chaseTarget;
        if (usePlanner && bot.settings().pathMode.get().usesPlanner()) {
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

    // ------------------------------------------------------------------ lança a cavalo

    /** Carga só com a opção ligada, alvo no nível do cavalo e PvP permitido (com "respeitar PvP"). */
    private boolean useLance(Horse horse, LivingEntity target) {
        if (!bot.settings().allowSpear.on() || lanceMisses >= LANCE_MAX_MISSES
                || Math.abs(target.getY() - horse.getY()) > LANCE_MAX_DY) {
            return false;
        }
        return !(target instanceof Player && bot.settings().respectPvp.on()
                && Boolean.FALSE.equals(target.getWorld().getGameRuleValue(GameRules.PVP)));
    }

    /**
     * Justa: segura a lança da fase e a ergue quando falta pouco para chegar ao alvo (a carga do jogo só
     * vale depois do atraso da lança). Depois de acertar ou de passar por ele, segue reto para ganhar
     * distância, dá a volta e carrega de novo. Dano, empurrão e derrubada da montaria são os da lança
     * do jogo, que usa a velocidade do cavalo.
     */
    private void joust(Horse horse, LivingEntity target) {
        holdLance();
        Location hl = horse.getLocation();
        if (runoutTicks > 0) {
            runoutTicks--;
            steer(horse, new Vec3(runoutX, hl.getY(), runoutZ), false);
            if (noMoveTicks > 5) {
                runoutTicks = 0; // parede à frente: volta já
            }
            detail += ", passando";
            return;
        }
        Vec3 tp = Vec3.of(target.getLocation());
        steer(horse, tp, true);
        double yawRad = Math.toRadians(hl.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double dx = tp.x() - hl.getX();
        double dz = tp.z() - hl.getZ();
        double dist = Math.hypot(dx, dz);
        double facing = dist < 1.0E-3 ? 1.0 : (dx * fx + dz * fz) / dist;
        if (lanceRaisedTicks > 0) {
            lanceRaisedTicks++;
            bot.lookAt(tp.add(0, target.getHeight() * 0.5, 0));
            detail += ", lança erguida";
            if (lanceHit || (facing < 0 && dist < 5.0)) {
                endPass(hl, fx, fz);
            } else if (lanceRaisedTicks > LANCE_MAX_RAISED_TICKS) {
                lowerLance();
                lanceMisses++;
            } else if (!bot.entity().hasActiveItem()) {
                raiseLance(); // o uso foi interrompido (cavou, trocou de item): ergue de novo
            }
            return;
        }
        Vector v = horse.getVelocity();
        double speed = Math.max(Math.hypot(v.getX(), v.getZ()), 0.05);
        if (facing > 0.8 && dist / speed <= lanceDelay + LANCE_RAISE_MARGIN) {
            raiseLance();
        }
    }

    /** Fim de uma passada: abaixa a lança e segue reto ~12 blocos antes de voltar. */
    private void endPass(Location hl, double fx, double fz) {
        lanceMisses = lanceHit ? 0 : lanceMisses + 1;
        lanceHit = false;
        lowerLance();
        runoutX = hl.getX() + fx * 12;
        runoutZ = hl.getZ() + fz * 12;
        runoutTicks = LANCE_RUNOUT_TICKS;
    }

    private void raiseLance() {
        Mannequin me = bot.entity();
        if (me == null || !GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            return;
        }
        if (me.getAttribute(Attribute.ATTACK_DAMAGE) == null) {
            // a carga do jogo lê o dano de ataque de quem segura a lança e o Mannequin não tem esse
            // atributo: sem ele o jogo lança um erro e o servidor remove o cavalo
            me.registerAttribute(Attribute.ATTACK_DAMAGE);
            me.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
        }
        me.startUsingItem(EquipmentSlot.HAND);
        lanceRaisedTicks = Math.max(lanceRaisedTicks, 1);
    }

    private void lowerLance() {
        Mannequin me = bot.entity();
        if (me != null && me.hasActiveItem() && GearScoring.isSpear(me.getActiveItem())) {
            me.clearActiveItem();
        }
        lanceRaisedTicks = 0;
    }

    /** Abaixa a lança e volta para a espada. */
    private void stowLance() {
        lowerLance();
        if (GearScoring.isSpear(bot.getItem(EquipmentSlot.HAND))) {
            bot.inventory().restorePreferredMainHand();
        }
    }

    private void resetJoust() {
        lanceRaisedTicks = 0;
        lanceHit = false;
        lanceMisses = 0;
        runoutTicks = 0;
    }

    private void holdLance() {
        if (bot.mining().isMining() || bot.survival().isEating()) {
            return;
        }
        ItemStack l = lanceForPhase();
        if (!l.isSimilar(bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, l.clone());
        }
    }

    /** Lança do nível da espada da fase: pedra (4), ferro (5-6), diamante (7-10, Afiação II a partir da 8), netherite (final). */
    private ItemStack lanceForPhase() {
        int phase = bot.progression().phase();
        if (phase != lancePhase) {
            Material m = phase >= Progression.FINAL_PHASE ? Material.NETHERITE_SPEAR
                    : phase >= 7 ? Material.DIAMOND_SPEAR : phase >= 5 ? Material.IRON_SPEAR : Material.STONE_SPEAR;
            lance = new ItemStack(m);
            if (phase >= 8) {
                lance.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
            }
            KineticWeapon kw = lance.getData(DataComponentTypes.KINETIC_WEAPON);
            lanceDelay = kw == null ? DEFAULT_LANCE_DELAY : kw.delayTicks();
            lancePhase = phase;
        }
        return lance;
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

    /** O cavalo com menor desvio no caminho até o alvo; nenhum se todos exigirem voltar demais. */
    private Horse findCandidate(LivingEntity target) {
        if (targetHorse != null && lockTicks > 0 && Bukkit.getEntity(targetHorse) instanceof Horse locked && eligible(locked)) {
            return locked;
        }
        Vec3 me = bot.pos();
        Vec3 goal = Vec3.of(target.getLocation());
        double direct = me.distanceTo(goal);
        Horse best = null;
        double bestDetour = MAX_DETOUR;
        for (Horse h : bot.world().getNearbyEntitiesByType(Horse.class, bot.location(), SEARCH_RADIUS)) {
            if (!eligible(h)) {
                continue;
            }
            Vec3 hp = Vec3.of(h.getLocation());
            double detour = me.distanceTo(hp) + hp.distanceTo(goal) - direct;
            if (detour < bestDetour) {
                bestDetour = detour;
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
