package dev.manhuntstar.pathfinding;

import dev.manhuntstar.util.Pos;
import java.util.List;
import java.util.Set;

/** Path found: nodes in order, blocks to dig, blocks to place and whether it reached the destination. */
public record PathResult(List<Pos> path, Set<Pos> toMine, Set<Pos> toPlace, boolean complete, int nodesExplored, long timeMs) {
}
