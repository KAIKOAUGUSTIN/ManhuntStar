package dev.manhuntstar.bot.perception;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.FakeWorld;

/** The block types and the flat world (stone up to y = 63, air above) of the perception tests. */
final class Terrain {

    static final BlockType AIR = BlockType.register(0, "AIR", 0, BlockTrait.AIR, BlockTrait.REPLACEABLE);
    static final BlockType STONE = BlockType.register(1, "STONE", 1.5f, BlockTrait.SOLID);
    static final BlockType WOOL = BlockType.register(100, "WHITE_WOOL", 0.8f, BlockTrait.SOLID, BlockTrait.OCCLUDES_VIBRATIONS);
    static final BlockType NETHER_PORTAL = BlockType.register(101, "NETHER_PORTAL", -1);
    static final BlockType END_PORTAL = BlockType.register(102, "END_PORTAL", -1);

    private Terrain() {
    }

    static FakeWorld flat() {
        return new FakeWorld(p -> p.y() <= 63 ? STONE : AIR);
    }
}
