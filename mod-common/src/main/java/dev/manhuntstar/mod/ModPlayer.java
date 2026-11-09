package dev.manhuntstar.mod;

import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameMode;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/** A Minecraft {@link ServerPlayer} as a core {@link GamePlayer}, doing what Bukkit's {@code CraftPlayer} does. */
public final class ModPlayer extends ModLiving implements GamePlayer {

    ModPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public ServerPlayer vanilla() {
        return (ServerPlayer) super.vanilla();
    }

    @Override
    public Audience audience() {
        return ModServer.get().audiences().audience(vanilla());
    }

    @Override
    public String name() {
        return vanilla().getScoreboardName();
    }

    /** The name as text: Paper's default display name. */
    @Override
    public Component displayName() {
        return Component.text(name());
    }

    @Override
    public boolean isOnline() {
        ServerPlayer p = vanilla();
        return p.level().getServer().getPlayerList().getPlayer(p.getUUID()) != null;
    }

    @Override
    public GameMode gameMode() {
        return GameMode.valueOf(vanilla().gameMode().name());
    }

    @Override
    public boolean hasPermission(String permission) {
        return ModPermissions.has(vanilla().createCommandSourceStack(), permission);
    }

    @Override
    public void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
        ServerPlayer p = vanilla();
        p.connection.send(new ClientboundSoundPacket(ModRegistry.sound(sound), ModRegistry.category(category), at.x(), at.y(), at.z(), volume, pitch,
                p.getRandom().nextLong()));
    }

    @Override
    public void sendBlockDamage(Pos block, float progress, int sourceEntityId) {
        if (progress < 0 || progress > 1) {
            throw new IllegalArgumentException("progress must be between 0.0 and 1.0 (inclusive)");
        }
        int stage = progress == 0 ? -1 : (int) (9.0F * progress);
        vanilla().connection.send(new ClientboundBlockDestructionPacket(sourceEntityId, ModConvert.blockPos(block), stage));
    }

    @Override
    public GameItem mainHandItem() {
        return ModItem.of(vanilla().getMainHandItem());
    }

    @Override
    public List<GameItem> inventoryContents() {
        Inventory inv = vanilla().getInventory();
        List<GameItem> out = new ArrayList<>(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(ModItem.of(inv.getItem(i)));
        }
        return out;
    }
}
