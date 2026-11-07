package dev.manhuntstar.platform;

/** A slime or magma cube. */
public interface GameSlime extends GameMob {

    /** 1 for the smallest; bigger ones split when they die. */
    int size();
}
