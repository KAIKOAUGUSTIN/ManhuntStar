package dev.manhuntstar.mod;

import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.Effect;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.Particle;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.SoundGroup;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.SoundType;

/** Core's sounds, effects, enchantments, attributes, slots, damage types and particles as Minecraft's. */
public final class ModRegistry {

    private static final Map<Enchant, Holder<Enchantment>> ENCHANTMENTS = new EnumMap<>(Enchant.class);
    private static final Map<DamageKind, Holder<DamageType>> DAMAGE = new EnumMap<>(DamageKind.class);

    private ModRegistry() {
    }

    /** Forgets what came from the server's registries (a new server starts in the same game, as in singleplayer). */
    static synchronized void reset() {
        ENCHANTMENTS.clear();
        DAMAGE.clear();
    }

    public static synchronized Holder<Enchantment> enchantment(Enchant e) {
        return ENCHANTMENTS.computeIfAbsent(e, k -> ModServer.get().vanilla().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(k.key()))));
    }

    public static Holder<Attribute> attribute(Attr a) {
        return switch (a) {
            case ARMOR -> Attributes.ARMOR;
            case ARMOR_TOUGHNESS -> Attributes.ARMOR_TOUGHNESS;
            case ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case FOLLOW_RANGE -> Attributes.FOLLOW_RANGE;
            case MAX_HEALTH -> Attributes.MAX_HEALTH;
            case MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
        };
    }

    public static Holder<MobEffect> effect(Effect e) {
        return switch (e) {
            case ABSORPTION -> MobEffects.ABSORPTION;
            case BLINDNESS -> MobEffects.BLINDNESS;
            case DOLPHINS_GRACE -> MobEffects.DOLPHINS_GRACE;
            case FIRE_RESISTANCE -> MobEffects.FIRE_RESISTANCE;
            case JUMP_BOOST -> MobEffects.JUMP_BOOST;
            case REGENERATION -> MobEffects.REGENERATION;
            case SLOWNESS -> MobEffects.SLOWNESS;
            case SPEED -> MobEffects.SPEED;
            case STRENGTH -> MobEffects.STRENGTH;
            case WEAKNESS -> MobEffects.WEAKNESS;
        };
    }

    public static EquipmentSlot slot(EquipSlot s) {
        return switch (s) {
            case HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            case FEET -> EquipmentSlot.FEET;
            case LEGS -> EquipmentSlot.LEGS;
            case CHEST -> EquipmentSlot.CHEST;
            case HEAD -> EquipmentSlot.HEAD;
            case BODY -> EquipmentSlot.BODY;
            case SADDLE -> EquipmentSlot.SADDLE;
        };
    }

    public static EquipSlot slot(EquipmentSlot s) {
        return switch (s) {
            case MAINHAND -> EquipSlot.HAND;
            case OFFHAND -> EquipSlot.OFF_HAND;
            case FEET -> EquipSlot.FEET;
            case LEGS -> EquipSlot.LEGS;
            case CHEST -> EquipSlot.CHEST;
            case HEAD -> EquipSlot.HEAD;
            case BODY -> EquipSlot.BODY;
            case SADDLE -> EquipSlot.SADDLE;
        };
    }

    private static ResourceKey<DamageType> damageKey(DamageKind kind) {
        return switch (kind) {
            case PLAYER_ATTACK -> DamageTypes.PLAYER_ATTACK;
            case ENDER_PEARL -> DamageTypes.ENDER_PEARL;
            case STARVE -> DamageTypes.STARVE;
            case FALL -> DamageTypes.FALL;
            case ON_FIRE -> DamageTypes.ON_FIRE;
            case IN_FIRE -> DamageTypes.IN_FIRE;
            case LAVA -> DamageTypes.LAVA;
            case OUT_OF_WORLD -> DamageTypes.FELL_OUT_OF_WORLD;
            case GENERIC_KILL -> DamageTypes.GENERIC_KILL;
            case MAGIC -> DamageTypes.MAGIC;
            case INDIRECT_MAGIC -> DamageTypes.INDIRECT_MAGIC;
            case DROWN -> DamageTypes.DROWN;
            case MOB_ATTACK -> DamageTypes.MOB_ATTACK;
            case SPEAR -> DamageTypes.SPEAR;
            case OTHER -> DamageTypes.GENERIC;
        };
    }

    public static synchronized Holder<DamageType> damageType(DamageKind kind) {
        return DAMAGE.computeIfAbsent(kind, k -> ModServer.get().vanilla().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(damageKey(k)));
    }

    /** The core kind of a damage type ({@link DamageKind#OTHER} for the ones core doesn't name). */
    public static DamageKind kind(Holder<DamageType> type) {
        for (DamageKind k : DamageKind.values()) {
            if (k != DamageKind.OTHER && type.is(damageKey(k))) {
                return k;
            }
        }
        return DamageKind.OTHER;
    }

    /** The sound event with the core sound's id (kept in the core sound). */
    public static Holder<SoundEvent> sound(GameSound s) {
        if (s.handle() instanceof Holder<?> h) {
            @SuppressWarnings("unchecked")
            Holder<SoundEvent> sound = (Holder<SoundEvent>) h;
            return sound;
        }
        Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace(s.key()))
                .orElseThrow(() -> new IllegalArgumentException("unknown sound: " + s.key()));
        s.setHandle(sound);
        return sound;
    }

    public static GameSound of(SoundEvent event) {
        Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(event);
        GameSound s = GameSound.of(id == null ? event.location().getPath() : id.getPath());
        if (s.handle() == null) {
            s.setHandle(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event));
        }
        return s;
    }

    public static SoundSource category(SoundCategory c) {
        return SoundSource.valueOf(c.name());
    }

    public static SoundGroup of(SoundType t) {
        return new SoundGroup(of(t.getBreakSound()), of(t.getHitSound()), of(t.getPlaceSound()), t.getVolume(), t.getPitch());
    }

    public static SimpleParticleType particle(Particle p) {
        return switch (p) {
            case CRIT -> ParticleTypes.CRIT;
            case ENCHANTED_HIT -> ParticleTypes.ENCHANTED_HIT;
            case SWEEP_ATTACK -> ParticleTypes.SWEEP_ATTACK;
            case CLOUD -> ParticleTypes.CLOUD;
            case HEART -> ParticleTypes.HEART;
            case FIREWORK -> ParticleTypes.FIREWORK;
        };
    }
}
