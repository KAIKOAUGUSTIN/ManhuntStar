package dev.manhuntstar.util;

/** Immutable 3D vector (avoids the mutability bugs of Bukkit's {@code Vector}). */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    /** The block's corner with the lowest coordinates (where Bukkit's Block.getLocation() points). */
    public static Vec3 of(Pos p) {
        return new Vec3(p.x(), p.y(), p.z());
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

    /** Rotates the vector around the Y axis. */
    public Vec3 rotateY(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Vec3(x * c - z * s, y, x * s + z * c);
    }

    public Pos toPos() {
        return new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Yaw (degrees, Minecraft convention) pointing in this direction. */
    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** Pitch (degrees, positive looking down). */
    public float pitch() {
        return (float) Math.toDegrees(-Math.atan2(y, Math.sqrt(x * x + z * z)));
    }

    /** Direction vector from yaw/pitch (same as the game's getLookAngle). */
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
