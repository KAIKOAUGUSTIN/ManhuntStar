package dev.manhuntstar.pathfinding;

import dev.manhuntstar.util.Pos;
import java.util.List;
import java.util.Set;

/** Caminho encontrado: nós em ordem, blocos a cavar, blocos a colocar e se chegou ao destino. */
public record PathResult(List<Pos> path, Set<Pos> toMine, Set<Pos> toPlace, boolean complete, int nodesExplored, long timeMs) {
}
