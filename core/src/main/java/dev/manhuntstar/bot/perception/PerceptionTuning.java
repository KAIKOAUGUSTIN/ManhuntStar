package dev.manhuntstar.bot.perception;

/**
 * The numbers of the bots' perception, in one place to adjust them by testing in game: how far they see and hear,
 * how wrong a guess is and how long what they learned is worth.
 */
public final class PerceptionTuning {

    /** How far a bot sees a player's body (blocks). */
    public static final double BODY_RANGE = 128.0;
    /** How far the name over a standing player's head shows, also through blocks (the game's own 64). */
    public static final double NAMETAG_RANGE = 64.0;
    /** How far the particles of an invisible player show (the client draws no particle past 32 blocks). */
    public static final double PARTICLE_RANGE = 32.0;
    /** Field of view, in degrees to each side of where the bot looks: about 100° across and 70° up to down. */
    public static final double VIEW_HALF_WIDTH = 50.0;
    public static final double VIEW_HALF_HEIGHT = 35.0;
    /** Closer than this a player is seen wherever the bot looks (the corner of the eye). */
    public static final double VIEW_ANY_SIDE = 2.0;

    /** The name gives the direction well and the distance badly: up to this fraction of it, along the line of sight. */
    public static final double NAMETAG_DEPTH_ERROR = 0.15;
    /** And this many blocks to the side. */
    public static final double NAMETAG_SIDE_ERROR = 1.0;
    /** The particles swirl around the body: up to this many blocks off. */
    public static final double PARTICLE_ERROR = 1.0;
    /** A sound is heard roughly: up to this many blocks off, half of that in height. */
    public static final double SOUND_ERROR = 4.0;

    /** How far a vibration is heard (the sculk sensor's 8 blocks), and with the player sprinting. */
    public static final double SOUND_RANGE = 8.0;
    public static final double SOUND_RANGE_SPRINTING = 16.0;
    /** At most one sound per player in this many ticks (a step every few ticks would be heard again and again). */
    public static final int SOUND_INTERVAL_TICKS = 5;

    /** Ticks between one look around and the next for each bot (the bots take turns). */
    public static final int LOOK_INTERVAL_TICKS = 5;
    /** A body seen this recently counts as being seen now (a bit more than the time between looks). */
    public static final int VISIBLE_TICKS = LOOK_INTERVAL_TICKS + 1;
    /** Within two sightings this close in time, the more precise one wins over the newer one. */
    public static final int PRECISION_WINDOW_TICKS = LOOK_INTERVAL_TICKS;
    /** A name, particles or a sound this recent is "where the player is now, more or less". */
    public static final int APPROX_TICKS = 40;
    /** How long the last known position is worth going to. */
    public static final int MEMORY_TICKS = 45 * 20;
    /** How long it is worth for the respawn and the other teleports near the player (past it, nothing is known). */
    public static final int ANCHOR_MEMORY_TICKS = 5 * 60 * 20;
    /** A clue reached this close with nothing new is dropped (the player isn't there anymore). */
    public static final double CLUE_REACHED = 3.0;

    /** With only the compass, the bot walks to a point this far ahead along it... */
    public static final double COMPASS_STEP = 24.0;
    /** ...made again when the compass turns more than this many degrees or the bot gets this close to it. */
    public static final double COMPASS_REPLAN_DEGREES = 10.0;
    public static final double COMPASS_REPLAN_DISTANCE = 8.0;

    /** The compass turned at least this much... */
    public static final double FLIP_DEGREES = 120.0;
    /** ...while the bot moved at most this many blocks: it just passed over the player. */
    public static final double FLIP_MAX_TRAVEL = 8.0;
    /** Ticks between the compass readings compared for that. */
    public static final int FLIP_SAMPLE_TICKS = 10;
    /** A passing point farther than this behind the bot, with nothing found there, is forgotten. */
    public static final double FLIP_FORGET_DISTANCE = 16.0;

    /** A column of at least this many blocks placed by players, one on top of the other, is a tower. */
    public static final int TOWER_MIN_BLOCKS = 10;
    /** How far a tower is seen, and how far from the compass's direction it can be. */
    public static final double TOWER_SIGHT = 160.0;
    public static final double TOWER_BEARING_DEGREES = 20.0;
    /** Ticks between one look for towers and the next for each bot. */
    public static final int TOWER_INTERVAL_TICKS = 20;

    /** How close a bot sees that the compass points at a portal (the player went through it). */
    public static final double PORTAL_DISCOVERY = 12.0;
    /** A player stands 4 s inside a Nether portal before it takes them; the bot does the same. */
    public static final int NETHER_PORTAL_TICKS = 80;

    private PerceptionTuning() {
    }
}
