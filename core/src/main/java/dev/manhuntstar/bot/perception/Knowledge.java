package dev.manhuntstar.bot.perception;

import dev.manhuntstar.util.Vec3;

/** What a bot knows about where its target is right now. */
public record Knowledge(State state, Vec3 pos, long age, Sighting sighting) {

    public enum State {
        /** The bot or someone on its team sees the body: {@code pos} is the real position. */
        VISIBLE,
        /** A name, particles or a sound just now: {@code pos} is a guess. */
        APPROX,
        /** Seen or heard a while ago ({@code age} ticks): where the player was. */
        LAST,
        /** Only the compass's direction. */
        COMPASS,
        /** Not even that (the compass spins). */
        NONE
    }

    public static final Knowledge NOTHING = new Knowledge(State.NONE, null, 0, null);
    public static final Knowledge COMPASS_ONLY = new Knowledge(State.COMPASS, null, 0, null);

    public boolean visible() {
        return state == State.VISIBLE;
    }

    /** There is a position: the real one or a guess. */
    public boolean knowsPosition() {
        return pos != null;
    }
}
