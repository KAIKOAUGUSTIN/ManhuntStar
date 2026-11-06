package dev.manhuntstar.platform;

import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A world for tests, without a server: blocks come from a map and, where it has nothing, from a default
 * function (for example "stone up to y=63, air above"). Sounds played are recorded. Methods can be
 * overridden for what a test needs.
 */
public class FakeWorld implements GameWorld {

    public record PlayedSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
    }

    private final UUID id = UUID.randomUUID();
    private final Map<Pos, BlockType> blocks = new HashMap<>();
    private final Function<Pos, BlockType> terrain;
    public final List<PlayedSound> sounds = new ArrayList<>();
    public Dimension dimension = Dimension.NORMAL;
    public int minHeight = -64;
    public int maxHeight = 320;

    public FakeWorld(Function<Pos, BlockType> terrain) {
        this.terrain = terrain;
    }

    public void set(Pos p, BlockType type) {
        blocks.put(p, type);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public Dimension dimension() {
        return dimension;
    }

    @Override
    public int minHeight() {
        return minHeight;
    }

    @Override
    public int maxHeight() {
        return maxHeight;
    }

    @Override
    public int logicalHeight() {
        return maxHeight - minHeight;
    }

    @Override
    public boolean hasCeiling() {
        return dimension == Dimension.NETHER;
    }

    @Override
    public boolean isDayTime() {
        return true;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public BlockSnapshot chunkSnapshot(int chunkX, int chunkZ) {
        return new BlockSnapshot() {
            @Override
            public int typeId(int x, int y, int z) {
                return type((chunkX << 4) + x, y, (chunkZ << 4) + z).id();
            }

            @Override
            public boolean waterlogged(int x, int y, int z) {
                return false;
            }
        };
    }

    @Override
    public BlockType type(int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        BlockType t = blocks.get(p);
        return t != null ? t : terrain.apply(p);
    }

    @Override
    public Fluid fluid(int x, int y, int z) {
        BlockType t = type(x, y, z);
        return t == Blocks.WATER ? Fluid.WATER : t == Blocks.LAVA ? Fluid.LAVA : Fluid.EMPTY;
    }

    @Override
    public GameBlock block(int x, int y, int z) {
        return new Block(x, y, z);
    }

    @Override
    public int highestY(int x, int z, boolean noLeaves) {
        for (int y = maxHeight - 1; y >= minHeight; y--) {
            BlockType t = type(x, y, z);
            if (t.is(BlockTrait.SOLID) && !(noLeaves && t.is(BlockTrait.LEAVES))) {
                return y;
            }
        }
        return minHeight;
    }

    @Override
    public RayHit rayTraceBlocks(Vec3 from, Vec3 direction, double maxDistance, boolean fluids, boolean ignorePassable) {
        Vec3 d = direction.normalize();
        for (double t = 0; t <= maxDistance; t += 0.05) {
            Vec3 p = from.add(d.scale(t));
            Pos b = p.toPos();
            BlockType type = type(b.x(), b.y(), b.z());
            boolean hit = type.is(BlockTrait.SOLID) || (fluids && fluid(b.x(), b.y(), b.z()) != Fluid.EMPTY)
                    || (!ignorePassable && !type.is(BlockTrait.AIR));
            if (hit) {
                return new RayHit(b, Face.UP, p);
            }
        }
        return null;
    }

    @Override
    public void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
        sounds.add(new PlayedSound(at, sound, category, volume, pitch));
    }

    @Override
    public void spawnParticle(Particle particle, Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        // particles are only for players to see
    }

    @Override
    public void spawnBlockParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameBlock block) {
        // particles are only for players to see
    }

    @Override
    public Boolean gameRule(GameRule rule) {
        return Boolean.TRUE;
    }

    @Override
    public Difficulty difficulty() {
        return Difficulty.NORMAL;
    }

    /** The entities in this world, for the tests that need them. */
    public final List<GameEntity> entities = new ArrayList<>();

    @Override
    public List<? extends GamePlayer> players() {
        List<GamePlayer> out = new ArrayList<>();
        for (GameEntity e : entities) {
            if (e instanceof GamePlayer p) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public List<GameEntity> nearbyEntities(Box box) {
        List<GameEntity> out = new ArrayList<>();
        for (GameEntity e : entities) {
            if (e.boundingBox().overlaps(box)) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    public List<GameEntity> nearbyEntities(Vec3 center, double x, double y, double z) {
        return nearbyEntities(new Box(center.x() - x, center.y() - y, center.z() - z, center.x() + x, center.y() + y, center.z() + z));
    }

    /** A block of the fake world: a solid type has a full collision box; there are no states. */
    public final class Block implements GameBlock {
        private final int x;
        private final int y;
        private final int z;
        private boolean open;

        Block(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public GameWorld world() {
            return FakeWorld.this;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public BlockType type() {
            return FakeWorld.this.type(x, y, z);
        }

        @Override
        public void setType(BlockType type, boolean physics) {
            set(new Pos(x, y, z), type);
        }

        @Override
        public void place(BlockType type) {
            set(new Pos(x, y, z), type);
        }

        @Override
        public boolean isPassable() {
            return !type().is(BlockTrait.SOLID) || open;
        }

        @Override
        public boolean isReplaceable() {
            return type().is(BlockTrait.REPLACEABLE) || type().is(BlockTrait.AIR);
        }

        @Override
        public boolean isLiquid() {
            return fluid() != Fluid.EMPTY;
        }

        @Override
        public Fluid fluid() {
            return FakeWorld.this.fluid(x, y, z);
        }

        @Override
        public int fluidLevel() {
            return isLiquid() ? 0 : -1;
        }

        @Override
        public boolean isTopSturdy() {
            return type().is(BlockTrait.SOLID);
        }

        @Override
        public int lightLevel() {
            return 15;
        }

        @Override
        public int skyLight() {
            return y >= highestY(x, z, false) ? 15 : 0;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isOpenable() {
            BlockType t = type();
            return t.is(BlockTrait.DOOR) || t.is(BlockTrait.FENCE_GATE) || t.is(BlockTrait.TRAPDOOR);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void setOpen(boolean open) {
            this.open = open;
        }

        @Override
        public BedPart bedPart() {
            return BedPart.NONE;
        }

        @Override
        public void setBed(BlockType bed, Face facing, BedPart part) {
            set(new Pos(x, y, z), bed);
        }

        @Override
        public void setPortalX() {
            set(new Pos(x, y, z), Blocks.NETHER_PORTAL);
        }

        @Override
        public Box boundingBox() {
            return type().is(BlockTrait.SOLID) ? new Box(x, y, z, x + 1, y + 1, z + 1) : new Box(x, y, z, x, y, z);
        }

        @Override
        public SoundGroup soundGroup() {
            GameSound s = GameSound.of("block.stone.break");
            return new SoundGroup(s, s, s, 1f, 1f);
        }
    }
}
