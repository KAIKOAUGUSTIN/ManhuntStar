package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BedPart;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Fluid;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.SoundGroup;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of a {@link ModWorld} as a core {@link GameBlock}, doing what Bukkit's {@code CraftBlock} does:
 * reading it reads the world as it is now.
 */
public final class ModBlock implements GameBlock {

    /** Update flags of a change that updates the neighbors (CraftBlock: 2 | 1). */
    private static final int PHYSICS = Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS;
    /** Update flags of a change that doesn't (CraftBlock: 2 | 528, no neighbor shape updates or drops). */
    private static final int NO_PHYSICS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private final ModWorld world;
    private final BlockPos pos;

    ModBlock(ModWorld world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    public static ModBlock of(ServerLevel level, BlockPos pos) {
        return new ModBlock(ModWorld.of(level), pos.immutable());
    }

    public BlockPos vanillaPos() {
        return pos;
    }

    private ServerLevel level() {
        return world.vanilla();
    }

    BlockState state() {
        return level().getBlockState(pos);
    }

    private boolean set(BlockState state, boolean physics) {
        return level().setBlock(pos, state, physics ? PHYSICS : NO_PHYSICS);
    }

    @Override
    public GameWorld world() {
        return world;
    }

    @Override
    public int x() {
        return pos.getX();
    }

    @Override
    public int y() {
        return pos.getY();
    }

    @Override
    public int z() {
        return pos.getZ();
    }

    @Override
    public BlockType type() {
        return ModTypes.of(state().getBlock());
    }

    @Override
    public void setType(BlockType type, boolean physics) {
        set(ModTypes.block(type).defaultBlockState(), physics);
    }

    @Override
    public void place(BlockType type) {
        BlockState s = ModTypes.block(type).defaultBlockState();
        if (s.getBlock() instanceof LeavesBlock) {
            // with no log nearby the leaves would decay on their own and drop saplings and apples
            s = s.setValue(LeavesBlock.PERSISTENT, true);
        }
        set(s, true);
    }

    @Override
    public boolean breakNaturally(GameItem tool, boolean effects) {
        ServerLevel level = level();
        BlockState state = state();
        Block block = state.getBlock();
        ItemStack item = ModItem.stack(tool).copy();
        boolean result = false;
        if (!state.isAir() && (!state.requiresCorrectToolForDrops() || item.isCorrectToolForDrops(state))) {
            // the drops, without the experience (Bukkit's breakNaturally drops none)
            Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, item).forEach(s -> Block.popResource(level, pos, s));
            state.spawnAfterBreak(level, pos, item, false);
            result = true;
        }
        if (result && effects) {
            if (block instanceof BaseFireBlock) {
                level.levelEvent(1009, pos, 0);
            } else {
                level.levelEvent(2014, pos, Block.getId(state));
            }
        }
        boolean destroyed = level.removeBlock(pos, false);
        if (destroyed) {
            block.destroy(level, pos, state);
        }
        if (result) {
            if (block instanceof IceBlock) {
                afterIceBroken(level, item);
            } else if (block instanceof TurtleEggBlock) {
                afterEggBroken(level, state);
            }
        }
        return destroyed && result;
    }

    /** Broken ice turns into water over a solid block or a liquid, as when a player breaks it. */
    private void afterIceBroken(ServerLevel level, ItemStack tool) {
        if (EnchantmentHelper.hasTag(tool, EnchantmentTags.PREVENTS_ICE_MELTING)) {
            return;
        }
        if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        BlockState below = level.getBlockState(pos.below());
        if (MinecraftCompat.meltsIceAbove(below) || below.liquid()) {
            level.setBlockAndUpdate(pos, IceBlock.meltsInto());
        }
    }

    /** One egg less of a clutch of turtle eggs, as when a player breaks it. */
    private void afterEggBroken(ServerLevel level, BlockState state) {
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        int eggs = state.getValue(TurtleEggBlock.EGGS);
        if (eggs <= 1) {
            level.destroyBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(TurtleEggBlock.EGGS, eggs - 1), Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
            level.levelEvent(2001, pos, Block.getId(state));
        }
    }

    @Override
    public boolean isPassable() {
        return state().getCollisionShape(level(), pos).isEmpty();
    }

    @Override
    public boolean isReplaceable() {
        return state().canBeReplaced();
    }

    @Override
    public boolean isLiquid() {
        return state().liquid();
    }

    @Override
    public Fluid fluid() {
        return ModWorld.fluid(level().getFluidState(pos));
    }

    @Override
    public int fluidLevel() {
        BlockState s = state();
        for (Property<?> p : s.getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("level")) {
                return s.getValue(ip);
            }
        }
        return -1;
    }

    @Override
    public boolean isTopSturdy() {
        return state().isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP, SupportType.FULL);
    }

    @Override
    public int lightLevel() {
        return level().getMaxLocalRawBrightness(pos);
    }

    @Override
    public int skyLight() {
        return level().getBrightness(LightLayer.SKY, pos);
    }

    @Override
    public boolean isLit() {
        BlockState s = state();
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    @Override
    public boolean isOpenable() {
        return state().hasProperty(BlockStateProperties.OPEN);
    }

    @Override
    public boolean isOpen() {
        BlockState s = state();
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    @Override
    public void setOpen(boolean open) {
        BlockState s = state();
        if (s.hasProperty(BlockStateProperties.OPEN)) {
            set(s.setValue(BlockStateProperties.OPEN, open), true);
        }
    }

    @Override
    public BedPart bedPart() {
        BlockState s = state();
        if (s.hasProperty(BlockStateProperties.BED_PART)) {
            return s.getValue(BlockStateProperties.BED_PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT ? BedPart.FOOT : BedPart.HEAD;
        }
        return BedPart.NONE;
    }

    @Override
    public void setBed(BlockType bed, Face facing, BedPart part) {
        BlockState s = ModTypes.block(bed).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, ModConvert.direction(facing))
                .setValue(BlockStateProperties.BED_PART, part == BedPart.HEAD
                        ? net.minecraft.world.level.block.state.properties.BedPart.HEAD
                        : net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        set(s, false);
    }

    @Override
    public void setPortalX() {
        set(Blocks.NETHER_PORTAL.defaultBlockState(), false);
        BlockState s = state();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            set(s.setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X), false);
        }
    }

    @Override
    public Box boundingBox() {
        VoxelShape shape = state().getShape(level(), pos);
        if (shape.isEmpty()) {
            return new Box(0, 0, 0, 0, 0, 0);
        }
        AABB b = shape.bounds();
        return new Box(x() + b.minX, y() + b.minY, z() + b.minZ, x() + b.maxX, y() + b.maxY, z() + b.maxZ);
    }

    @Override
    public SoundGroup soundGroup() {
        return ModRegistry.of(state().getSoundType());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ModBlock other && other.world == world && other.pos.equals(pos);
    }

    @Override
    public int hashCode() {
        return 31 * world.hashCode() + pos.hashCode();
    }

    @Override
    public String toString() {
        return "ModBlock{" + world.name() + "," + pos.toShortString() + ",type=" + type() + "}";
    }
}
