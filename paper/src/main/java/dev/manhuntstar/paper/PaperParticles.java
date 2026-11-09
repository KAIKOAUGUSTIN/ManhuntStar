package dev.manhuntstar.paper;

import dev.manhuntstar.platform.Particle;

/** Core particles as Bukkit {@link org.bukkit.Particle}s. */
public final class PaperParticles {

    private PaperParticles() {
    }

    public static org.bukkit.Particle of(Particle particle) {
        return switch (particle) {
            case CRIT -> org.bukkit.Particle.CRIT;
            case ENCHANTED_HIT -> org.bukkit.Particle.ENCHANTED_HIT;
            case SWEEP_ATTACK -> org.bukkit.Particle.SWEEP_ATTACK;
            case CLOUD -> org.bukkit.Particle.CLOUD;
            case HEART -> org.bukkit.Particle.HEART;
            case FIREWORK -> org.bukkit.Particle.FIREWORK;
        };
    }
}
