package dev.manhuntstar.bot.survival;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Survival: eating when hungry, golden apple in combat and escaping fire by placing water. */
public final class Survival {

    private static final int EAT_TICKS = 32;
    private static final int GAPPLE_COOLDOWN = 240;
    private static final int FIRE_ESCAPE_MOVE_TICKS = 8;
    private static final int FIRE_WATER_PICKUP_TICKS = 12;

    private final Bot bot;
    private int eatingTicks;
    private int gappleCooldown;
    private boolean eatingGoldenApple;
    private ItemStack currentFood = ItemStack.empty();
    private ItemStack savedMainHand = ItemStack.empty();
    private boolean fireEscapeActive;
    private int fireEscapeTicks;
    private Vec3 fireEscapeSource;
    private Pos fireEscapeWaterPos;
    private GameWorld fireEscapeWaterWorld;
    private int fireEscapeWaterCleanupTicks;

    public Survival(Bot bot) {
        this.bot = bot;
    }

    public boolean isEating() {
        return eatingTicks > 0;
    }

    public boolean isBusy() {
        return eatingTicks > 0 || fireEscapeActive;
    }

    public void tick() {
        if (!bot.isAlive() || bot.capacityLocked()) {
            return;
        }
        tickExhaustion();
        if (gappleCooldown > 0) {
            gappleCooldown--;
        }
        tickFireEscapeWaterRecovery();
        Settings s = bot.settings();
        if (shouldHandleFireEscape(s)) {
            tickFireEscape();
            return;
        }
        resetFireEscape();
        if (eatingTicks > 0) {
            tickEating();
            return;
        }
        if (s.autoGapple.on() && shouldUseGoldenApple()) {
            startEating(new ItemStack(Material.GOLDEN_APPLE), true);
            return;
        }
        if (s.autoEat.on() && bot.hunger().foodLevel() <= 16) {
            ItemStack food = selectPhaseFood(bot.progression().phase());
            if (!food.isEmpty()) {
                startEating(food, false);
            }
        }
    }

    private void tickExhaustion() {
        float exhaustion = 0;
        if (bot.motor().sprinting()) {
            exhaustion += 0.02f;
        }
        if (bot.velocity().horizontalLengthSqr() > 0.0025) {
            exhaustion += 0.01f;
        }
        if (!bot.onGround()) {
            exhaustion += 0.002f;
        }
        if (bot.combat().isChargingCrossbow() || bot.combat().isChargingTrident()) {
            exhaustion += 0.003f;
        }
        if (exhaustion > 0) {
            bot.hunger().addExhaustion(exhaustion);
        }
    }

    private boolean shouldUseGoldenApple() {
        return gappleCooldown <= 0 && bot.progression().phase() >= 5 && bot.health() <= 14f && isInCombat();
    }

    private boolean isInCombat() {
        GamePlayer t = bot.target();
        if (t != null && !t.isDead() && t.world().equals(bot.world()) && bot.distanceTo(t) < 16) {
            return true;
        }
        GameLiving mob = bot.combat().mobAttacker();
        return mob != null && !mob.isDead() && mob.world().equals(bot.world()) && bot.distanceTo(mob) < 12;
    }

