package dev.manhuntstar.bot;

import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;

/** Melhor espada, picareta e armaduras já obtidas pelo bot (mantidas entre mortes conforme a opção). */
public record PersistentGear(GameItem sword, GameItem pickaxe, GameItem head, GameItem chest, GameItem legs, GameItem feet) {

    public static final PersistentGear EMPTY = new PersistentGear(GameItem.empty(), GameItem.empty(), GameItem.empty(),
            GameItem.empty(), GameItem.empty(), GameItem.empty());

    public GameItem armor(EquipSlot slot) {
        return switch (slot) {
            case HEAD -> head;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> feet;
            default -> GameItem.empty();
        };
    }
}
