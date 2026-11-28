package dev.manhuntstar.bot.perception;

import static dev.manhuntstar.bot.perception.PerceptionTuning.BODY_RANGE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.NAMETAG_DEPTH_ERROR;
import static dev.manhuntstar.bot.perception.PerceptionTuning.NAMETAG_RANGE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.NAMETAG_SIDE_ERROR;
import static dev.manhuntstar.bot.perception.PerceptionTuning.PARTICLE_ERROR;
import static dev.manhuntstar.bot.perception.PerceptionTuning.PARTICLE_RANGE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.SOUND_ERROR;
import static dev.manhuntstar.bot.perception.PerceptionTuning.VIEW_ANY_SIDE;
import static dev.manhuntstar.bot.perception.PerceptionTuning.VIEW_HALF_HEIGHT;
import static dev.manhuntstar.bot.perception.PerceptionTuning.VIEW_HALF_WIDTH;

import dev.manhuntstar.bot.perception.Sighting.Kind;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;

/** What a bot's eyes catch of a player, and how far off the guess it makes from that is. */
public final class Vision {

    private Vision() {
    }

    /**
     * What a bot with its eyes at {@code eye}, looking along {@code yaw}/{@code pitch}, sees of the player: the body
     * (feet, chest and eyes with no block in the way, also an invisible player in armor), else the name through the
     * blocks (a standing, visible player), else the invisibility particles; null when nothing.
     */
    public static Kind look(GameWorld w, Vec3 eye, float yaw, float pitch, PlayerView p) {
        Vec3 chest = p.chest();
        double distSq = eye.distanceToSqr(chest);
        if (distSq > BODY_RANGE * BODY_RANGE) {
            return null;
        }
        boolean chestInView = inView(eye, yaw, pitch, chest);
        if (p.bodyShows() && chestInView && seesBody(w, eye, p)) {
            return Kind.BODY;
        }
        Vec3 tag = p.nameTag();
        if (!p.invisible() && !p.sneaking() && eye.distanceToSqr(tag) <= NAMETAG_RANGE * NAMETAG_RANGE && inView(eye, yaw, pitch, tag)) {
            return Kind.NAMETAG;
        }
        if (p.invisible() && !p.armor() && p.particles() && distSq <= PARTICLE_RANGE * PARTICLE_RANGE && chestInView
                && WorldUtil.clearLine(w, eye, chest)) {
            return Kind.PARTICLES;
        }
        return null;
    }

    /** Feet, chest and eyes with no block in the way. */
    public static boolean seesBody(GameWorld w, Vec3 eye, PlayerView p) {
        return seesBody(w, eye, p.feet(), p.chest(), p.eyes());
    }

    public static boolean seesBody(GameWorld w, Vec3 eye, Vec3 feet, Vec3 chest, Vec3 eyes) {
        return WorldUtil.clearLine(w, eye, chest) && WorldUtil.clearLine(w, eye, eyes) && WorldUtil.clearLine(w, eye, feet);
    }

    /** The point is in the field of view of someone looking along {@code yaw}/{@code pitch} (or right next to them). */
    public static boolean inView(Vec3 eye, float yaw, float pitch, Vec3 point) {
        Vec3 d = point.subtract(eye);
        if (d.lengthSqr() <= VIEW_ANY_SIDE * VIEW_ANY_SIDE) {
            return true;
        }
        float side = Math.abs(wrapDegrees(d.yaw() - yaw));
        float up = Math.abs(d.pitch() - pitch);
        return side <= VIEW_HALF_WIDTH && up <= VIEW_HALF_HEIGHT;
    }

    /**
     * The position the bot believes in from what it noticed at {@code at} (the feet for the eyes, the source for a
     * sound). {@code noise} holds three numbers from -1 to 1 that stay the same while the bot keeps noticing the
     * player, so the guess doesn't jump around on every look.
     */
    public static Vec3 estimate(Vec3 eye, Vec3 at, Kind kind, double[] noise) {
        return switch (kind) {
            case BODY -> at;
            case NAMETAG -> {
                Vec3 los = at.subtract(eye);
                Vec3 dir = los.normalize();
                Vec3 side = new Vec3(-dir.z(), 0, dir.x()).normalize();
                yield at.add(dir.scale(noise[0] * NAMETAG_DEPTH_ERROR * los.length())).add(side.scale(noise[1] * NAMETAG_SIDE_ERROR));
            }
            case PARTICLES -> at.add(noise[0] * PARTICLE_ERROR, noise[2] * PARTICLE_ERROR * 0.5, noise[1] * PARTICLE_ERROR);
            case SOUND -> at.add(noise[0] * SOUND_ERROR, noise[2] * SOUND_ERROR * 0.5, noise[1] * SOUND_ERROR);
        };
    }

    static float wrapDegrees(float v) {
        float f = v % 360.0f;
        if (f >= 180.0f) {
            f -= 360.0f;
        }
        if (f < -180.0f) {
            f += 360.0f;
        }
        return f;
    }
}
