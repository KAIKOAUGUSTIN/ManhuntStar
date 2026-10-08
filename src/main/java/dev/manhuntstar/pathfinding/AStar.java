package dev.manhuntstar.pathfinding;

import dev.manhuntstar.util.Pos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A* com o modelo de movimento do mod: andar/correr (com teto), subir 1 bloco, cair até 3,
 * pular buracos de 2-3 blocos, pilar para cima/descer cavando, diagonais, cavar blocos no caminho,
 * colocar blocos para atravessar vãos e atravessar água de barco. Nós ficam em arrays (sem alocar objetos) e cada thread
 * tem sua própria instância, então pode rodar em paralelo com segurança.
 */
public final class AStar {

    private static final double SPRINT_COST = 2.81;
    private static final double CEILING_SPRINT_COST = 2.22;
    private static final double JUMP_COST = 3.16;
    private static final double STEP_UP_COST = 7.79;
    private static final double SWIM_EXTRA = 4.63;
    /** De barco na superfície: ~8 blocos/s contra ~5,6 correndo. */
    private static final double BOAT_FACTOR = 0.7;
    /** Colocar o barco, embarcar e quebrá-lo na outra margem (~2 s): só compensa em travessia longa. */
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

    // Armazenamento dos nós (struct-of-arrays).
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
     * @param allowBoat a superfície da água conta como trecho de barco (mais rápido que nadar, com um
     *                  custo fixo para embarcar), e o bot atravessa de barco o trecho que o caminho escolher
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
                    expand(cur, x, cy - drop, z, 3.0 + drop * 1.5);
                }
                if (CostProvider.isSolid(costs.cost(cx, cy - 1, cz))) {
                    tryParkour(cur, d);
                }
            }
            expand(cur, cx, cy + 1, cz, STEP_UP_COST);
            expand(cur, cx, cy - 1, cz, 3.0);
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

    /** Diagonal só se pelo menos um dos lados não tiver parede no corpo (não atravessa quina). */
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

    /** Ao sair de uma beirada o corpo passa pelas células acima do pouso: precisam estar livres. */
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
        // o pulo sobe mais de 1 bloco: com teto logo acima da cabeça ele não sai e o bot cai no vão
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

    private void expand(int cur, int x, int y, int z, double baseCost) {
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
        if (swimming && allowBoat && feet == CostProvider.WATER && head == CostProvider.AIR && boatFits(x, y, z)) {
            cost = baseCost * BOAT_FACTOR;
            if (costs.cost(nx[cur], ny[cur], nz[cur]) != CostProvider.WATER) {
                cost += BOAT_BOARD_COST;
            }
        } else if (swimming) {
            cost += SWIM_EXTRA;
        }
        if (!swimming && (ground == CostProvider.AIR || ground == CostProvider.WATER)) {
            if (!allowPlacement) {
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

    /** O barco tem 1,4 bloco de largura e não passa num canal de 1 bloco: a água precisa continuar para os lados. */
    private boolean boatFits(int x, int y, int z) {
        int water = 0;
        for (int[] d : CARDINALS) {
            if (costs.cost(x + d[0], y, z + d[1]) == CostProvider.WATER) {
                water++;
            }
        }
        return water >= 3;
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

    // ------------------------------------------------------------------ heap binário por f = g + h

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

    /** Mapa long -> int com endereçamento aberto (evita boxing). */
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
