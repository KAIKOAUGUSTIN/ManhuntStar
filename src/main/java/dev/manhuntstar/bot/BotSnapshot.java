package dev.manhuntstar.bot;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Estado salvo de um bot (para reiniciar o servidor ou renascer): identidade, posição, vida, fome,
 * fase, inventário, equipamento e equipamento persistente.
 */
public record BotSnapshot(
        UUID id,
        String name,
        String visualName,
        SkinData skin,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        double health,
        int food,
        float saturation,
        BotDifficulty difficulty,
        int phase,
        boolean progressionEnabled,
        List<ItemStack> inventory,
        ItemStack mainHand,
        ItemStack offHand,
        ItemStack head,
        ItemStack chest,
        ItemStack legs,
        ItemStack feet,
        PersistentGear gear,
        String group,
        UUID target,
        boolean shouldRespawn,
        long horseBlockedTicksLeft,
        boolean totemAvailable,
        int aggression,
        int kills,
        boolean alive) {
}
