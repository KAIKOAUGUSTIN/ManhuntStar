package dev.manhuntstar.mod.mixin;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoUpdatePacketAccessor {

    /** The packet's rows; the game only builds them from real players, the TAB list puts the bots' in. */
    @Mutable
    @Accessor("entries")
    void manhuntstar$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
