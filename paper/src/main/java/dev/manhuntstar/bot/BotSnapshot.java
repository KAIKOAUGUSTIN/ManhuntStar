package dev.manhuntstar.bot;

import dev.manhuntstar.platform.GameItem;
import java.util.List;
import java.util.UUID;

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
        List<GameItem> inventory,
        GameItem mainHand,
        GameItem offHand,
        GameItem head,
        GameItem chest,
        GameItem legs,
        GameItem feet,
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
