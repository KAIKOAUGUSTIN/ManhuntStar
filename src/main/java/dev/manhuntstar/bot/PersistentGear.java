package dev.manhuntstar.bot;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Melhor espada, picareta e armaduras já obtidas pelo bot (mantidas entre mortes conforme a opção). */
public record PersistentGear(ItemStack sword, ItemStack pickaxe, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {

    public static final PersistentGear EMPTY = new PersistentGear(ItemStack.empty(), ItemStack.empty(), ItemStack.empty(),
            ItemStack.empty(), ItemStack.empty(), ItemStack.empty());

    public ItemStack armor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> head;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> feet;
            default -> ItemStack.empty();
        };
    }
}
