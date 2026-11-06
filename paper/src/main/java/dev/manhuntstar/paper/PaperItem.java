package dev.manhuntstar.paper;

import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.ItemType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** A Bukkit {@link ItemStack} as a core {@link GameItem}: every call is the Bukkit one, on the same stack. */
final class PaperItem implements GameItem {

    private static final Map<BlockType, BlockData> BLOCK_DATA = new ConcurrentHashMap<>();

    final ItemStack stack;

    PaperItem(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public ItemType type() {
        return PaperItems.of(stack.getType());
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public int amount() {
        return stack.getAmount();
    }

    @Override
    public void setAmount(int amount) {
        stack.setAmount(amount);
    }

    @Override
    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public GameItem copy() {
        return new PaperItem(stack.clone());
    }

    @Override
    public boolean isSimilar(GameItem other) {
        return stack.isSimilar(other == null ? null : ((PaperItem) other).stack);
    }

    @Override
    public int enchantLevel(Enchant enchant) {
        return stack.getEnchantmentLevel(PaperItems.enchantment(enchant));
    }

    @Override
    public void addEnchant(Enchant enchant, int level) {
        stack.addUnsafeEnchantment(PaperItems.enchantment(enchant), level);
    }

    @Override
    public boolean hasCustomName() {
        return stack.hasItemMeta() && stack.getItemMeta().hasCustomName();
    }

    @Override
    public EquipSlot equipSlot() {
        Equippable eq = stack.getData(DataComponentTypes.EQUIPPABLE);
        return eq == null ? null : PaperItems.slot(eq.slot());
    }

    @Override
    public double attributeAmount(Attr attr, EquipSlot slot) {
        ItemAttributeModifiers mods = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return 0;
        }
        Attribute attribute = PaperItems.attribute(attr);
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute() == attribute && slotMatches(e.modifier().getSlotGroup(), slot)) {
                total += e.modifier().getAmount();
            }
        }
        return total;
    }

    private static boolean slotMatches(EquipmentSlotGroup group, EquipSlot slot) {
        return group == null || group.test(PaperItems.slot(slot));
    }

    @Override
    public float destroySpeed(BlockType block) {
        BlockData data = BLOCK_DATA.computeIfAbsent(block, b -> PaperBlocks.material(b).createBlockData());
        return data.getDestroySpeed(stack, false);
    }

    @Override
    public void setDamage(int damage) {
        if (stack.getItemMeta() instanceof Damageable d) {
            d.setDamage(damage);
            stack.setItemMeta(d);
        }
    }

    @Override
    public int chargeDelayTicks() {
        KineticWeapon kw = stack.getData(DataComponentTypes.KINETIC_WEAPON);
        return kw == null ? -1 : kw.delayTicks();
    }

    @Override
    public byte[] serialize() {
        return stack.serializeAsBytes();
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
