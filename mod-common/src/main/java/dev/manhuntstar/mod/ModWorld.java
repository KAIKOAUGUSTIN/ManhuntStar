package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BlockSnapshot;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.BodySpec;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.Difficulty;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.Fluid;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameBoat;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameItemEntity;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameProjectile;
import dev.manhuntstar.platform.GameRule;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Particle;
import dev.manhuntstar.platform.Place;
import dev.manhuntstar.platform.RayHit;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.Vec3;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A Minecraft {@link ServerLevel} as a core {@link GameWorld}, doing what the Paper plugin's Bukkit calls
 * do. There's one instance per loaded level, so {@code ==} works on worlds.
 *
 * <p>Worlds are named the way Bukkit names them ({@code world}, {@code world_nether},
 * {@code world_the_end}; other dimensions by their id), so a config written for the plugin works.
 */
public final class ModWorld implements GameWorld {

    private static final Map<ResourceKey<Level>, ModWorld> CACHE = new ConcurrentHashMap<>();

    /**
     * The mod's chunk ticket: keeps chunks loaded and ticking and the dimension running, like a Bukkit plugin
     * ticket. It isn't registered, so it is never saved with the world.
     */
    private static final TicketType TICKET = new TicketType(0L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    /** Radius 2 around the chunk gives it level 31: loaded with entities ticking, as Bukkit's plugin tickets. */
    private static final int TICKET_RADIUS = 2;

    private final ServerLevel level;
    private final UUID id;
    private final Set<Long> tickets = new HashSet<>();

    private ModWorld(ServerLevel level) {
        this.level = level;
        this.id = UUID.nameUUIDFromBytes(("manhuntstar:" + level.dimension().identifier()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Forgets every world when the server stops, so a singleplayer world closed to the title screen isn't
     * kept in memory until the next one opens.
     */
    static void forgetAll() {
        CACHE.clear();
    }

    public static ModWorld of(Level level) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        ModWorld cached = CACHE.get(server.dimension());
        if (cached != null && cached.level == server) {
            return cached;
        }
        ModWorld fresh = new ModWorld(server);
        CACHE.put(server.dimension(), fresh);
        return fresh;
    }

    /** The level behind a core world. */
    public static ServerLevel vanilla(GameWorld world) {
        return world == null ? null : ((ModWorld) world).level;
    }

    public ServerLevel vanilla() {
        return level;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        String base = level.getServer().getWorldData().getLevelName();
        ResourceKey<Level> key = level.dimension();
        if (key == Level.OVERWORLD) {
            return base;
        }
        if (key == Level.NETHER) {
            return base + "_nether";
        }
        if (key == Level.END) {
            return base + "_the_end";
        }
        return key.identifier().toString();
    }

    @Override
    public Dimension dimension() {
        ResourceKey<Level> key = level.dimension();
        if (key == Level.OVERWORLD) {
            return Dimension.NORMAL;
        }
        if (key == Level.NETHER) {
            return Dimension.NETHER;
        }
        if (key == Level.END) {
            return Dimension.THE_END;
        }
        return Dimension.CUSTOM;
    }

    @Override
    public int minHeight() {
        return level.getMinY();
    }

    @Override
    public int maxHeight() {
        return level.getMaxY() + 1;
    }

    @Override
    public int logicalHeight() {
        return level.dimensionType().logicalHeight();
    }

    @Override
    public boolean hasCeiling() {
        return level.dimensionType().hasCeiling();
    }

    @Override
    public boolean isDayTime() {
        return level.isBrightOutside();
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    @Override
    public Vec3 spawnPosition() {
        LevelData.RespawnData r = level.getRespawnData();
        if (r.globalPos().dimension() != level.dimension()) {
            return new Vec3(0, level.getSeaLevel(), 0);
        }
        BlockPos p = r.pos();
        return new Vec3(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String chunkLoadLevel(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return "UNLOADED";
        }
        FullChunkStatus status = chunk.getFullStatus();
        return switch (status) {
            case INACCESSIBLE -> "INACCESSIBLE";
            case FULL -> "BORDER";
            case BLOCK_TICKING -> "TICKING";
            case ENTITY_TICKING -> "ENTITY_TICKING";
        };
    }

    @Override
    public BlockSnapshot chunkSnapshot(int chunkX, int chunkZ) {
        return new ModChunkSnapshot(level.getChunk(chunkX, chunkZ));
    }

    @Override
    public GameBlock block(int x, int y, int z) {
        return new ModBlock(this, new BlockPos(x, y, z));
    }

    @Override
    public BlockType type(int x, int y, int z) {
        return ModTypes.of(level.getBlockState(new BlockPos(x, y, z)).getBlock());
    }

    @Override
    public Fluid fluid(int x, int y, int z) {
        return fluid(level.getFluidState(new BlockPos(x, y, z)));
    }

    static Fluid fluid(FluidState state) {
        net.minecraft.world.level.material.Fluid f = state.getType();
        if (f == Fluids.WATER) {
            return Fluid.WATER;
        }
        if (f == Fluids.FLOWING_WATER) {
            return Fluid.FLOWING_WATER;
        }
        if (f == Fluids.LAVA) {
            return Fluid.LAVA;
        }
        if (f == Fluids.FLOWING_LAVA) {
            return Fluid.FLOWING_LAVA;
        }
        return Fluid.EMPTY;
    }

    @Override
    public int highestY(int x, int z, boolean noLeaves) {
        return level.getChunk(x >> 4, z >> 4).getHeight(noLeaves ? Heightmap.Types.MOTION_BLOCKING_NO_LEAVES : Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    @Override
    public RayHit rayTraceBlocks(Vec3 from, Vec3 direction, double maxDistance, boolean fluids, boolean ignorePassable) {
        if (maxDistance < 0) {
            return null;
        }
        Vec3 d = direction.normalize().scale(maxDistance);
        net.minecraft.world.phys.Vec3 start = ModConvert.vanilla(from);
        net.minecraft.world.phys.Vec3 end = start.add(d.x(), d.y(), d.z());
        BlockHitResult r = level.clip(new ClipContext(start, end, ignorePassable ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (r.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new RayHit(ModConvert.pos(r.getBlockPos()), ModConvert.face(r.getDirection()), ModConvert.vec(r.getLocation()));
    }

    @Override
    public void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
        level.playSound(null, at.x(), at.y(), at.z(), ModRegistry.sound(sound), ModRegistry.category(category), volume, pitch);
    }

    @Override
    public void spawnParticle(Particle particle, Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        level.sendParticles(ModRegistry.particle(particle), at.x(), at.y(), at.z(), count, offsetX, offsetY, offsetZ, extra);
    }

    @Override
    public void spawnBlockParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameBlock block) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ((ModBlock) block).state()), at.x(), at.y(), at.z(), count, offsetX, offsetY, offsetZ, extra);
    }

    @Override
    public void spawnItemParticle(Vec3 at, int count, double offsetX, double offsetY, double offsetZ, double extra, GameItem item) {
        ItemStack stack = ModItem.stack(item);
        ItemParticleOption option = stack.isEmpty() ? new ItemParticleOption(ParticleTypes.ITEM, stack.getItem())
                : new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack));
        level.sendParticles(option, at.x(), at.y(), at.z(), count, offsetX, offsetY, offsetZ, extra);
    }

    @Override
    public Boolean gameRule(GameRule rule) {
        return level.getGameRules().get(switch (rule) {
            case PVP -> GameRules.PVP;
            case NATURAL_HEALTH_REGENERATION -> GameRules.NATURAL_HEALTH_REGENERATION;
            case SHOW_DEATH_MESSAGES -> GameRules.SHOW_DEATH_MESSAGES;
        });
    }

    @Override
    public Difficulty difficulty() {
        return Difficulty.values()[level.getDifficulty().ordinal()];
    }

    @Override
    public List<? extends GamePlayer> players() {
        List<GamePlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            out.add(ModEntities.of(p));
        }
        return out;
    }

    @Override
    public List<GameEntity> entities() {
        List<GameEntity> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e != null && ModEntities.isValid(e)) {
                out.add(ModEntities.of(e));
            }
        }
        return out;
    }

