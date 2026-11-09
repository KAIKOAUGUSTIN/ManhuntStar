package dev.manhuntstar.platform;

import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;

/** Where a ray hit a block: the block, the face it went through and the exact point. */
public record RayHit(Pos block, Face face, Vec3 point) {
}
