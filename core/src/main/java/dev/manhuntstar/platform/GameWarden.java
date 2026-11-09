package dev.manhuntstar.platform;

/** A warden. */
public interface GameWarden extends GameMob {

    /** Makes the warden angrier at an entity (it attacks once the anger is high enough). */
    void increaseAnger(GameEntity target, int amount);
}
