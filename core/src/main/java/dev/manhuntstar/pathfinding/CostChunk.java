package dev.manhuntstar.pathfinding;

import dev.manhuntstar.platform.BlockSnapshot;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Cópia imutável de um chunk com os custos de cada seção 16x16x16 calculados sob demanda.
 * Pode ser lida por várias threads ao mesmo tempo: cada seção é calculada uma vez e publicada
 * com segurança (se duas threads calcularem a mesma seção, o resultado é idêntico).
 */
final class CostChunk {

    final BlockSnapshot snapshot;
    final int minY;
    final long capturedTick;
    private final AtomicReferenceArray<byte[]> sections;

    CostChunk(BlockSnapshot snapshot, int minY, int maxY, long capturedTick) {
        this.snapshot = snapshot;
        this.minY = minY;
        this.capturedTick = capturedTick;
        this.sections = new AtomicReferenceArray<>(((maxY - minY) >> 4) + 1);
    }

    byte cost(int lx, int y, int lz) {
        int sy = (y - minY) >> 4;
        byte[] sec = sections.get(sy);
        if (sec == null) {
            sec = scanSection(minY + (sy << 4));
            sections.compareAndSet(sy, null, sec);
        }
        return sec[lx | (lz << 4) | (((y - minY) & 15) << 8)];
    }

    private byte[] scanSection(int baseY) {
        byte[] out = new byte[4096];
        for (int ly = 0; ly < 16; ly++) {
            int y = baseY + ly;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int type = snapshot.typeId(lx, y, lz);
                    byte c = BlockCosts.cost(type);
                    if (c == CostProvider.AIR && BlockCosts.waterloggable(type) && snapshot.waterlogged(lx, y, lz)) {
                        c = CostProvider.WATER;
                    }
                    out[lx | (lz << 4) | (ly << 8)] = c;
                }
            }
        }
        return out;
    }
}
