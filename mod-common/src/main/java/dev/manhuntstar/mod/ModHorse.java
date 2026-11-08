package dev.manhuntstar.mod;

import dev.manhuntstar.mod.mixin.AbstractHorseAccessor;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;

/** A Minecraft {@link AbstractHorse} as a core {@link GameHorse}, doing what Bukkit's {@code CraftAbstractHorse} does. */
public final class ModHorse extends ModMob implements GameHorse {

    /** The horse's open mouth (Paper's {@code setMouthOpen}). */
    private static final int FLAG_OPEN_MOUTH = 64;

    ModHorse(AbstractHorse horse) {
        super(horse);
    }

    @Override
    public AbstractHorse vanilla() {
        return (AbstractHorse) super.vanilla();
    }

    @Override
    public boolean isHorse() {
        return vanilla() instanceof Horse;
    }

    @Override
    public boolean isAdult() {
        return !vanilla().isBaby();
    }

    @Override
    public boolean isTamed() {
        return vanilla().isTamed();
    }

    @Override
    public void setTamed(boolean tamed) {
        vanilla().setTamed(tamed);
    }

    @Override
    public int domestication() {
        return vanilla().getTemper();
    }

    @Override
    public int maxDomestication() {
        return vanilla().getMaxTemper();
    }

    @Override
    public void setDomestication(int value) {
        if (value < 0 || value > maxDomestication()) {
            throw new IllegalArgumentException("Domestication level (" + value + ") need to be between 0 and " + maxDomestication());
        }
        vanilla().setTemper(value);
    }

    @Override
    public double jumpStrength() {
        return vanilla().getAttributeValue(Attributes.JUMP_STRENGTH);
    }

    @Override
    public void setRearing(boolean rearing) {
        if (rearing) {
            vanilla().setStanding(Integer.MAX_VALUE);
        } else {
            vanilla().clearStanding();
        }
    }

    @Override
    public void setEating(boolean eating) {
        ((AbstractHorseAccessor) vanilla()).manhuntstar$setFlag(FLAG_OPEN_MOUTH, eating);
    }

    @Override
    public void setEatingGrass(boolean eating) {
        vanilla().setEating(eating);
    }

    @Override
    public GameItem saddle() {
        return ModItem.of(vanilla().getItemBySlot(EquipmentSlot.SADDLE));
    }

    @Override
    public void setSaddle(GameItem saddle) {
        vanilla().setItemSlot(EquipmentSlot.SADDLE, ModItem.stack(saddle).copy());
    }
}
