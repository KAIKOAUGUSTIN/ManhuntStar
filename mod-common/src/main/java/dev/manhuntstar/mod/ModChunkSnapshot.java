package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BlockSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * A copy of a chunk's blocks (as Bukkit's {@code getChunkSnapshot(false, false, false)}), taken on the server
 * thread and read by the pathfinding threads. Each section's palette is copied; empty sections stay air.
 */
final class ModChunkSnapshot implements BlockSnapshot {

    private static final int AIR = BuiltInRegistries.BLOCK.getId(Blocks.AIR);

    private final int minSection;
    private final PalettedContainer<BlockState>[] sections;

    @SuppressWarnings("unchecked")
    ModChunkSnapshot(LevelChunk chunk) {
        LevelChunkSection[] src = chunk.getSections();
        this.minSection = chunk.getMinSectionY();
        this.sections = new PalettedContainer[src.length];
        for (int i = 0; i < src.length; i++) {
            LevelChunkSection s = src[i];
            sections[i] = s == null || s.hasOnlyAir() ? null : s.getStates().copy();
        }
    }

    private BlockState state(int x, int y, int z) {
        int i = (y >> 4) - minSection;
        if (i < 0 || i >= sections.length) {
            return null;
        }
        PalettedContainer<BlockState> s = sections[i];
        return s == null ? null : s.get(x & 15, y & 15, z & 15);
    }

    @Override
    public int typeId(int x, int y, int z) {
        BlockState s = state(x, y, z);
        return s == null ? AIR : BuiltInRegistries.BLOCK.getId(s.getBlock());
    }

    @Override
    public boolean waterlogged(int x, int y, int z) {
        BlockState s = state(x, y, z);
        return s != null && s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);
    }
}
