package dev.manhuntstar.bot;

import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.ItemTrait;

/** Scoring of swords, pickaxes and armor to pick the best equipment. */
public final class GearScoring {

    private GearScoring() {
    }

    public static boolean isEmpty(GameItem s) {
        return s == null || s.isEmpty();
    }

    public static boolean isSword(GameItem s) {
        return !isEmpty(s) && s.type().is(ItemTrait.SWORD);
    }

    public static boolean isPickaxe(GameItem s) {
        return !isEmpty(s) && s.type().is(ItemTrait.PICKAXE);
    }

    public static boolean isSpear(GameItem s) {
        return !isEmpty(s) && s.type().is(ItemTrait.SPEAR);
    }

    public static boolean isAxe(GameItem s) {
        return !isEmpty(s) && s.type().is(ItemTrait.AXE);
    }

    public static boolean isTool(GameItem s) {
        if (isEmpty(s)) {
            return false;
        }
        var m = s.type();
        return m.is(ItemTrait.PICKAXE) || m.is(ItemTrait.AXE) || m.is(ItemTrait.SHOVEL) || m.is(ItemTrait.HOE);
    }

    public static boolean isArmorForSlot(GameItem s, EquipSlot slot) {
        if (isEmpty(s)) {
            return false;
        }
        return s.equipSlot() == slot;
    }

    /** Sum of the item's modifiers of an attribute that are active in the given slot group. */
    public static double attributeAmount(GameItem s, Attr attribute, EquipSlot slot) {
        if (isEmpty(s)) {
            return 0;
        }
        return s.attributeAmount(attribute, slot);
    }

    /** Extra damage of the weapon in hand (without the player's base 1). */
    public static double weaponDamage(GameItem s) {
        return attributeAmount(s, Attr.ATTACK_DAMAGE, EquipSlot.HAND);
    }

    /** Effective attack speed (the player's base 4.0 + the item's modifier). */
    public static double attackSpeed(GameItem s) {
        return Math.max(0.25, 4.0 + attributeAmount(s, Attr.ATTACK_SPEED, EquipSlot.HAND));
    }

    public static float attackScore(GameItem s) {
        if (!isSword(s)) {
            return -1f;
        }
        float attack = 1.0f + (float) weaponDamage(s);
        int sharpness = s.enchantLevel(Enchant.SHARPNESS);
        int fire = s.enchantLevel(Enchant.FIRE_ASPECT);
        return attack + sharpness * 1.0f + fire * 0.5f;
    }

    public static float pickaxeScore(GameItem s) {
        if (!isPickaxe(s)) {
            return -1f;
        }
        float speed = s.destroySpeed(Blocks.STONE);
        return speed + s.enchantLevel(Enchant.EFFICIENCY) * 2.0f;
    }

    public static float armorScore(GameItem s, EquipSlot slot) {
        if (!isArmorForSlot(s, slot)) {
            return -1f;
        }
        double armor = attributeAmount(s, Attr.ARMOR, slot);
        double toughness = attributeAmount(s, Attr.ARMOR_TOUGHNESS, slot);
        return (float) (armor + toughness * 0.5 + s.enchantLevel(Enchant.PROTECTION) * 0.75);
    }

    public static boolean isBetterSword(GameItem candidate, GameItem current) {
        return attackScore(candidate) > attackScore(current) + 0.01f;
    }

    public static boolean isBetterPickaxe(GameItem candidate, GameItem current) {
        return pickaxeScore(candidate) > pickaxeScore(current) + 0.01f;
    }

    public static boolean isBetterArmor(GameItem candidate, GameItem current, EquipSlot slot) {
        return armorScore(candidate, slot) > armorScore(current, slot) + 0.01f;
    }

    public static GameItem copyOrEmpty(GameItem s) {
        return isEmpty(s) ? GameItem.empty() : s.copy();
    }
}
