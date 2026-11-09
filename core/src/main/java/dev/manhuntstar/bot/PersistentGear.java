package dev.manhuntstar.bot;

import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;

/** Best sword, pickaxe and armor the bot has had (kept between deaths depending on the option). */
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
