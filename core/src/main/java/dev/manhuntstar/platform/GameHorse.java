package dev.manhuntstar.platform;

/** A horse, donkey, mule or camel (Bukkit's AbstractHorse). {@link #isHorse()} tells a real horse apart. */
public interface GameHorse extends GameMob {

    /** The entity is a horse (not a donkey, mule, camel...). */
    boolean isHorse();

    boolean isAdult();

    boolean isTamed();

    void setTamed(boolean tamed);

    int domestication();

    int maxDomestication();

    void setDomestication(int value);

    double jumpStrength();

    void setRearing(boolean rearing);

    void setEating(boolean eating);

    void setEatingGrass(boolean eating);

    /** What's in the saddle slot (empty when nothing). */
    GameItem saddle();

    void setSaddle(GameItem saddle);
}
