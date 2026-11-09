package dev.manhuntstar.bot.ai;

import dev.manhuntstar.util.Vec3;
import java.util.Random;
import java.util.UUID;

/**
 * Each bot's own way of walking (derived from the UUID): a slight sideways drift, some wobble and a
 * little overshoot, so a group of bots doesn't walk in a perfect line. Scales with {@code combat.humanization}.
 */
public final class Personality {

    private final float lateralBias;
    private final float forwardBias;
    private final float noiseFrequency;
    private final float noiseAmplitude;
    private final float overshootChance;
    private final Random rng;
    private float noisePhase;

    public Personality(UUID id) {
        Random r = new Random(id.getLeastSignificantBits());
        lateralBias = (r.nextFloat() - 0.5f) * 0.8f;
        forwardBias = r.nextFloat() * 0.3f;
        r.nextFloat(); // reaction delay (not used by the mod)
        r.nextFloat(); // sprint threshold (not used by the mod)
        noiseFrequency = 0.03f + r.nextFloat() * 0.04f;
        noiseAmplitude = 0.05f + r.nextFloat() * 0.2f;
        overshootChance = r.nextFloat() * 0.12f;
        noisePhase = r.nextFloat() * (float) Math.PI * 2f;
        rng = new Random(id.getMostSignificantBits());
    }

    public void tick() {
        noisePhase += noiseFrequency;
    }

    public Vec3 humanize(Vec3 waypoint, Vec3 moveDir, double mult) {
        if (mult <= 0 || moveDir.lengthSqr() < 1.0E-6) {
            return waypoint;
        }
        Vec3 lateral = new Vec3(-moveDir.z(), 0, moveDir.x()).normalize();
        float wobble = (float) Math.sin(noisePhase) * noiseAmplitude;
        Vec3 out = waypoint.add(lateral.scale((lateralBias + wobble) * mult)).add(moveDir.scale(forwardBias * mult));
        if (rng.nextFloat() < overshootChance * mult) {
            out = out.add(moveDir.scale((0.3 + rng.nextFloat() * 0.4) * mult));
        }
        return out;
    }
}