    @Override
    public List<GameEntity> nearbyEntities(Box box) {
        return wrap(level.getEntities((Entity) null, ModConvert.aabb(box), e -> true));
    }

    @Override
    public List<GameEntity> nearbyEntities(Vec3 center, double x, double y, double z) {
        AABB box = new AABB(center.x() - x, center.y() - y, center.z() - z, center.x() + x, center.y() + y, center.z() + z);
        return wrap(level.getEntities((Entity) null, box, e -> true));
    }

    static List<GameEntity> wrap(List<Entity> entities) {
        List<GameEntity> out = new ArrayList<>(entities.size());
        for (Entity e : entities) {
            out.add(ModEntities.of(e));
        }
        return out;
    }

    @Override
    public GameItemEntity dropItemNaturally(Vec3 at, GameItem item) {
        double xs = Mth.nextDouble(level.getRandom(), -0.25, 0.25);
        double ys = Mth.nextDouble(level.getRandom(), -0.25, 0.25) - EntityTypes.ITEM.getHeight() / 2.0;
        double zs = Mth.nextDouble(level.getRandom(), -0.25, 0.25);
        ItemEntity drop = new ItemEntity(level, at.x() + xs, at.y() + ys, at.z() + zs, ModItem.stack(item).copy());
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        return (GameItemEntity) ModEntities.of(drop);
    }

