package dev.manhuntstar.bot;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

/** Pontuação de espadas, picaretas e armaduras para escolher o melhor equipamento. */
public final class GearScoring {

    private static final BlockData STONE = Material.STONE.createBlockData();

    private GearScoring() {
    }

    public static boolean isEmpty(ItemStack s) {
        return s == null || s.isEmpty();
    }

    public static boolean isSword(ItemStack s) {
        return !isEmpty(s) && Tag.ITEMS_SWORDS.isTagged(s.getType());
    }

    public static boolean isPickaxe(ItemStack s) {
        return !isEmpty(s) && Tag.ITEMS_PICKAXES.isTagged(s.getType());
    }

    public static boolean isAxe(ItemStack s) {
        return !isEmpty(s) && Tag.ITEMS_AXES.isTagged(s.getType());
    }

    public static boolean isTool(ItemStack s) {
        if (isEmpty(s)) {
            return false;
        }
        Material m = s.getType();
        return Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_AXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m) || Tag.ITEMS_HOES.isTagged(m);
    }

    public static boolean isArmorForSlot(ItemStack s, EquipmentSlot slot) {
        if (isEmpty(s)) {
            return false;
        }
        Equippable eq = s.getData(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    /** Soma dos modificadores de um atributo do item ativos no grupo de slot informado. */
    public static double attributeAmount(ItemStack s, Attribute attribute, EquipmentSlot slot) {
        if (isEmpty(s)) {
            return 0;
        }
        ItemAttributeModifiers mods = s.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return 0;
        }
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute() == attribute && slotMatches(e.modifier().getSlotGroup(), slot)) {
                total += e.modifier().getAmount();
            }
        }
        return total;
    }

    private static boolean slotMatches(EquipmentSlotGroup group, EquipmentSlot slot) {
        return group == null || group.test(slot);
    }

    /** Dano extra da arma na mão (sem contar o 1 base do jogador). */
    public static double weaponDamage(ItemStack s) {
        return attributeAmount(s, Attribute.ATTACK_DAMAGE, EquipmentSlot.HAND);
    }

    /** Velocidade de ataque efetiva (base 4.0 do jogador + modificador do item). */
    public static double attackSpeed(ItemStack s) {
        return Math.max(0.25, 4.0 + attributeAmount(s, Attribute.ATTACK_SPEED, EquipmentSlot.HAND));
    }

    public static float attackScore(ItemStack s) {
        if (!isSword(s)) {
            return -1f;
        }
        float attack = 1.0f + (float) weaponDamage(s);
        int sharpness = s.getEnchantmentLevel(Enchantment.SHARPNESS);
        int fire = s.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        return attack + sharpness * 1.0f + fire * 0.5f;
    }

    public static float pickaxeScore(ItemStack s) {
        if (!isPickaxe(s)) {
            return -1f;
        }
        float speed = STONE.getDestroySpeed(s, false);
        return speed + s.getEnchantmentLevel(Enchantment.EFFICIENCY) * 2.0f;
    }

    public static float armorScore(ItemStack s, EquipmentSlot slot) {
        if (!isArmorForSlot(s, slot)) {
            return -1f;
        }
        double armor = attributeAmount(s, Attribute.ARMOR, slot);
        double toughness = attributeAmount(s, Attribute.ARMOR_TOUGHNESS, slot);
        return (float) (armor + toughness * 0.5 + s.getEnchantmentLevel(Enchantment.PROTECTION) * 0.75);
    }

    public static boolean isBetterSword(ItemStack candidate, ItemStack current) {
        return attackScore(candidate) > attackScore(current) + 0.01f;
    }

    public static boolean isBetterPickaxe(ItemStack candidate, ItemStack current) {
        return pickaxeScore(candidate) > pickaxeScore(current) + 0.01f;
    }

    public static boolean isBetterArmor(ItemStack candidate, ItemStack current, EquipmentSlot slot) {
        return armorScore(candidate, slot) > armorScore(current, slot) + 0.01f;
    }

    public static ItemStack copyOrEmpty(ItemStack s) {
        return isEmpty(s) ? ItemStack.empty() : s.clone();
    }
}
