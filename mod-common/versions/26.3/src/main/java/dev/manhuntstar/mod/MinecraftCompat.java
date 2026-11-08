package dev.manhuntstar.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the mods' code uses from Minecraft that changed between the supported versions, for Minecraft 26.3
 * (the other versions have their own copy under mod-common/versions/). Each method does what Paper does on
 * that version.
 */
final class MinecraftCompat {

    static final Class<? extends Mob> ENDERMAN = Enderman.class;

    private MinecraftCompat() {
    }

    /** Sends the entity's new velocity to the players (after Bukkit's setVelocity). */
    static void syncVelocity(Entity entity) {
        entity.syncVelocity = true;
    }

    /** Bukkit's setInvulnerable: no damage at all, except from what kills through it (the void, /kill). */
    static void setInvulnerable(Entity entity, boolean invulnerable) {
        entity.setPermanentlyInvulnerable(invulnerable);
    }

    /** The ticks left before the entity can be hurt again (Bukkit's noDamageTicks). */
    static int damageCooldown(LivingEntity entity) {
        return entity.damageCooldownTime;
    }

    /** Swings the main hand with the held item's animation, seen by the entity itself too. */
    static void swingMainHand(LivingEntity entity) {
        SwingAnimation animation = entity.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.ATTACK_ANIMATION);
        entity.swing(InteractionHand.MAIN_HAND, animation != null ? animation : SwingAnimation.DEFAULT, true);
    }

    /** Whether the block stops movement (Bukkit's Material.isSolid). */
    static boolean blocksMotion(BlockState state) {
        return state.is(BlockTags.BLOCKS_MOTION);
    }

    /** Whether ice broken on top of this block turns into water (liquids too, which the caller checks). */
    static boolean meltsIceAbove(BlockState state) {
        return state.is(BlockTags.ICE_MELTS_WHEN_DESTROYED_ABOVE);
    }
}
