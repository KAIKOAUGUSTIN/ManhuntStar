package dev.manhuntstar.platform;

/** The entity that is a bot's body in the world (the vanilla Mannequin). */
public interface BotBody extends GameLiving {

    /** Changes the pose; {@code fixed} keeps the game from changing it back on its own. */
    void setPose(Pose pose, boolean fixed);
}