    @Override
    public BotBody spawnBotBody(Place at, BodySpec spec) {
        Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (m == null) {
            throw new IllegalStateException("could not create a mannequin");
        }
        m.absSnapTo(at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        m.setYHeadRot(at.yaw());
        ModBotBody body = (ModBotBody) ModEntities.of(m);
        body.setPersistent(false);
        body.hideDescription();
        body.setProfile(spec.profile());
        m.setCustomName(ModServer.get().vanilla(spec.name()));
        m.setCustomNameVisible(spec.nameVisible());
        body.setImmovable(false);
        AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(spec.health());
        }
        body.setHealth(spec.health());
        body.setMark(spec.markKey(), spec.markValue());
        level.addFreshEntity(m);
        return body;
    }

    @Override
    public GameBoat spawnBoat(Place at) {
        Entity boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
        if (boat == null) {
            return null;
        }
        boat.snapTo(at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        level.addFreshEntity(boat);
        return ModEntities.of(boat) instanceof GameBoat b ? b : null;
    }

    @Override
    public GameEntity spawnTnt(Vec3 at, int fuseTicks, GameEntity source) {
        Entity owner = ModEntities.vanilla(source);
        PrimedTnt tnt = new PrimedTnt(level, at.x(), at.y(), at.z(), owner instanceof LivingEntity l ? l : null);
        tnt.setFuse(fuseTicks);
        level.addFreshEntity(tnt);
        return ModEntities.of(tnt);
    }

    @Override
    public GameProjectile spawnFirework(Vec3 at, GameItem rocket, GameLiving shooter, Vec3 velocity) {
        // the rocket's lifetime comes from its flight duration, as in the game
        FireworkRocketEntity fw = new FireworkRocketEntity(level, ModItem.stack(rocket), ModEntities.vanilla(shooter), at.x(), at.y(), at.z(), true);
        fw.setDeltaMovement(ModConvert.vanilla(velocity));
        MinecraftCompat.syncVelocity(fw);
        level.addFreshEntity(fw);
        return (GameProjectile) ModEntities.of(fw);
    }

    @Override
    public synchronized void addChunkTicket(int chunkX, int chunkZ) {
        if (tickets.add(ChunkPos.pack(chunkX, chunkZ))) {
            level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(chunkX, chunkZ), TICKET_RADIUS);
        }
    }

    @Override
    public synchronized void removeChunkTicket(int chunkX, int chunkZ) {
        if (tickets.remove(ChunkPos.pack(chunkX, chunkZ))) {
            level.getChunkSource().removeTicketWithRadius(TICKET, new ChunkPos(chunkX, chunkZ), TICKET_RADIUS);
        }
    }

    @Override
    public synchronized void removeAllChunkTickets() {
        for (long key : tickets) {
            level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.unpack(key), TICKET_RADIUS);
        }
        tickets.clear();
    }

    @Override
    public String toString() {
        return name();
    }
}
