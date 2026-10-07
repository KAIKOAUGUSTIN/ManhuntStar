package dev.manhuntstar.util;

/**
 * Mira de projéteis com a física do servidor. A cada tick a flecha (e o tridente) anda a velocidade
 * atual, perde 1% dela no ar e cai 0,05 bloco/tick. O ângulo é achado simulando o voo, e o alvo em
 * movimento é antecipado pelo tempo de voo.
 */
public final class Ballistics {

    public static final double ARROW_GRAVITY = 0.05;
    public static final double ARROW_DRAG = 0.99;
    private static final int MAX_TICKS = 200;
    private static final double MIN_ANGLE = Math.toRadians(-85);
    private static final double MAX_ANGLE = Math.toRadians(80);
    private static final double SCAN_STEP = Math.toRadians(1.0);

    private Ballistics() {
    }

    /**
     * @param angle  inclinação do disparo em radianos (positivo para cima)
     * @param ticks  tempo de voo até o alvo, em ticks (fracionário)
     * @param reachable false quando o alvo está fora do alcance (o ângulo é o que chega mais perto)
     */
    public record Solution(double angle, double ticks, boolean reachable) {
    }

    /** Tiro baixo (trajetória mais reta) que passa por (distância horizontal, desnível). */
    public static Solution solve(double horizontal, double dy, double speed, double gravity, double drag) {
        if (horizontal < 1.0E-3) {
            return new Solution(dy >= 0 ? MAX_ANGLE : MIN_ANGLE, Math.abs(dy) / speed, true);
        }
        double bestAngle = 0;
        double bestErr = Double.NEGATIVE_INFINITY;
        double prevAngle = Double.NaN;
        for (double a = MIN_ANGLE; a <= MAX_ANGLE; a += SCAN_STEP) {
            double err = heightError(a, horizontal, dy, speed, gravity, drag);
            if (err >= 0) {
                if (Double.isNaN(prevAngle)) {
                    return new Solution(a, flightTicks(a, horizontal, speed, drag), true);
                }
                double lo = prevAngle;
                double hi = a;
                for (int i = 0; i < 24; i++) {
                    double mid = (lo + hi) * 0.5;
                    if (heightError(mid, horizontal, dy, speed, gravity, drag) >= 0) {
                        hi = mid;
                    } else {
                        lo = mid;
                    }
                }
                return new Solution(hi, flightTicks(hi, horizontal, speed, drag), true);
            }
            if (err > bestErr) {
                bestErr = err;
                bestAngle = a;
            }
            prevAngle = a;
        }
        return new Solution(bestAngle, flightTicks(bestAngle, horizontal, speed, drag), false);
    }

    /** Altura do projétil ao cruzar a distância horizontal menos o desnível do alvo (−∞ se não chega). */
    static double heightError(double angle, double horizontal, double dy, double speed, double gravity, double drag) {
        double vx = Math.cos(angle) * speed;
        double vy = Math.sin(angle) * speed;
        double x = 0;
        double y = 0;
        for (int t = 0; t < MAX_TICKS; t++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= horizontal) {
                double f = (horizontal - x) / (nx - x);
                return y + (ny - y) * f - dy;
            }
            x = nx;
            y = ny;
            vx *= drag;
            vy = vy * drag - gravity;
            if (vx < 1.0E-4 || y < dy - 256) {
                break;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** Ticks até cruzar a distância horizontal (a parte horizontal só sofre arrasto). */
    static double flightTicks(double angle, double horizontal, double speed, double drag) {
        double vx = Math.cos(angle) * speed;
        if (vx <= 1.0E-6) {
            return MAX_TICKS;
        }
        double ratio = 1 - horizontal * (1 - drag) / vx;
        if (ratio <= 0) {
            return MAX_TICKS;
        }
        return Math.log(ratio) / Math.log(drag);
    }

    /**
     * Vetor de disparo de {@code from} até {@code target}, antecipando a velocidade do alvo
     * (blocos/tick). Usa a física da flecha; {@code gravity = 0} vira um tiro reto.
     */
    public static Vec3 aim(Vec3 from, Vec3 target, Vec3 targetVelocity, double speed, double gravity, double drag) {
        Vec3 aimPoint = target;
        Solution sol = null;
        for (int i = 0; i < 3; i++) {
            Vec3 d = aimPoint.subtract(from);
            double horizontal = d.horizontalLength();
            if (gravity <= 0) {
                double ticks = d.length() / speed;
                sol = new Solution(Math.atan2(d.y(), horizontal), ticks, true);
            } else {
                sol = solve(horizontal, d.y(), speed, gravity, drag);
            }
            if (targetVelocity == null || targetVelocity.lengthSqr() < 1.0E-6) {
                break;
            }
            double t = Math.min(sol.ticks(), 60);
            aimPoint = target.add(targetVelocity.scale(t));
        }
        Vec3 d = aimPoint.subtract(from);
        Vec3 flat = d.horizontal().normalize();
        if (flat.lengthSqr() < 1.0E-6) {
            return new Vec3(0, Math.signum(d.y()) * speed, 0);
        }
        double c = Math.cos(sol.angle());
        double s = Math.sin(sol.angle());
        return new Vec3(flat.x() * c * speed, s * speed, flat.z() * c * speed);
    }

    /** Vetor de disparo de flecha/tridente (gravidade 0,05 e arrasto 0,99). */
    public static Vec3 aimArrow(Vec3 from, Vec3 target, Vec3 targetVelocity, double speed) {
        return aim(from, target, targetVelocity, speed, ARROW_GRAVITY, ARROW_DRAG);
    }
}
