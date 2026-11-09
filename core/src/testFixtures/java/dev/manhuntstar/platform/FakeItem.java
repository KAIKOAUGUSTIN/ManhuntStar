package dev.manhuntstar.platform;

import java.util.EnumMap;
import java.util.Map;

/**
 * A stack of items for tests, without Minecraft: the type, the amount and the enchantments; the test sets
 * the slot it is worn in, its attribute modifiers and how fast it breaks blocks.
 */
public class FakeItem implements GameItem {

    public final ItemType type;
    public int amount;
    public final Map<Enchant, Integer> enchants = new EnumMap<>(Enchant.class);
    public final Map<Attr, Double> attributes = new EnumMap<>(Attr.class);
    public EquipSlot slot;
    public float destroySpeed = 1f;
    public boolean customName;
    public int damage;
    public int maxStackSize = 64;

    public FakeItem(ItemType type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    @Override
    public ItemType type() {
        return isEmpty() ? ItemType.of("AIR") : type;
    }

    @Override
    public boolean isEmpty() {
        return amount <= 0 || type.name().equals("AIR");
    }

    @Override
    public int amount() {
        return isEmpty() ? 0 : amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public int maxStackSize() {
        return maxStackSize;
    }

    @Override
    public FakeItem copy() {
        FakeItem c = new FakeItem(type, amount);
        c.enchants.putAll(enchants);
        c.attributes.putAll(attributes);
        c.slot = slot;
        c.destroySpeed = destroySpeed;
        c.customName = customName;
        c.damage = damage;
        c.maxStackSize = maxStackSize;
        return c;
    }

    @Override
    public boolean isSimilar(GameItem other) {
        return other instanceof FakeItem o && o.type() == type() && o.enchants.equals(enchants) && o.customName == customName
                && o.damage == damage;
    }

    @Override
    public int enchantLevel(Enchant enchant) {
        return enchants.getOrDefault(enchant, 0);
    }

    @Override
    public void addEnchant(Enchant enchant, int level) {
        enchants.put(enchant, level);
    }

    @Override
    public boolean hasCustomName() {
        return customName;
    }

    @Override
    public EquipSlot equipSlot() {
        return slot;
    }

    @Override
    public double attributeAmount(Attr attribute, EquipSlot slot) {
        return attributes.getOrDefault(attribute, 0.0);
    }

    @Override
    public float destroySpeed(BlockType block) {
        return destroySpeed;
    }

    @Override
    public void setDamage(int damage) {
        this.damage = damage;
    }

    @Override
    public int chargeDelayTicks() {
        return -1;
    }

    @Override
    public byte[] serialize() {
        throw new UnsupportedOperationException("the fake server doesn't store items");
    }
}
