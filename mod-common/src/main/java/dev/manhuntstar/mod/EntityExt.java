package dev.manhuntstar.mod;

/**
 * What the mod adds to every Minecraft entity (through a mixin): the core object that wraps it, a pose the
 * game must not change on its own, and whether it is left out when its chunk is saved.
 */
public interface EntityExt {

    Object manhuntstar$wrapper();

    void manhuntstar$setWrapper(Object wrapper);

    boolean manhuntstar$fixedPose();

    void manhuntstar$setFixedPose(boolean fixed);

    /** Not saved with the chunk (Bukkit's {@code setPersistent(false)}). */
    boolean manhuntstar$transient();

    void manhuntstar$setTransient(boolean value);

    /** The game's shared entity flags (Bukkit's {@code setGliding} sets flag 7). */
    void manhuntstar$setSharedFlag(int flag, boolean value);
}
