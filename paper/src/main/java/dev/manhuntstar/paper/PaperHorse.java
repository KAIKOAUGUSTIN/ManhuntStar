package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameItem;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;

/** A Bukkit {@link AbstractHorse} as a core {@link GameHorse}: every call is the Bukkit one. */
public final class PaperHorse extends PaperMob implements GameHorse {

    PaperHorse(AbstractHorse horse) {
        super(horse);
    }

    @Override
    public AbstractHorse bukkit() {
        return (AbstractHorse) entity;
    }

    @Override
    public boolean isHorse() {
        return entity instanceof Horse;
    }

    @Override
    public boolean isAdult() {
        return bukkit().isAdult();
    }

    @Override
    public boolean isTamed() {
        return bukkit().isTamed();
    }

    @Override
    public void setTamed(boolean tamed) {
        bukkit().setTamed(tamed);
    }

    @Override
    public int domestication() {
        return bukkit().getDomestication();
    }

    @Override
    public int maxDomestication() {
        return bukkit().getMaxDomestication();
    }

    @Override
    public void setDomestication(int value) {
        bukkit().setDomestication(value);
    }

    @Override
    public double jumpStrength() {
        return bukkit().getJumpStrength();
    }

    @Override
    public void setRearing(boolean rearing) {
        bukkit().setRearing(rearing);
    }

    @Override
    public void setEating(boolean eating) {
        bukkit().setEating(eating);
    }

    @Override
    public void setEatingGrass(boolean eating) {
        bukkit().setEatingGrass(eating);
    }

    @Override
    public GameItem saddle() {
        return PaperItems.of(bukkit().getInventory().getSaddle());
    }

    @Override
    public void setSaddle(GameItem saddle) {
        bukkit().getInventory().setSaddle(PaperItems.stack(saddle));
    }
}
