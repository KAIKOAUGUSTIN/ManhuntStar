package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BlockSnapshot;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.Fluid;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.RayHit;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.platform.Difficulty;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameRule;
import dev.manhuntstar.platform.Particle;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Locations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A Bukkit {@link World} as a core {@link GameWorld}: every call is the Bukkit one. There's one instance
 * per loaded world, so {@code ==} behaves like it does on Bukkit worlds.
 */
public final class PaperWorld implements GameWorld {

    private static final Map<UUID, PaperWorld> CACHE = new ConcurrentHashMap<>();

    private final World world;

    private PaperWorld(World world) {
        this.world = world;
    }

    public static PaperWorld of(World world) {
        if (world == null) {
            return null;
        }
        PaperWorld cached = CACHE.get(world.getUID());
        if (cached != null && cached.world == world) {
            return cached;
        }
        PaperWorld fresh = new PaperWorld(world);
        CACHE.put(world.getUID(), fresh);
        return fresh;
    }

    /** The Bukkit world behind a core world. */
    public static World bukkit(GameWorld world) {
        return world == null ? null : ((PaperWorld) world).world;
    }

    public World bukkit() {
        return world;
    }

    @Override
    public UUID id() {
        return world.getUID();
    }

    @Override
    public String name() {
        return world.getName();
    }

    @Override
    public Dimension dimension() {
        return switch (world.getEnvironment()) {
            case NORMAL -> Dimension.NORMAL;
            case NETHER -> Dimension.NETHER;
            case THE_END -> Dimension.THE_END;
            default -> Dimension.CUSTOM;
        };
    }

    @Override
    public int minHeight() {
        return world.getMinHeight();
    }

    @Override
    public int maxHeight() {
        return world.getMaxHeight();
    }

    @Override
    public int logicalHeight() {
        return world.getLogicalHeight();
    }

    @Override
    public boolean hasCeiling() {
        return world.hasCeiling();
    }

    @Override
    public boolean isDayTime() {
        return world.isDayTime();
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public BlockSnapshot chunkSnapshot(int chunkX, int chunkZ) {
        return new PaperChunkSnapshot(world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));
    }

    @Override
    public GameBlock block(int x, int y, int z) {
        return new PaperBlock(this, world.getBlockAt(x, y, z));
    }

    @Override
    public BlockType type(int x, int y, int z) {
        return PaperBlocks.of(world.getType(x, y, z));
    }

    @Override
    public Fluid fluid(int x, int y, int z) {
        org.bukkit.Fluid f = world.getFluidData(x, y, z).getFluidType();
        if (f == org.bukkit.Fluid.WATER) {
            return Fluid.WATER;
        }
        if (f == org.bukkit.Fluid.FLOWING_WATER) {
            return Fluid.FLOWING_WATER;
        }
        if (f == org.bukkit.Fluid.LAVA) {
            return Fluid.LAVA;
        }
        if (f == org.bukkit.Fluid.FLOWING_LAVA) {
            return Fluid.FLOWING_LAVA;
        }
        return Fluid.EMPTY;
    }

    @Override
    public int highestY(int x, int z, boolean noLeaves) {
        return world.getHighestBlockYAt(x, z, noLeaves ? HeightMap.MOTION_BLOCKING_NO_LEAVES : HeightMap.MOTION_BLOCKING);
    }

    @Override
    public RayHit rayTraceBlocks(Vec3 from, Vec3 direction, double maxDistance, boolean fluids, boolean ignorePassable) {
        RayTraceResult r = world.rayTraceBlocks(new Location(world, from.x(), from.y(), from.z()),
                new Vector(direction.x(), direction.y(), direction.z()), maxDistance,
                fluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, ignorePassable);
        if (r == null || r.getHitBlock() == null) {
            return null;
        }
        Block b = r.getHitBlock();
        Vector p = r.getHitPosition();
        return new RayHit(new dev.manhuntstar.util.Pos(b.getX(), b.getY(), b.getZ()),
                r.getHitBlockFace() == null ? null : PaperBlocks.face(r.getHitBlockFace()), new Vec3(p.getX(), p.getY(), p.getZ()));
    }

    @Override
    public void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
        world.playSound(new Location(world, at.x(), at.y(), at.z()), PaperSounds.sound(sound), PaperSounds.category(category), volume, pitch);
    }

    @Override
    public void spawnParticle(Particle particle, Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        world.spawnParticle(PaperParticles.of(particle), at.x(), at.y(), at.z(), count, offsetX, offsetY, offsetZ, extra);
    }

    @Override
    public void spawnBlockParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameBlock block) {
        world.spawnParticle(org.bukkit.Particle.BLOCK, at.x(), at.y(), at.z(), count, offsetX, offsetY, offsetZ, extra,
                ((PaperBlock) block).bukkit().getBlockData());
    }

    @Override
    public Boolean gameRule(GameRule rule) {
        return world.getGameRuleValue(switch (rule) {
            case PVP -> GameRules.PVP;
            case NATURAL_HEALTH_REGENERATION -> GameRules.NATURAL_HEALTH_REGENERATION;
            case SHOW_DEATH_MESSAGES -> GameRules.SHOW_DEATH_MESSAGES;
        });
    }

    @Override
    public Difficulty difficulty() {
        return Difficulty.values()[world.getDifficulty().ordinal()];
    }

    @Override
    public List<? extends GamePlayer> players() {
        List<GamePlayer> out = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            out.add(PaperEntities.of(p));
        }
        return out;
    }

    @Override
    public List<GameEntity> nearbyEntities(Box box) {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(Locations.boundingBox(box))) {
            out.add(PaperEntities.of(e));
        }
        return out;
    }

    @Override
    public List<GameEntity> nearbyEntities(Vec3 center, double x, double y, double z) {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(new Location(world, center.x(), center.y(), center.z()), x, y, z)) {
            out.add(PaperEntities.of(e));
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PaperWorld other && other.world.equals(world);
    }

    @Override
    public int hashCode() {
        return world.hashCode();
    }

    @Override
    public String toString() {
        return world.getName();
    }
}
