package dev.manhuntstar.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallisticsTest {

    /** Física da flecha no servidor 26.2: anda, perde 1% e cai 0,05 por tick. Devolve a menor distância ao ponto. */
    private static double closestApproach(Vec3 from, Vec3 velocity, Vec3 target, Vec3 targetVelocity) {
        double x = from.x(), y = from.y(), z = from.z();
        double vx = velocity.x(), vy = velocity.y(), vz = velocity.z();
        double best = Double.MAX_VALUE;
        Vec3 t = target;
        for (int tick = 0; tick < 200; tick++) {
            double nx = x + vx, ny = y + vy, nz = z + vz;
            Vec3 nt = t.add(targetVelocity);
            // menor distância no segmento deste tick
            for (int s = 0; s <= 10; s++) {
                double f = s / 10.0;
                double px = x + (nx - x) * f, py = y + (ny - y) * f, pz = z + (nz - z) * f;
                double tx = t.x() + (nt.x() - t.x()) * f, ty = t.y() + (nt.y() - t.y()) * f, tz = t.z() + (nt.z() - t.z()) * f;
                double d = Math.sqrt((px - tx) * (px - tx) + (py - ty) * (py - ty) + (pz - tz) * (pz - tz));
                best = Math.min(best, d);
            }
            x = nx;
            y = ny;
            z = nz;
            t = nt;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
            vz *= 0.99;
        }
        return best;
    }

    @Test
    void stationaryTargetsAreHitAtAllRanges() {
        Vec3 from = new Vec3(0, 65.52, 0);
        for (double speed : new double[] {1.6, 1.8, 2.5}) {
            for (int dist = 4; dist <= 40; dist += 3) {
                for (int dy = -12; dy <= 12; dy += 4) {
                    Vec3 target = new Vec3(dist * 0.6, 64.9 + dy, dist * 0.8);
                    if (!Ballistics.solve(dist, target.y() - from.y(), speed, Ballistics.ARROW_GRAVITY, Ballistics.ARROW_DRAG).reachable()) {
                        continue; // alto e longe demais para essa velocidade (nem no jogo alcança)
                    }
                    Vec3 v = Ballistics.aimArrow(from, target, Vec3.ZERO, speed);
                    assertTrue(Math.abs(v.length() - speed) < 1.0E-6, "velocidade alterada");
                    double miss = closestApproach(from, v, target, Vec3.ZERO);
                    assertTrue(miss < 0.25, "erro de " + miss + " a " + dist + " blocos, desnível " + dy + ", velocidade " + speed);
                }
            }
        }
    }

    @Test
    void runningTargetIsLed() {
        Vec3 from = new Vec3(0, 65.52, 0);
        Vec3 running = new Vec3(0.28, 0, 0); // jogador correndo de lado (5,6 blocos/s)
        for (int dist = 8; dist <= 30; dist += 4) {
            Vec3 target = new Vec3(0, 64.9, dist);
            Vec3 led = Ballistics.aimArrow(from, target, running, 1.8);
            Vec3 naive = Ballistics.aimArrow(from, target, Vec3.ZERO, 1.8);
            double ledMiss = closestApproach(from, led, target, running);
            double naiveMiss = closestApproach(from, naive, target, running);
            assertTrue(ledMiss < 0.35, "com antecipação errou " + ledMiss + " a " + dist);
            assertTrue(ledMiss < naiveMiss, "antecipar deveria ajudar a " + dist);
        }
    }

    @Test
    void modFormulaWentOverTheHead() {
        // fórmula da rajada de arco do mod: passava acima da cabeça de um jogador parado a 15 blocos
        Vec3 from = new Vec3(0, 65.52, 0);
        Vec3 head = new Vec3(0, 65.47, 15);
        double dist = 15;
        double lift = dist * 0.24 + dist * dist * 0.0032;
        Vec3 dir = new Vec3(0, head.y() - from.y() + lift, dist).normalize().scale(1.8);
        Vec3 chest = new Vec3(0, 64.9, 15);
        assertTrue(closestApproach(from, dir, chest, Vec3.ZERO) > 1.5);
        assertTrue(closestApproach(from, Ballistics.aimArrow(from, chest, Vec3.ZERO, 1.8), chest, Vec3.ZERO) < 0.25);
    }

    @Test
    void straightShotWithoutGravity() {
        Vec3 from = new Vec3(0, 65, 0);
        Vec3 target = new Vec3(10, 70, 10);
        Vec3 v = Ballistics.aim(from, target, Vec3.ZERO, 1.65, 0, 1);
        Vec3 dir = target.subtract(from).normalize().scale(1.65);
        assertTrue(v.subtract(dir).length() < 1.0E-6);
    }
}
