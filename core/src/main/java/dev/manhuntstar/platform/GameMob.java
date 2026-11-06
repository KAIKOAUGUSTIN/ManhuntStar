package dev.manhuntstar.platform;

/** A mob with an AI (zombie, piglin, horse...), like Bukkit's Mob. */
public interface GameMob extends GameLiving {

    GameLiving target();

    void setTarget(GameLiving target);

    boolean isAware();

    void setAware(boolean aware);

    void stopPathfinding();
}
