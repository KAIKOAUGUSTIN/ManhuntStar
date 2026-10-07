package dev.manhuntstar.pathfinding;

/**
 * Custo de cada bloco para o pathfinding.
 * <ul>
 *   <li>{@link #AIR} (0): livre para passar.</li>
 *   <li>{@link #WATER} (-2): água (dá para nadar).</li>
 *   <li>{@link #AVOID} (-1): perigo ou fora da área conhecida (lava, fogo, arbusto de frutas, neve fofa...).</li>
 *   <li>{@link #UNBREAKABLE} (-3): sólido que não quebra (bedrock, barreira): dá para pisar, não dá para cavar.</li>
 *   <li>1..127: sólido quebrável; o valor é o custo de cavar.</li>
 * </ul>
 */
@FunctionalInterface
public interface CostProvider {

    byte AIR = 0;
    byte AVOID = -1;
    byte WATER = -2;
    byte UNBREAKABLE = -3;

    byte cost(int x, int y, int z);

    static boolean isMinable(byte c) {
        return c > 0;
    }

    /** Pode servir de chão (sólido, quebrável ou não). */
    static boolean isSolid(byte c) {
        return c > 0 || c == UNBREAKABLE;
    }

    /** O corpo não pode ocupar (perigo ou inquebrável). */
    static boolean isBlocking(byte c) {
        return c == AVOID || c == UNBREAKABLE;
    }
}
