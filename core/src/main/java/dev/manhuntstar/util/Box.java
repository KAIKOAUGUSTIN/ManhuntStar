package dev.manhuntstar.util;

/** An axis-aligned box (an entity's or a block's bounding box). Immutable. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public static final Box EMPTY = new Box(0, 0, 0, 0, 0, 0);

    /** The box grown by the amount on every side (shrunk if negative). */
    public Box expand(double amount) {
        return expand(amount, amount, amount);
    }

    public Box expand(double x, double y, double z) {
        return new Box(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    /** The same box moved by this offset. */
    public Box move(Vec3 offset) {
        return new Box(minX + offset.x(), minY + offset.y(), minZ + offset.z(), maxX + offset.x(), maxY + offset.y(), maxZ + offset.z());
    }

    /** Distance from the point to the nearest point of the box, 0 inside (as the game's {@code AABB.distanceToSqr}). */
    public double distanceTo(Vec3 p) {
        double dx = Math.max(Math.max(minX - p.x(), 0), p.x() - maxX);
        double dy = Math.max(Math.max(minY - p.y(), 0), p.y() - maxY);
        double dz = Math.max(Math.max(minZ - p.z(), 0), p.z() - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The boxes share some volume (touching faces don't count). */
    public boolean overlaps(Box o) {
        return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
    }

    public boolean contains(Vec3 p) {
        return p.x() >= minX && p.x() < maxX && p.y() >= minY && p.y() < maxY && p.z() >= minZ && p.z() < maxZ;
    }

    public Vec3 center() {
        return new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    public double height() {
        return maxY - minY;
    }

    public double widthX() {
        return maxX - minX;
    }

    public double widthZ() {
        return maxZ - minZ;
    }
}
