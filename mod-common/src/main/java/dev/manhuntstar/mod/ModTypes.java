package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.ItemTrait;
import dev.manhuntstar.platform.ItemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Registers the game's blocks, items and entity types in core ({@link BlockType}, {@link ItemType},
 * {@link EntityKind}), named like Bukkit's materials (the id in capitals) and with the same traits the
 * Paper plugin gives them, and converts between them and Minecraft's.
 */
public final class ModTypes {

    private static BlockType[] blocks = new BlockType[0];
    private static Block[] blockById = new Block[0];
    private static ItemType[] items = new ItemType[0];
    private static Item[] itemById = new Item[0];

    private ModTypes() {
    }

    /** The name core uses for a game id: the path in capitals (other namespaces in front, as NAMESPACE_PATH). */
    static String name(Identifier id) {
        String path = id.getPath().toUpperCase(Locale.ROOT).replace('/', '_').replace('.', '_');
        return id.getNamespace().equals("minecraft") ? path : id.getNamespace().toUpperCase(Locale.ROOT) + "_" + path;
    }

    /** Registers every type (on the server thread, once the tags are loaded and a world exists). */
    public static synchronized void init(ServerLevel level) {
        int blockCount = BuiltInRegistries.BLOCK.size();
        BlockType[] bt = new BlockType[blockCount];
        Block[] bb = new Block[blockCount];
        for (Block b : BuiltInRegistries.BLOCK) {
            int id = BuiltInRegistries.BLOCK.getId(b);
            BlockState s = b.defaultBlockState();
            bt[id] = BlockType.register(id, name(BuiltInRegistries.BLOCK.getKey(b)), s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    b.getFriction(), traits(s));
            bb[id] = b;
        }
        blocks = bt;
        blockById = bb;

        int itemCount = BuiltInRegistries.ITEM.size();
        ItemType[] it = new ItemType[itemCount];
        Item[] ii = new Item[itemCount];
        for (Item i : BuiltInRegistries.ITEM) {
            int id = BuiltInRegistries.ITEM.getId(i);
            it[id] = ItemType.register(id, name(BuiltInRegistries.ITEM.getKey(i)), i.components().getOrDefault(DataComponents.MAX_DAMAGE, 0), traits(i));
            ii[id] = i;
        }
        items = it;
        itemById = ii;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            EntityKind.register(name(BuiltInRegistries.ENTITY_TYPE.getKey(type)), traits(type, level));
        }
    }

    private static BlockTrait[] traits(BlockState s) {
        List<BlockTrait> out = new ArrayList<>();
        if (s.isAir()) {
            out.add(BlockTrait.AIR);
        }
        if (MinecraftCompat.blocksMotion(s)) {
            out.add(BlockTrait.SOLID);
        }
        if (s.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            out.add(BlockTrait.OCCLUDING);
        }
        if (s.is(BlockTags.CLIMBABLE)) {
            out.add(BlockTrait.CLIMBABLE);
        }
        if (s.is(BlockTags.LEAVES)) {
            out.add(BlockTrait.LEAVES);
        }
        if (s.canBeReplaced()) {
            out.add(BlockTrait.REPLACEABLE);
        }
        if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
            out.add(BlockTrait.WATERLOGGABLE);
        }
        if (s.is(BlockTags.DOORS)) {
            out.add(BlockTrait.DOOR);
        }
        if (s.is(BlockTags.FENCE_GATES)) {
            out.add(BlockTrait.FENCE_GATE);
        }
        if (s.is(BlockTags.TRAPDOORS)) {
            out.add(BlockTrait.TRAPDOOR);
        }
        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            out.add(BlockTrait.MINEABLE_PICKAXE);
        }
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) {
            out.add(BlockTrait.MINEABLE_AXE);
        }
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            out.add(BlockTrait.MINEABLE_SHOVEL);
        }
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) {
            out.add(BlockTrait.MINEABLE_HOE);
        }
        return out.toArray(BlockTrait[]::new);
    }

    private static ItemTrait[] traits(Item i) {
        List<ItemTrait> out = new ArrayList<>();
        var holder = i.builtInRegistryHolder();
        if (holder.is(ItemTags.SWORDS)) {
            out.add(ItemTrait.SWORD);
        }
        if (holder.is(ItemTags.AXES)) {
            out.add(ItemTrait.AXE);
        }
        if (holder.is(ItemTags.PICKAXES)) {
            out.add(ItemTrait.PICKAXE);
        }
        if (holder.is(ItemTags.SHOVELS)) {
            out.add(ItemTrait.SHOVEL);
        }
        if (holder.is(ItemTags.HOES)) {
            out.add(ItemTrait.HOE);
        }
        if (holder.is(ItemTags.SPEARS)) {
            out.add(ItemTrait.SPEAR);
        }
        return out.toArray(ItemTrait[]::new);
    }

    /**
     * The traits of an entity type, from the class of an entity of that type (made only to look at it and
     * never added to the world, as the game does before spawning one).
     */
    private static EntityTrait[] traits(EntityType<?> type, ServerLevel level) {
        List<EntityTrait> out = new ArrayList<>();
        if (type == EntityTypes.PLAYER) {
            out.add(EntityTrait.LIVING);
            out.add(EntityTrait.PLAYER);
        } else {
            Entity sample = null;
            try {
                sample = type.create(level, EntitySpawnReason.LOAD);
            } catch (Throwable ignored) {
                // a type that can't be made this way: no traits from its class
            }
            if (sample != null) {
                add(out, sample, LivingEntity.class, EntityTrait.LIVING);
                add(out, sample, Player.class, EntityTrait.PLAYER);
                add(out, sample, Mob.class, EntityTrait.MOB);
                add(out, sample, Enemy.class, EntityTrait.ENEMY);
                add(out, sample, Projectile.class, EntityTrait.PROJECTILE);
                add(out, sample, AbstractArrow.class, EntityTrait.ARROW);
                add(out, sample, ThrownTrident.class, EntityTrait.TRIDENT);
                add(out, sample, ThrownEnderpearl.class, EntityTrait.ENDER_PEARL);
                add(out, sample, FireworkRocketEntity.class, EntityTrait.FIREWORK);
                add(out, sample, PrimedTnt.class, EntityTrait.TNT);
                add(out, sample, ItemEntity.class, EntityTrait.ITEM);
                add(out, sample, ExperienceOrb.class, EntityTrait.EXPERIENCE_ORB);
                if (sample instanceof VehicleEntity || sample instanceof AbstractHorse || sample instanceof ItemSteerable) {
                    out.add(EntityTrait.VEHICLE);
                }
                add(out, sample, AbstractBoat.class, EntityTrait.BOAT);
                add(out, sample, AbstractHorse.class, EntityTrait.HORSE);
                add(out, sample, EnderDragon.class, EntityTrait.ENDER_DRAGON);
                add(out, sample, MinecraftCompat.ENDERMAN, EntityTrait.ENDERMAN);
                add(out, sample, Spider.class, EntityTrait.SPIDER);
                add(out, sample, Slime.class, EntityTrait.SLIME);
                add(out, sample, Warden.class, EntityTrait.WARDEN);
                add(out, sample, AbstractPiglin.class, EntityTrait.PIGLIN_FAMILY);
                add(out, sample, Piglin.class, EntityTrait.PIGLIN);
                add(out, sample, ZombifiedPiglin.class, EntityTrait.ZOMBIFIED_PIGLIN);
                add(out, sample, Hoglin.class, EntityTrait.HOGLIN);
                add(out, sample, Zoglin.class, EntityTrait.ZOGLIN);
                add(out, sample, Breeze.class, EntityTrait.BREEZE);
                add(out, sample, Creaking.class, EntityTrait.CREAKING);
                add(out, sample, MagmaCube.class, EntityTrait.MAGMA_CUBE);
                add(out, sample, Mannequin.class, EntityTrait.MANNEQUIN);
                add(out, sample, ArmorStand.class, EntityTrait.ARMOR_STAND);
            }
        }
        if (type.builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            out.add(EntityTrait.SENSITIVE_TO_SMITE);
        }
        if (type.builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            out.add(EntityTrait.SENSITIVE_TO_BANE);
        }
        return out.toArray(EntityTrait[]::new);
    }

    private static void add(List<EntityTrait> out, Entity e, Class<?> c, EntityTrait trait) {
        if (c.isInstance(e)) {
            out.add(trait);
        }
    }

    public static BlockType of(Block b) {
        return blocks[BuiltInRegistries.BLOCK.getId(b)];
    }

    public static Block block(BlockType type) {
        return blockById[type.id()];
    }

    public static ItemType of(Item i) {
        return items[BuiltInRegistries.ITEM.getId(i)];
    }

    public static Item item(ItemType type) {
        return itemById[type.id()];
    }

    public static EntityKind of(EntityType<?> type) {
        return EntityKind.of(name(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
    }
}