    private static ItemStack selectPhaseFood(int phase) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (phase <= 3) {
            return new ItemStack(r.nextBoolean() ? Material.CARROT : Material.POTATO);
        }
        if (phase <= 5) {
            int roll = r.nextInt(3);
            return new ItemStack(roll == 0 ? Material.COOKED_BEEF : roll == 1 ? Material.CARROT : Material.POTATO);
        }
        return new ItemStack(r.nextInt(4) < 3 ? Material.COOKED_BEEF : Material.CARROT);
    }

    // ------------------------------------------------------------------ fire

    private boolean shouldHandleFireEscape(Settings s) {
        if (!s.fireWaterEscape.on() || bot.progression().phase() < 3 || !bot.isOnFire()) {
            return false;
        }
        GameWorld w = bot.world();
        if (w.dimension() == Dimension.NETHER || bot.isInLava() || bot.isInWater()) {
            return false;
        }
        BotBody e = bot.entity();
        return e == null || !PaperEntities.bukkit(e).hasPotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    private void tickFireEscape() {
        fireEscapeActive = true;
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        if (fireEscapeSource == null) {
            fireEscapeSource = bot.lastFireSourcePos();
            if (fireEscapeSource == null) {
                fireEscapeSource = bot.pos().subtract(bot.forwardVector());
            }
        }
        if (fireEscapeTicks < FIRE_ESCAPE_MOVE_TICKS) {
            Vec3 away = bot.pos().subtract(fireEscapeSource).horizontal();
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize().scale(0.45);
            bot.lookAt(bot.eyePos().add(away));
            bot.motor().walk(away);
            bot.motor().setSprinting(true);
            fireEscapeTicks++;
            return;
        }
        bot.motor().stopWalk();
        bot.motor().setSprinting(false);
        if (!bot.onGround()) {
            return;
        }
        GameWorld w = bot.world();
        Pos waterPos = bot.blockPos();
        if (!WorldUtil.canBeReplaced(w, waterPos)) {
            if (!WorldUtil.canBeReplaced(w, waterPos.above())) {
                return;
            }
            waterPos = waterPos.above();
        }
        ItemStack oldMain = bot.getItem(EquipmentSlot.HAND).clone();
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.WATER_BUCKET));
        bot.setPitchNow(90f);
        w.block(waterPos).setType(Blocks.WATER);
        bot.swing();
        w.playSound(Vec3.of(waterPos), Sounds.ITEM_BUCKET_EMPTY, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
        // if the bot dies before picking it up, the water disappears all the same
        bot.manager().temporaryBlocks().add(w, waterPos, Blocks.WATER, FIRE_WATER_PICKUP_TICKS + 40);
        fireEscapeWaterPos = waterPos;
        fireEscapeWaterWorld = w;
        fireEscapeWaterCleanupTicks = 0;
        bot.setItem(EquipmentSlot.HAND, oldMain);
        resetFireEscape();
    }

    private void tickFireEscapeWaterRecovery() {
        if (fireEscapeWaterPos == null) {
            return;
        }
        if (++fireEscapeWaterCleanupTicks < FIRE_WATER_PICKUP_TICKS) {
            return;
        }
        GameWorld w = fireEscapeWaterWorld;
        GameBlock b = w.block(fireEscapeWaterPos);
        // in another world the bot can't reach it: the temporary block registry picks it up
        if (w.equals(bot.world()) && b.type() == Blocks.WATER) {
            bot.manager().temporaryBlocks().remove(w, fireEscapeWaterPos);
            ItemStack oldMain = bot.getItem(EquipmentSlot.HAND).clone();
            bot.motor().stopWalk();
            bot.motor().setSprinting(false);
            bot.lookAt(Vec3.center(fireEscapeWaterPos));
            bot.setPitchNow(90f);
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.BUCKET));
            b.setType(Blocks.AIR);
            bot.swing();
            w.playSound(Vec3.of(fireEscapeWaterPos), Sounds.ITEM_BUCKET_FILL, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1.0f, 1.0f);
            bot.setItem(EquipmentSlot.HAND, oldMain);
        }
        fireEscapeWaterPos = null;
        fireEscapeWaterCleanupTicks = 0;
    }

    private void resetFireEscape() {
        fireEscapeActive = false;
        fireEscapeTicks = 0;
        fireEscapeSource = null;
    }

    // ------------------------------------------------------------------ eating

    private void startEating(ItemStack food, boolean golden) {
        if (food.isEmpty() || eatingTicks > 0 || bot.entity() == null) {
            return;
        }
        eatingGoldenApple = golden;
        currentFood = food.clone();
        savedMainHand = bot.getItem(EquipmentSlot.HAND).clone();
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        bot.motor().stopWalk();
        bot.motor().setSprinting(false);
        bot.setItem(EquipmentSlot.HAND, currentFood.clone());
        bot.mannequin().startUsingItem(EquipmentSlot.HAND);
        eatingTicks = EAT_TICKS;
    }

    private void tickEating() {
        if (bot.combat().isBlocking()) {
            bot.combat().stopBlock();
        }
        bot.motor().stopWalk();
        bot.motor().setSprinting(false);
        BotBody e = bot.entity();
        if (eatingTicks % 4 == 0 && eatingTicks < EAT_TICKS - 6 && e != null) {
            GameWorld w = PaperWorld.of(PaperWorld.bukkit(e.world()));
            Vec3 mouth = bot.eyePos().add(bot.lookVector().scale(0.3)).add(0, -0.1, 0);
            PaperWorld.bukkit(w).spawnParticle(Particle.ITEM, mouth.x(), mouth.y(), mouth.z(), 5, 0.1, 0.1, 0.1, 0.05, currentFood);
            w.playSound(e.position(), Sounds.ENTITY_GENERIC_EAT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.5f + 0.5f * ThreadLocalRandom.current().nextInt(2),
                    (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.2f + 1.0f);
        }
        if (--eatingTicks > 0) {
            return;
        }
        if (e != null) {
            PaperEntities.bukkit(e).clearActiveItem();
            e.world().playSound(e.position(), Sounds.ENTITY_PLAYER_BURP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.5f, ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f);
        }
        applyFood(currentFood, eatingGoldenApple);
        // The offhand isn't touched while eating; putting back the previous one would bring back a totem
        // used up during the meal.
        bot.setItem(EquipmentSlot.HAND, savedMainHand.clone());
        currentFood = ItemStack.empty();
        savedMainHand = ItemStack.empty();
        eatingGoldenApple = false;
    }

    private void applyFood(ItemStack food, boolean golden) {
        BotBody e = bot.entity();
        if (golden) {
            bot.hunger().eat(4, 1.2f);
            if (e != null) {
                PaperEntities.bukkit(e).addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                PaperEntities.bukkit(e).addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
            }
            gappleCooldown = GAPPLE_COOLDOWN;
            return;
        }
        switch (food.getType()) {
            case COOKED_BEEF -> bot.hunger().eat(8, 0.8f);
            case CARROT -> bot.hunger().eat(3, 0.6f);
            case POTATO -> bot.hunger().eat(1, 0.6f);
            default -> bot.hunger().eat(2, 0.5f);
        }
    }
}
