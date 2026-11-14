package dev.manhuntstar.mod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the mods' code uses from Minecraft that changed between the supported versions, for Minecraft 26.2
 * (the other versions have their own copy under mod-common/versions/). Each method does what Paper does on
 * that version.
 */
final class MinecraftCompat {

    static final Class<? extends Mob> ENDERMAN = EnderMan.class;

    private MinecraftCompat() {
    }

    /** Sends the entity's new velocity to the players (after Bukkit's setVelocity). */
    static void syncVelocity(Entity entity) {
        entity.hurtMarked = true;
    }

    /** Bukkit's setInvulnerable: no damage at all, except from what kills through it (the void, /kill). */
    static void setInvulnerable(Entity entity, boolean invulnerable) {
        entity.setInvulnerable(invulnerable);
    }

    /** Whether it was made invulnerable with {@link #setInvulnerable} (on 26.2 that's all isInvulnerable() reads). */
    static boolean isInvulnerable(Entity entity) {
        return entity.isInvulnerable();
    }

    /** The ticks left before the entity can be hurt again (Bukkit's noDamageTicks). */
    static int damageCooldown(LivingEntity entity) {
        return entity.invulnerableTime;
    }

    /** Swings the main hand, seen by the entity itself too. */
    static void swingMainHand(LivingEntity entity) {
        entity.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Whether the block stops movement (Bukkit's Material.isSolid). */
    static boolean blocksMotion(BlockState state) {
        return state.blocksMotion();
    }

    /** Whether ice broken on top of this block turns into water (liquids too, which the caller checks). */
    static boolean meltsIceAbove(BlockState state) {
        return state.blocksMotion();
    }
}
