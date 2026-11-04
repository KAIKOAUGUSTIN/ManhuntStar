package dev.manhuntstar.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/** Vetor 3D imutável (evita os erros de mutabilidade do {@link Vector} do Bukkit). */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public static Vec3 of(Location l) {
        return new Vec3(l.getX(), l.getY(), l.getZ());
    }

    public static Vec3 of(Vector v) {
        return new Vec3(v.getX(), v.getY(), v.getZ());
    }

    public static Vec3 of(Entity e) {
        Location l = e.getLocation();
        return new Vec3(l.getX(), l.getY(), l.getZ());
    }

    public static Vec3 center(Pos p) {
        return new Vec3(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    public static Vec3 bottomCenter(Pos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 subtract(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public Vec3 multiply(double fx, double fy, double fz) {
        return new Vec3(x * fx, y * fy, z * fz);
    }

    public Vec3 withY(double ny) {
        return new Vec3(x, ny, z);
    }

    public Vec3 horizontal() {
        return new Vec3(x, 0, z);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public double horizontalLengthSqr() {
        return x * x + z * z;
    }

    public Vec3 normalize() {
        double len = length();
        return len < 1.0E-5 ? ZERO : new Vec3(x / len, y / len, z / len);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double distanceTo(Vec3 o) {
        return Math.sqrt(distanceToSqr(o));
    }

    public double distanceToSqr(Vec3 o) {
        double dx = x - o.x;
        double dy = y - o.y;
        double dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double horizontalDistance(Vec3 o) {
        double dx = x - o.x;
        double dz = z - o.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Gira o vetor em torno do eixo Y. */
    public Vec3 rotateY(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Vec3(x * c - z * s, y, x * s + z * c);
    }

    public Pos toPos() {
        return new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public Location toLocation(World world, float yaw, float pitch) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Yaw (graus, convenção do Minecraft) apontando nesta direção. */
    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** Pitch (graus, positivo olhando para baixo). */
    public float pitch() {
        return (float) Math.toDegrees(-Math.atan2(y, Math.sqrt(x * x + z * z)));
    }

    /** Vetor de direção a partir de yaw/pitch (igual ao getLookAngle do jogo). */
    public static Vec3 fromRotation(float yaw, float pitch) {
        double f = Math.toRadians(pitch);
        double g = Math.toRadians(-yaw);
        double h = Math.cos(g);
        double i = Math.sin(g);
        double j = Math.cos(f);
        double k = Math.sin(f);
        return new Vec3(i * j, -k, h * j);
    }
}
