package dev.manhuntstar.pathfinding;

import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.Arrays;

/**
 * Campo de fluxo (Dijkstra a partir do alvo): para cada bloco da região guarda o próximo passo
 * rumo ao alvo. Um único cálculo serve para todos os bots que perseguem o mesmo alvo.
 * Arrays planos e heap de primitivos, sem objetos por nó.
 */
public final class FlowField {

    public static final int RADIUS = 48;
    public static final int HALF_HEIGHT = 24;

    private static final float WALK = 2.81f;
    private static final float DIAGONAL = 3.97f;
    private static final float STEP_UP = 7.79f;
    private static final float STEP_DOWN = 4.5f;
    private static final float SWIM_EXTRA = 4.63f;
    private static final float INF = Float.MAX_VALUE;
    /** 16 vizinhos: 4 retos, 4 diagonais, 4 subindo e 4 descendo (dx, dy, dz). */
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};

    private final byte[] dir;
    private final float[] cost;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Pos target;
    private final long computedAtMs;

    private FlowField(byte[] dir, float[] cost, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, Pos target) {
        this.dir = dir;
        this.cost = cost;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.target = target;
        this.computedAtMs = System.currentTimeMillis();
    }

    public Pos target() {
        return target;
    }

    public long computedAtMs() {
        return computedAtMs;
    }

    /** Calcula o campo (thread de trabalho). */
    public static FlowField compute(CostProvider costs, Pos target) {
        int sizeX = RADIUS * 2 + 1;
        int sizeY = HALF_HEIGHT * 2 + 1;
        int sizeZ = RADIUS * 2 + 1;
        int total = sizeX * sizeY * sizeZ;
        float[] cost = new float[total];
        byte[] dir = new byte[total];
        Arrays.fill(cost, INF);
        int minX = target.x() - RADIUS;
        int minY = target.y() - HALF_HEIGHT;
        int minZ = target.z() - RADIUS;
        int start = index(RADIUS, HALF_HEIGHT, RADIUS, sizeX, sizeY);
        cost[start] = 0f;
        dir[start] = encode(0, 0, 0);
        FloatHeap open = new FloatHeap(4096);
        open.push(start, 0f);
        while (!open.isEmpty()) {
            float curCost = open.peekKey();
            int cur = open.pop();
            if (curCost > cost[cur]) {
                continue;
            }
            int lx = cur % sizeX;
            int ly = (cur / sizeX) % sizeY;
            int lz = cur / (sizeX * sizeY);
            int wx = lx + minX;
            int wy = ly + minY;
            int wz = lz + minZ;
            for (int[] n : NEIGHBORS) {
                int nx = lx + n[0];
                int ny = ly + n[1];
                int nz = lz + n[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue;
                }
                // O bot anda do vizinho para a célula atual.
                float move = moveCost(costs, wx + n[0], wy + n[1], wz + n[2], wx, wy, wz);
                if (move >= INF) {
                    continue;
                }
                float newCost = curCost + move;
                int ni = index(nx, ny, nz, sizeX, sizeY);
                if (newCost < cost[ni]) {
                    cost[ni] = newCost;
                    dir[ni] = encode(-n[0], -n[1], -n[2]);
                    open.push(ni, newCost);
                }
            }
        }
        return new FlowField(dir, cost, minX, minY, minZ, sizeX, sizeY, sizeZ, target);
    }

    /** Custo de andar de (fx,fy,fz) para (tx,ty,tz), ou {@link #INF} se impossível. */
    private static float moveCost(CostProvider c, int fx, int fy, int fz, int tx, int ty, int tz) {
        byte feet = c.cost(tx, ty, tz);
        byte head = c.cost(tx, ty + 1, tz);
        byte ground = c.cost(tx, ty - 1, tz);
        if (CostProvider.isBlocking(feet) || CostProvider.isBlocking(head) || ground == CostProvider.AVOID) {
            return INF;
        }
        boolean swimming = feet == CostProvider.WATER || head == CostProvider.WATER;
        if (!swimming && (ground == CostProvider.AIR || ground == CostProvider.WATER)) {
            return INF;
        }
        if (CostProvider.isBlocking(c.cost(fx, fy, fz)) || CostProvider.isBlocking(c.cost(fx, fy + 1, fz))) {
            return INF;
        }
        int dx = tx - fx;
        int dy = ty - fy;
        int dz = tz - fz;
        float base;
        if (dy > 0) {
            byte ceiling = c.cost(fx, fy + 2, fz);
            if (ceiling == CostProvider.UNBREAKABLE || ceiling == CostProvider.AVOID) {
                return INF;
            }
            base = STEP_UP + (CostProvider.isMinable(ceiling) ? ceiling : 0);
        } else if (dy < 0) {
            byte above = c.cost(tx, ty + 2, tz);
            if (CostProvider.isBlocking(above)) {
                return INF;
            }
            base = STEP_DOWN + (CostProvider.isMinable(above) ? above : 0);
        } else if (dx != 0 && dz != 0) {
            boolean side1 = !CostProvider.isSolid(c.cost(tx, fy, fz)) && !CostProvider.isSolid(c.cost(tx, fy + 1, fz));
            boolean side2 = !CostProvider.isSolid(c.cost(fx, fy, tz)) && !CostProvider.isSolid(c.cost(fx, fy + 1, tz));
            if (!side1 && !side2) {
                return INF;
            }
            base = DIAGONAL;
        } else {
            base = WALK;
        }
        if (CostProvider.isMinable(feet)) {
            base += feet;
        }
        if (CostProvider.isMinable(head)) {
            base += head;
        }
        if (swimming) {
            base += SWIM_EXTRA;
        }
        return base;
    }

    /** Direção (dx, dy, dz) do próximo passo a partir do bloco, ou null fora do campo/inalcançável. */
    public int[] direction(int wx, int wy, int wz) {
        int i = localIndex(wx, wy, wz);
        if (i < 0 || cost[i] == INF) {
            return null;
        }
        return decode(dir[i]);
    }

    /** Centro inferior do próximo bloco rumo ao alvo (tenta também 1 bloco acima/abaixo do pé). */
    public Vec3 waypoint(int wx, int wy, int wz) {
        for (int dy : new int[]{0, 1, -1}) {
            int[] d = direction(wx, wy + dy, wz);
            if (d != null) {
                return Vec3.bottomCenter(new Pos(wx + d[0], wy + dy + d[1], wz + d[2]));
            }
        }
        return null;
    }

    public float costAt(int wx, int wy, int wz) {
        int i = localIndex(wx, wy, wz);
        return i < 0 ? INF : cost[i];
    }

    public boolean inRange(Pos p) {
        return localIndex(p.x(), p.y(), p.z()) >= 0;
    }

    private int localIndex(int wx, int wy, int wz) {
        int lx = wx - minX;
        int ly = wy - minY;
        int lz = wz - minZ;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= sizeX || ly >= sizeY || lz >= sizeZ) {
            return -1;
        }
        return index(lx, ly, lz, sizeX, sizeY);
    }

    private static int index(int lx, int ly, int lz, int sizeX, int sizeY) {
        return lx + sizeX * (ly + sizeY * lz);
    }

    static byte encode(int dx, int dy, int dz) {
        return (byte) (((dx + 1) & 3) | (((dy + 1) & 3) << 2) | (((dz + 1) & 3) << 4));
    }

    static int[] decode(byte b) {
        int v = b & 0xFF;
        return new int[]{(v & 3) - 1, ((v >> 2) & 3) - 1, ((v >> 4) & 3) - 1};
    }

    /** Heap mínimo de (índice, custo) com remoção preguiçosa (entradas velhas são ignoradas). */
    private static final class FloatHeap {
        private int[] items;
        private float[] keys;
        private int size;

        FloatHeap(int cap) {
            items = new int[cap];
            keys = new float[cap];
        }

        boolean isEmpty() {
            return size == 0;
        }

        float peekKey() {
            return keys[0];
        }

        void push(int item, float key) {
            if (size == items.length) {
                items = Arrays.copyOf(items, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (keys[p] <= key) {
                    break;
                }
                items[i] = items[p];
                keys[i] = keys[p];
                i = p;
            }
            items[i] = item;
            keys[i] = key;
        }

        int pop() {
            int top = items[0];
            size--;
            if (size > 0) {
                int item = items[size];
                float key = keys[size];
                int i = 0;
                while (true) {
                    int l = 2 * i + 1;
                    if (l >= size) {
                        break;
                    }
                    int r = l + 1;
                    int c = (r < size && keys[r] < keys[l]) ? r : l;
                    if (keys[c] >= key) {
                        break;
                    }
                    items[i] = items[c];
                    keys[i] = keys[c];
                    i = c;
                }
                items[i] = item;
                keys[i] = key;
            }
            return top;
        }
    }
}
