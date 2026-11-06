package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BedPart;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Fluid;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.SoundGroup;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Face;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BoundingBox;

/** A Bukkit {@link Block} as a core {@link GameBlock}: every call is the Bukkit one. */
public final class PaperBlock implements GameBlock {

    private final PaperWorld world;
    private final Block block;

    PaperBlock(PaperWorld world, Block block) {
        this.world = world;
        this.block = block;
    }

    public static PaperBlock of(Block block) {
        return new PaperBlock(PaperWorld.of(block.getWorld()), block);
    }

    public Block bukkit() {
        return block;
    }

    @Override
    public GameWorld world() {
        return world;
    }

    @Override
    public int x() {
        return block.getX();
    }

    @Override
    public int y() {
        return block.getY();
    }

    @Override
    public int z() {
        return block.getZ();
    }

    @Override
    public BlockType type() {
        return PaperBlocks.of(block.getType());
    }

    @Override
    public void setType(BlockType type, boolean physics) {
        block.setType(PaperBlocks.material(type), physics);
    }

    @Override
    public void place(BlockType type) {
        BlockData data = PaperBlocks.material(type).createBlockData();
        if (data instanceof Leaves leaves) {
            // with no log nearby the leaves would decay on their own and drop saplings and apples
            leaves.setPersistent(true);
        }
        block.setBlockData(data, true);
    }

    @Override
    public boolean isPassable() {
        return block.isPassable();
    }

    @Override
    public boolean isReplaceable() {
        return block.isReplaceable();
    }

    @Override
    public boolean isLiquid() {
        return block.isLiquid();
    }

    @Override
    public Fluid fluid() {
        return world.fluid(block.getX(), block.getY(), block.getZ());
    }

    @Override
    public int fluidLevel() {
        return block.getBlockData() instanceof Levelled l ? l.getLevel() : -1;
    }

    @Override
    public boolean isTopSturdy() {
        return block.getBlockData().isFaceSturdy(org.bukkit.block.BlockFace.UP, BlockSupport.FULL);
    }

    @Override
    public int lightLevel() {
        return block.getLightLevel();
    }

    @Override
    public int skyLight() {
        return block.getLightFromSky();
    }

    @Override
    public boolean isLit() {
        return block.getBlockData() instanceof Lightable l && l.isLit();
    }

    @Override
    public boolean isOpenable() {
        return block.getBlockData() instanceof Openable;
    }

    @Override
    public boolean isOpen() {
        return block.getBlockData() instanceof Openable o && o.isOpen();
    }

    @Override
    public void setOpen(boolean open) {
        if (block.getBlockData() instanceof Openable o) {
            o.setOpen(open);
            block.setBlockData(o, true);
        }
    }

    @Override
    public BedPart bedPart() {
        if (block.getBlockData() instanceof Bed bed) {
            return bed.getPart() == Bed.Part.FOOT ? BedPart.FOOT : BedPart.HEAD;
        }
        return BedPart.NONE;
    }

    @Override
    public void setBed(BlockType bed, Face facing, BedPart part) {
        Bed data = (Bed) PaperBlocks.material(bed).createBlockData();
        data.setFacing(PaperBlocks.face(facing));
        data.setPart(part == BedPart.HEAD ? Bed.Part.HEAD : Bed.Part.FOOT);
        block.setBlockData(data, false);
    }

    @Override
    public void setPortalX() {
        block.setType(Material.NETHER_PORTAL, false);
        if (block.getBlockData() instanceof Orientable o) {
            o.setAxis(Axis.X);
            block.setBlockData(o, false);
        }
    }

    @Override
    public Box boundingBox() {
        BoundingBox b = block.getBoundingBox();
        return new Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ());
    }

    @Override
    public SoundGroup soundGroup() {
        return PaperSounds.of(block.getBlockData().getSoundGroup());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PaperBlock other && other.block.equals(block);
    }

    @Override
    public int hashCode() {
        return block.hashCode();
    }

    @Override
    public String toString() {
        return block.toString();
    }
}
