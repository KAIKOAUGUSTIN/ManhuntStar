package dev.manhuntstar.bot.ai;

import dev.manhuntstar.util.Vec3;
import java.util.Random;
import java.util.UUID;

/**
 * Jeito próprio de cada bot andar (derivado do UUID): leve desvio lateral, oscilação e passar
 * um pouco do ponto, para que um grupo de bots não ande em fila perfeita. Escala com "humanizacao".
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
        r.nextFloat(); // atraso de reação (não usado pelo mod)
        r.nextFloat(); // limiar de corrida (não usado pelo mod)
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
