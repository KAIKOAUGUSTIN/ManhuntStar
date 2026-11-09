package dev.manhuntstar.mod;

/** What the mod adds to every mob (through a mixin): whether its AI runs (Paper's {@code Mob#setAware}). */
public interface MobExt {

    boolean manhuntstar$aware();

    void manhuntstar$setAware(boolean aware);
}
