package dev.manhuntstar.platform;

import dev.manhuntstar.util.Pos;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;

/** A player, like Bukkit's Player. Messages, action bars and boss bars go through Adventure. */
public interface GamePlayer extends GameLiving, ForwardingAudience.Single {

    String name();

    Component displayName();

    boolean isOnline();

    GameMode gameMode();

    boolean hasPermission(String permission);

    /** Plays a sound only this player hears. */
    void playSound(dev.manhuntstar.util.Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch);

    /** Shows (only to this player) the breaking cracks of a block; 0 removes them. */
    void sendBlockDamage(Pos block, float progress, int sourceEntityId);

    GameItem mainHandItem();

    /** Every slot of the player's inventory (empty stacks included). */
    java.util.List<GameItem> inventoryContents();
}
