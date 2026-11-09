package dev.manhuntstar.platform;

/**
 * A stack of items, like Bukkit's ItemStack: it can be changed (amount, enchantments) and {@link #copy()}
 * makes an independent one. Reading an entity's equipment gives a copy, as on Bukkit.
 */
public interface GameItem {

    /** A new stack of one item. */
    static GameItem of(ItemType type) {
        return Platform.server().newItem(type, 1);
    }

    static GameItem of(ItemType type, int amount) {
        return Platform.server().newItem(type, amount);
    }

    /** A new empty stack (air). */
    static GameItem empty() {
        return Platform.server().emptyItem();
    }

    ItemType type();

    boolean isEmpty();

    int amount();

    void setAmount(int amount);

    int maxStackSize();

    GameItem copy();

    /** The same item and data (enchantments, name, wear...), whatever the amount. False for null. */
    boolean isSimilar(GameItem other);

    /** The level of an enchantment on the item; 0 without it. */
    int enchantLevel(Enchant enchant);

    /** Adds an enchantment at any level, even one the item couldn't normally have. */
    void addEnchant(Enchant enchant, int level);

    /** The item was renamed (anvil). */
    boolean hasCustomName();

    /** The slot the item is worn in (armor, elytra...), or null when it isn't worn. */
    EquipSlot equipSlot();

    /** Sum of the item's modifiers of an attribute that are active in the given slot. */
    double attributeAmount(Attr attribute, EquipSlot slot);

    /** How fast the item breaks a block of this type (1 by hand). */
    float destroySpeed(BlockType block);

    /** Sets how worn the item is (uses spent), when it can wear out. */
    void setDamage(int damage);

    /** The ticks a charge weapon (spear) takes before it hits, or -1 when it isn't one. */
    int chargeDelayTicks();

    /** The stack as bytes, read back by {@link GameServer#deserializeItem}. */
    byte[] serialize();
}
