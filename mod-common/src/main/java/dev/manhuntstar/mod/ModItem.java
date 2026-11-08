package dev.manhuntstar.mod;

import com.mojang.serialization.Dynamic;
import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.ItemType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

/** A Minecraft {@link ItemStack} as a core {@link GameItem}, on the same stack (as the Paper plugin's). */
public final class ModItem implements GameItem {

    final ItemStack stack;

    ModItem(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * A stack as a core item (null counts as empty). The core item changes the same stack. An empty one
     * gets a stack of its own: the game's empty stack is shared and must not change.
     */
    public static GameItem of(ItemStack stack) {
        return new ModItem(stack == null || stack == ItemStack.EMPTY ? new ItemStack(Items.AIR) : stack);
    }

    /** The stack behind a core item (null counts as empty). */
    public static ItemStack stack(GameItem item) {
        return item == null ? ItemStack.EMPTY : ((ModItem) item).stack;
    }

    @Override
    public ItemType type() {
        return ModTypes.of(stack.getItem());
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public int amount() {
        return stack.getCount();
    }

    @Override
    public void setAmount(int amount) {
        stack.setCount(amount);
    }

    @Override
    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public GameItem copy() {
        return new ModItem(stack.copy());
    }

    @Override
    public boolean isSimilar(GameItem other) {
        if (other == null) {
            return false;
        }
        ItemStack that = ((ModItem) other).stack;
        return that == stack || ItemStack.isSameItemSameComponents(stack, that);
    }

    @Override
    public int enchantLevel(Enchant enchant) {
        return stack.getEnchantments().getLevel(ModRegistry.enchantment(enchant));
    }

    @Override
    public void addEnchant(Enchant enchant, int level) {
        Holder<net.minecraft.world.item.enchantment.Enchantment> e = ModRegistry.enchantment(enchant);
        EnchantmentHelper.updateEnchantments(stack, m -> m.set(e, level));
    }

    @Override
    public boolean hasCustomName() {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    @Override
    public EquipSlot equipSlot() {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq == null ? null : ModRegistry.slot(eq.slot());
    }

    @Override
    public double attributeAmount(Attr attr, EquipSlot slot) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return 0;
        }
        Holder<Attribute> attribute = ModRegistry.attribute(attr);
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().equals(attribute) && slotMatches(e.slot(), slot)) {
                total += e.modifier().amount();
            }
        }
        return total;
    }

    private static boolean slotMatches(EquipmentSlotGroup group, EquipSlot slot) {
        return group == null || group.test(ModRegistry.slot(slot));
    }

    @Override
    public float destroySpeed(BlockType block) {
        return stack.getDestroySpeed(ModTypes.block(block).defaultBlockState());
    }

    @Override
    public void setDamage(int damage) {
        stack.set(DataComponents.DAMAGE, damage);
    }

    @Override
    public int chargeDelayTicks() {
        KineticWeapon kw = stack.get(DataComponents.KINETIC_WEAPON);
        return kw == null ? -1 : kw.delayTicks();
    }

    /** The stack as compressed NBT with the game's data version, so a newer game can update it. */
    @Override
    public byte[] serialize() {
        Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(ModServer.get().registryOps(NbtOps.INSTANCE), stack).getOrThrow();
        CompoundTag root = new CompoundTag();
        root.put("item", tag);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** The other way of {@link #serialize}. */
    static ItemStack deserialize(byte[] data) {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Tag tag = root.get("item");
        int version = root.getIntOr("DataVersion", 0);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        if (version > 0 && version < current) {
            tag = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), version, current).getValue();
        }
        return ItemStack.OPTIONAL_CODEC.parse(ModServer.get().registryOps(NbtOps.INSTANCE), tag).getOrThrow();
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
