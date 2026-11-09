package dev.manhuntstar.pathfinding;

import dev.manhuntstar.util.Pos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A* with the mod's movement model: walking/sprinting (with a ceiling), stepping up 1 block, falling up to 3,
 * jumping 2-3 block gaps, pillaring up/digging down, diagonals, digging blocks in the way,
 * placing blocks to cross gaps and crossing water by boat. Nodes live in arrays (no object allocation) and
 * each thread has its own instance, so it can safely run in parallel.
 *
 * <p>Going down never relies on a placed block: while falling, the bot can't put a block under where it
 * will land. Digging the floor with a gap below (the ceiling of a room or of the target's box), it falls to
 * the first support, up to {@link #MAX_DIG_FALL} blocks.
 */
public final class AStar {

    /** Longest planned fall: dig the floor and fall into the gap below (5 blocks = 1 heart of damage). */
    public static final int MAX_DIG_FALL = 5;
    /** Extra cost per block of fall above 3 (fall damage). */
    private static final double FALL_DAMAGE_COST = 4.0;

    private static final double SPRINT_COST = 2.81;
    private static final double CEILING_SPRINT_COST = 2.22;
    private static final double JUMP_COST = 3.16;
    private static final double STEP_UP_COST = 7.79;
    private static final double SWIM_EXTRA = 4.63;
    /** By boat on the surface: ~8 blocks/s against ~5.6 sprinting. */
    private static final double BOAT_FACTOR = 0.7;
    /** Placing the boat, boarding and breaking it on the other bank (~2 s): only worth it on a long crossing. */
    private static final double BOAT_BOARD_COST = 25.0;
    private static final double PLACE_COST = 20.0;
    private static final double DIAG_COST = 3.97334;
    private static final double DIAG_CEILING_COST = 3.13908;
    private static final double HEURISTIC_UNIT = 2.22;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final ThreadLocal<AStar> LOCAL = ThreadLocal.withInitial(AStar::new);

    public static AStar get() {
        return LOCAL.get();
    }

    // Node storage (struct-of-arrays).
    private int capacity = 4096;
    private int count;
    private int[] nx = new int[capacity];
    private int[] ny = new int[capacity];
    private int[] nz = new int[capacity];
    private int[] parent = new int[capacity];
    private double[] g = new double[capacity];
    private double[] h = new double[capacity];
    private int[] heapIdx = new int[capacity];
    private final LongIntMap index = new LongIntMap(8192);
    private int[] heap = new int[capacity];
    private int heapSize;

    private CostProvider costs;
    private int tx;
    private int ty;
    private int tz;
    private double greediness;
    private boolean allowPlacement;
    private boolean allowBoat;

    /**
     * @param allowBoat the water surface counts as a boat stretch (faster than swimming, with a fixed
     *                  cost to board), and the bot crosses by boat the stretch the path picks
     */
    public PathResult findPath(CostProvider costs, Pos start, Pos target, int maxNodes, long maxTimeMs, double greediness,
            boolean allowPlacement, boolean allowBoat) {
        long t0 = System.nanoTime();
        long deadline = t0 + maxTimeMs * 1_000_000L;
        this.costs = costs;
        this.tx = target.x();
        this.ty = target.y();
        this.tz = target.z();
        this.greediness = greediness;
        this.allowPlacement = allowPlacement;
        this.allowBoat = allowBoat;
        count = 0;
        heapSize = 0;
        index.clear();
        int startNode = newNode(start.x(), start.y(), start.z(), -1, 0, heuristic(start.x(), start.y(), start.z()));
        heapInsert(startNode);
        int best = startNode;
        int iterations = 0;
        while (heapSize > 0 && iterations < maxNodes) {
            if ((iterations & 63) == 0 && System.nanoTime() > deadline) {
                break;
            }
            int cur = heapPop();
            iterations++;
            if (h[cur] < h[best]) {
                best = cur;
            }
            int cx = nx[cur];
            int cy = ny[cur];
            int cz = nz[cur];
            if (Math.abs(cx - tx) <= 1 && Math.abs(cy - ty) <= 1 && Math.abs(cz - tz) <= 1) {
                return reconstruct(cur, true, iterations, t0);
            }
            boolean ceilingHere = CostProvider.isSolid(costs.cost(cx, cy + 2, cz));
            for (int[] d : CARDINALS) {
                int x = cx + d[0];
                int z = cz + d[1];
                double horiz = (ceilingHere || CostProvider.isSolid(costs.cost(x, cy + 2, z))) ? CEILING_SPRINT_COST : SPRINT_COST;
                expand(cur, x, cy, z, horiz);
                expandStepUp(cur, x, z, ceilingHere);
                for (int drop = 1; drop <= 3; drop++) {
                    if (!dropColumnClear(x, cy, z, drop)) {
                        break;
                    }
                    expand(cur, x, cy - drop, z, 3.0 + drop * 1.5, false);
                }
                if (CostProvider.isSolid(costs.cost(cx, cy - 1, cz))) {
                    tryParkour(cur, d);
                }
            }
            expand(cur, cx, cy + 1, cz, STEP_UP_COST);
            expandDown(cur);
            for (int[] d : DIAGONALS) {
                int x = cx + d[0];
                int z = cz + d[1];
                if (!diagonalCornersPassable(cx, cy, cz, x, z)) {
                    continue;
                }
                double cost = (ceilingHere || CostProvider.isSolid(costs.cost(x, cy + 2, z))) ? DIAG_CEILING_COST : DIAG_COST;
                expand(cur, x, cy, z, cost);
            }
        }
        return reconstruct(best, false, iterations, t0);
    }

    /** Diagonal only if at least one of the sides has no wall at body height (doesn't cut corners). */
    private boolean diagonalCornersPassable(int cx, int cy, int cz, int x, int z) {
        boolean side1 = !CostProvider.isSolid(costs.cost(x, cy, cz)) && !CostProvider.isSolid(costs.cost(x, cy + 1, cz));
        boolean side2 = !CostProvider.isSolid(costs.cost(cx, cy, z)) && !CostProvider.isSolid(costs.cost(cx, cy + 1, z));
        return side1 || side2;
    }

    private void expandStepUp(int cur, int x, int z, boolean ceilingHere) {
        int cy = ny[cur];
        double extra = 0;
        if (ceilingHere) {
            byte ceiling = costs.cost(nx[cur], cy + 2, nz[cur]);
            if (!CostProvider.isMinable(ceiling)) {
                return;
            }
            extra = ceiling;
        }
        expand(cur, x, cy + 1, z, STEP_UP_COST + extra);
    }

    /** When stepping off a ledge the body passes through the cells above the landing: they must be free. */
    private boolean dropColumnClear(int x, int cy, int z, int drop) {
        for (int y = cy + 1; y > cy - drop + 1; y--) {
            byte c = costs.cost(x, y, z);
            if (CostProvider.isSolid(c) || c == CostProvider.AVOID) {
                return false;
            }
        }
        return true;
    }

    private void tryParkour(int cur, int[] d) {
        int cx = nx[cur];
        int cy = ny[cur];
        int cz = nz[cur];
        // the jump goes up more than 1 block: with a ceiling right above the head it doesn't make it and the bot falls into the gap
        if (CostProvider.isSolid(costs.cost(cx, cy + 2, cz))) {
            return;
        }
        for (int dist = 2; dist <= 3; dist++) {
            int px = cx + d[0] * dist;
            int pz = cz + d[1] * dist;
            if (!CostProvider.isSolid(costs.cost(px, cy - 1, pz)) || costs.cost(px, cy, pz) != CostProvider.AIR || costs.cost(px, cy + 1, pz) != CostProvider.AIR
                    || CostProvider.isSolid(costs.cost(px, cy + 2, pz))) {
                continue;
            }
            boolean clear = true;
            for (int i = 1; i < dist; i++) {
                int mx = cx + d[0] * i;
                int mz = cz + d[1] * i;
                if (costs.cost(mx, cy, mz) != CostProvider.AIR || costs.cost(mx, cy + 1, mz) != CostProvider.AIR
                        || CostProvider.isSolid(costs.cost(mx, cy + 2, mz))) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                submit(cur, px, cy, pz, SPRINT_COST * dist + JUMP_COST);
            }
        }
    }

    /**
     * Going down: swimming, or digging the floor. If there is a gap under the dug block, the bot falls to
     * the first support (or to the water), at most {@link #MAX_DIG_FALL} blocks. With no block to dig there is
     * no way down: in the air the bot can't put a block under itself. The exception is the starting node in
     * the air (the bot jumping or falling when the path was requested), which really falls; the other nodes in
     * the air stand on a block the path will place.
     */
    private void expandDown(int cur) {
        int cx = nx[cur];
        int cy = ny[cur];
        int cz = nz[cur];
        byte floor = costs.cost(cx, cy - 1, cz);
        if (floor == CostProvider.WATER || costs.cost(cx, cy, cz) == CostProvider.WATER) {
            expand(cur, cx, cy - 1, cz, 3.0, false);
            return;
        }
        boolean dig = CostProvider.isMinable(floor);
        if (!dig && !(floor == CostProvider.AIR && parent[cur] < 0)) {
            return;
        }
        for (int land = cy - 1; land >= cy - MAX_DIG_FALL; land--) {
            byte feet = land == cy - 1 ? CostProvider.AIR : costs.cost(cx, land, cz);
            if (CostProvider.isSolid(feet) || feet == CostProvider.AVOID) {
                return;
            }
            if (feet != CostProvider.WATER && !CostProvider.isSolid(costs.cost(cx, land - 1, cz))) {
                continue;
            }
            if (land == cy - 1) {
                expand(cur, cx, land, cz, 3.0, false);
                return;
            }
            int fall = cy - land;
            double cost = 3.0 + fall * 1.5 + Math.max(0, fall - 3) * FALL_DAMAGE_COST;
            // the dug block only counts in expand when it is the landing's head (2-block fall)
            if (dig && land < cy - 2) {
                cost += floor;
            }
            expand(cur, cx, land, cz, cost, false);
            return;
        }
    }

    private void expand(int cur, int x, int y, int z, double baseCost) {
        expand(cur, x, y, z, baseCost, true);
    }

    /** @param canPlace can land on a placed block (not for falls: you can't place while falling) */
    private void expand(int cur, int x, int y, int z, double baseCost, boolean canPlace) {
        byte feet = costs.cost(x, y, z);
        byte head = costs.cost(x, y + 1, z);
        byte ground = costs.cost(x, y - 1, z);
        if (CostProvider.isBlocking(feet) || CostProvider.isBlocking(head) || ground == CostProvider.AVOID) {
            return;
        }
        double cost = baseCost;
        if (CostProvider.isMinable(feet)) {
            cost += feet;
        }
        if (CostProvider.isMinable(head)) {
            cost += head;
        }
        boolean swimming = feet == CostProvider.WATER || head == CostProvider.WATER;
        if (swimming && allowBoat && isBoatNode(x, y, z)) {
            cost = baseCost * BOAT_FACTOR;
            if (!isBoatNode(nx[cur], ny[cur], nz[cur])) {
                cost += BOAT_BOARD_COST;
            }
        } else if (swimming) {
            cost += SWIM_EXTRA;
        }
        if (!swimming && (ground == CostProvider.AIR || ground == CostProvider.WATER)) {
            if (!allowPlacement || !canPlace) {
                return;
            }
            cost += PLACE_COST;
            int dx = Math.abs(x - nx[cur]);
            int dz = Math.abs(z - nz[cur]);
            if (dx == 1 && dz == 1) {
                boolean s1 = CostProvider.isSolid(costs.cost(nx[cur], y - 1, z));
                boolean s2 = CostProvider.isSolid(costs.cost(x, y - 1, nz[cur]));
                if (!s1 && !s2) {
                    cost += PLACE_COST;
                }
            }
        }
        submit(cur, x, y, z, cost);
    }

    /** Water surface where the boat fits (feet in the water, head in the air). */
    private boolean isBoatNode(int x, int y, int z) {
        return costs.cost(x, y, z) == CostProvider.WATER && costs.cost(x, y + 1, z) == CostProvider.AIR && boatFits(x, y, z);
    }

    /**
     * The boat is 1.4 blocks wide: centered on a block, it reaches about 0.2 blocks into the four neighbors.
     * If one of them is the bank, the hull scrapes the ground and the boat nearly stops.
     */
    private boolean boatFits(int x, int y, int z) {
        for (int[] d : CARDINALS) {
            if (costs.cost(x + d[0], y, z + d[1]) != CostProvider.WATER) {
                return false;
            }
        }
        return true;
    }

    private void submit(int cur, int x, int y, int z, double moveCost) {
        long key = Pos.pack(x, y, z);
        double newG = g[cur] + moveCost;
        int existing = index.get(key);
        if (existing >= 0) {
            if (g[existing] <= newG) {
                return;
            }
            g[existing] = newG;
            parent[existing] = cur;
            if (heapIdx[existing] >= 0) {
                heapUp(heapIdx[existing]);
            } else {
                heapInsert(existing);
            }
            return;
        }
        int n = newNode(x, y, z, cur, newG, heuristic(x, y, z));
        heapInsert(n);
    }

    private double heuristic(int x, int y, int z) {
        return (Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz)) * HEURISTIC_UNIT * greediness;
    }

    private int newNode(int x, int y, int z, int par, double gg, double hh) {
        if (count == capacity) {
            grow();
        }
        int n = count++;
        nx[n] = x;
        ny[n] = y;
        nz[n] = z;
        parent[n] = par;
        g[n] = gg;
        h[n] = hh;
        heapIdx[n] = -1;
        index.put(Pos.pack(x, y, z), n);
        return n;
    }

    private void grow() {
        capacity *= 2;
        nx = Arrays.copyOf(nx, capacity);
        ny = Arrays.copyOf(ny, capacity);
        nz = Arrays.copyOf(nz, capacity);
        parent = Arrays.copyOf(parent, capacity);
        g = Arrays.copyOf(g, capacity);
        h = Arrays.copyOf(h, capacity);
        heapIdx = Arrays.copyOf(heapIdx, capacity);
        heap = Arrays.copyOf(heap, capacity);
    }

    private PathResult reconstruct(int goal, boolean complete, int iterations, long t0) {
        List<Pos> path = new ArrayList<>();
        Set<Pos> toMine = new LinkedHashSet<>();
        Set<Pos> toPlace = new LinkedHashSet<>();
        for (int cur = goal; cur >= 0; cur = parent[cur]) {
            int x = nx[cur];
            int y = ny[cur];
            int z = nz[cur];
            Pos pos = new Pos(x, y, z);
            path.add(pos);
            byte feet = costs.cost(x, y, z);
            byte head = costs.cost(x, y + 1, z);
            byte ground = costs.cost(x, y - 1, z);
            if (CostProvider.isMinable(feet)) {
                toMine.add(pos);
            }
            if (CostProvider.isMinable(head)) {
                toMine.add(pos.above());
            }
            int p = parent[cur];
            if (p >= 0 && y > ny[p] && CostProvider.isMinable(costs.cost(nx[p], ny[p] + 2, nz[p]))) {
                toMine.add(new Pos(nx[p], ny[p] + 2, nz[p]));
            }
            // dug the floor and fell into the gap below: the dug block is neither the landing's feet nor its head
            if (p >= 0 && x == nx[p] && z == nz[p] && y < ny[p] - 2 && CostProvider.isMinable(costs.cost(x, ny[p] - 1, z))) {
                toMine.add(new Pos(x, ny[p] - 1, z));
            }
            boolean swimming = feet == CostProvider.WATER || head == CostProvider.WATER;
            if (allowPlacement && !swimming && (ground == CostProvider.AIR || ground == CostProvider.WATER)) {
                if (p >= 0 && Math.abs(x - nx[p]) == 1 && Math.abs(z - nz[p]) == 1) {
                    boolean s1 = CostProvider.isSolid(costs.cost(nx[p], y - 1, z));
                    boolean s2 = CostProvider.isSolid(costs.cost(x, y - 1, nz[p]));
                    if (!s1 && !s2) {
                        toPlace.add(new Pos(nx[p], y - 1, z));
                    }
                }
                toPlace.add(pos.below());
            }
        }
        Collections.reverse(path);
        return new PathResult(path, toMine, toPlace, complete, iterations, (System.nanoTime() - t0) / 1_000_000L);
    }

    // ------------------------------------------------------------------ binary heap by f = g + h

    private double f(int n) {
        return g[n] + h[n];
    }

    private void heapInsert(int n) {
        heap[heapSize] = n;
        heapIdx[n] = heapSize;
        heapSize++;
        heapUp(heapSize - 1);
    }

    private int heapPop() {
        int top = heap[0];
        heapIdx[top] = -1;
        heapSize--;
        if (heapSize > 0) {
            int last = heap[heapSize];
            heap[0] = last;
            heapIdx[last] = 0;
            heapDown(0);
        }
        return top;
    }

    private void heapUp(int i) {
        int n = heap[i];
        double fn = f(n);
        while (i > 0) {
            int p = (i - 1) >>> 1;
            int pn = heap[p];
            if (f(pn) <= fn) {
                break;
            }
            heap[i] = pn;
            heapIdx[pn] = i;
            i = p;
        }
        heap[i] = n;
        heapIdx[n] = i;
    }

    private void heapDown(int i) {
        int n = heap[i];
        double fn = f(n);
        while (true) {
            int l = 2 * i + 1;
            if (l >= heapSize) {
                break;
            }
            int r = l + 1;
            int c = (r < heapSize && f(heap[r]) < f(heap[l])) ? r : l;
            int cn = heap[c];
            if (f(cn) >= fn) {
                break;
            }
            heap[i] = cn;
            heapIdx[cn] = i;
            i = c;
        }
        heap[i] = n;
        heapIdx[n] = i;
    }

    /** long -> int map with open addressing (avoids boxing). */
    static final class LongIntMap {
        private long[] keys;
        private int[] values;
        private boolean[] used;
        private int size;
        private int mask;

        LongIntMap(int cap) {
            int c = Integer.highestOneBit(Math.max(16, cap) * 2 - 1);
            keys = new long[c];
            values = new int[c];
            used = new boolean[c];
            mask = c - 1;
        }

        void clear() {
            if (size > 0) {
                Arrays.fill(used, false);
                size = 0;
            }
        }

        private static int mix(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }

        int get(long k) {
            int i = mix(k) & mask;
            while (used[i]) {
                if (keys[i] == k) {
                    return values[i];
                }
                i = (i + 1) & mask;
            }
            return -1;
        }

        void put(long k, int v) {
            if ((size + 1) * 2 > keys.length) {
                rehash();
            }
            int i = mix(k) & mask;
            while (used[i]) {
                if (keys[i] == k) {
                    values[i] = v;
                    return;
                }
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = k;
            values[i] = v;
            size++;
        }

        private void rehash() {
            long[] ok = keys;
            int[] ov = values;
            boolean[] ou = used;
            int c = ok.length * 2;
            keys = new long[c];
            values = new int[c];
            used = new boolean[c];
            mask = c - 1;
            size = 0;
            for (int i = 0; i < ok.length; i++) {
                if (ou[i]) {
                    put(ok[i], ov[i]);
                }
            }
        }
    }
}
