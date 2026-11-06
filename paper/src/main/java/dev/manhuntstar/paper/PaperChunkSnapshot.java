package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BlockSnapshot;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.Waterlogged;

/** A Bukkit {@link ChunkSnapshot} as a core {@link BlockSnapshot}. */
final class PaperChunkSnapshot implements BlockSnapshot {

    private final ChunkSnapshot snapshot;

    PaperChunkSnapshot(ChunkSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public int typeId(int x, int y, int z) {
        return snapshot.getBlockType(x, y, z).ordinal();
    }

    @Override
    public boolean waterlogged(int x, int y, int z) {
        return snapshot.getBlockData(x, y, z) instanceof Waterlogged w && w.isWaterlogged();
    }
}
