package dev.manhuntstar.paper;

import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.ItemTrait;
import dev.manhuntstar.platform.ItemType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Registers the game's items in core ({@link ItemType}), with the Bukkit {@link Material} number as the
 * type number, and converts items, enchantments, attributes and slots between Bukkit and core.
 */
public final class PaperItems {

    private static final ItemType[] TYPES;
    private static final Material[] MATERIALS;

    static {
        Material[] all = Material.values();
        MATERIALS = all;
        TYPES = new ItemType[all.length];
        for (Material m : all) {
            if (m.isItem() && !m.isLegacy()) {
                TYPES[m.ordinal()] = ItemType.register(m.ordinal(), m.name(), m.getMaxDurability(), traits(m));
            }
        }
    }

    private PaperItems() {
    }

    /** Registers the item types in core (before any item is read). */
    public static void init() {
        // the work is done by the static block, once
    }

    private static ItemTrait[] traits(Material m) {
        List<ItemTrait> out = new ArrayList<>();
        if (Tag.ITEMS_SWORDS.isTagged(m)) {
            out.add(ItemTrait.SWORD);
        }
        if (Tag.ITEMS_AXES.isTagged(m)) {
            out.add(ItemTrait.AXE);
        }
        if (Tag.ITEMS_PICKAXES.isTagged(m)) {
            out.add(ItemTrait.PICKAXE);
        }
        if (Tag.ITEMS_SHOVELS.isTagged(m)) {
            out.add(ItemTrait.SHOVEL);
        }
        if (Tag.ITEMS_HOES.isTagged(m)) {
            out.add(ItemTrait.HOE);
        }
        if (Tag.ITEMS_SPEARS.isTagged(m)) {
            out.add(ItemTrait.SPEAR);
        }
        return out.toArray(ItemTrait[]::new);
    }

    /** The core type of an item material (null for materials that aren't items). */
    public static ItemType of(Material m) {
        return TYPES[m.ordinal()];
    }

    /** The Bukkit material of a core item type. */
    public static Material material(ItemType type) {
        return MATERIALS[type.id()];
    }

    /** A Bukkit stack as a core one (null counts as empty). The core item changes the same stack. */
    public static GameItem of(ItemStack stack) {
        return new PaperItem(stack == null ? ItemStack.empty() : stack);
    }

    /** The Bukkit stack behind a core one (null counts as empty). */
    public static ItemStack stack(GameItem item) {
        return item == null ? ItemStack.empty() : ((PaperItem) item).stack;
    }

    public static Enchantment enchantment(Enchant e) {
        return switch (e) {
            case BANE_OF_ARTHROPODS -> Enchantment.BANE_OF_ARTHROPODS;
            case EFFICIENCY -> Enchantment.EFFICIENCY;
            case FIRE_ASPECT -> Enchantment.FIRE_ASPECT;
            case KNOCKBACK -> Enchantment.KNOCKBACK;
            case LOYALTY -> Enchantment.LOYALTY;
            case PROTECTION -> Enchantment.PROTECTION;
            case SHARPNESS -> Enchantment.SHARPNESS;
            case SMITE -> Enchantment.SMITE;
            case SWEEPING_EDGE -> Enchantment.SWEEPING_EDGE;
        };
    }

    public static Attribute attribute(Attr a) {
        return switch (a) {
            case ARMOR -> Attribute.ARMOR;
            case ARMOR_TOUGHNESS -> Attribute.ARMOR_TOUGHNESS;
            case ATTACK_DAMAGE -> Attribute.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attribute.ATTACK_SPEED;
            case FOLLOW_RANGE -> Attribute.FOLLOW_RANGE;
            case MAX_HEALTH -> Attribute.MAX_HEALTH;
            case MOVEMENT_SPEED -> Attribute.MOVEMENT_SPEED;
        };
    }

    public static EquipmentSlot slot(EquipSlot s) {
        return switch (s) {
            case HAND -> EquipmentSlot.HAND;
            case OFF_HAND -> EquipmentSlot.OFF_HAND;
            case FEET -> EquipmentSlot.FEET;
            case LEGS -> EquipmentSlot.LEGS;
            case CHEST -> EquipmentSlot.CHEST;
            case HEAD -> EquipmentSlot.HEAD;
            case BODY -> EquipmentSlot.BODY;
            case SADDLE -> EquipmentSlot.SADDLE;
        };
    }

    /** The core slot of a Bukkit one (null for slots core doesn't know). */
    public static EquipSlot slot(EquipmentSlot s) {
        return switch (s) {
            case HAND -> EquipSlot.HAND;
            case OFF_HAND -> EquipSlot.OFF_HAND;
            case FEET -> EquipSlot.FEET;
            case LEGS -> EquipSlot.LEGS;
            case CHEST -> EquipSlot.CHEST;
            case HEAD -> EquipSlot.HEAD;
            case BODY -> EquipSlot.BODY;
            case SADDLE -> EquipSlot.SADDLE;
            default -> null;
        };
    }
}
