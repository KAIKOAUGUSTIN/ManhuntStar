package dev.manhuntstar.mod;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.SkinData;
import dev.manhuntstar.host.BotTabList;
import dev.manhuntstar.mod.mixin.PlayerInfoUpdatePacketAccessor;
import dev.manhuntstar.platform.GamePlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Bots in the TAB list, like real players (with skin, ping and survival mode), through the player info packets. */
public final class ModTabList implements BotTabList {

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> ADD = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> LATENCY = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);

    private final BooleanSupplier enabledSetting;
    private final Map<UUID, Bot> listed = new HashMap<>();
    private final Map<UUID, Integer> latency = new HashMap<>();

    public ModTabList(BooleanSupplier enabledSetting) {
        this.enabledSetting = enabledSetting;
    }

    private boolean active() {
        return enabledSetting.getAsBoolean();
    }

    @Override
    public void add(Bot bot) {
        listed.put(bot.id(), bot);
        latency.put(bot.id(), 20 + ThreadLocalRandom.current().nextInt(60));
        if (active()) {
            broadcast(packet(ADD, List.of(bot)));
        }
    }

    @Override
    public void remove(Bot bot) {
        listed.remove(bot.id());
        latency.remove(bot.id());
        broadcast(new ClientboundPlayerInfoRemovePacket(List.of(bot.id())));
    }

    /** Updates name/skin (e.g. after /manhunt skin). */
    @Override
    public void refresh(Bot bot) {
        if (!active() || !listed.containsKey(bot.id())) {
            return;
        }
        broadcast(new ClientboundPlayerInfoRemovePacket(List.of(bot.id())));
        broadcast(packet(ADD, List.of(bot)));
    }

    @Override
    public void sendAll(GamePlayer player) {
        if (!active() || listed.isEmpty()) {
            return;
        }
        send(ModEntities.vanilla(player), packet(ADD, new ArrayList<>(listed.values())));
    }

    @Override
    public void resync() {
        if (listed.isEmpty()) {
            return;
        }
        if (active()) {
            broadcast(packet(ADD, new ArrayList<>(listed.values())));
        } else {
            broadcast(new ClientboundPlayerInfoRemovePacket(new ArrayList<>(listed.keySet())));
        }
    }

    /** A ping that varies a little, like a real player's (every 5 s). */
    @Override
    public void tick(int now) {
        if (!active() || listed.isEmpty() || now % 100 != 0) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (UUID id : listed.keySet()) {
            int cur = latency.getOrDefault(id, 40);
            latency.put(id, Math.max(8, Math.min(180, cur + r.nextInt(-6, 7))));
        }
        broadcast(packet(LATENCY, new ArrayList<>(listed.values())));
    }

    @Override
    public void clear() {
        if (!listed.isEmpty()) {
            broadcast(new ClientboundPlayerInfoRemovePacket(new ArrayList<>(listed.keySet())));
        }
        listed.clear();
    }

    private ClientboundPlayerInfoUpdatePacket packet(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions, Collection<Bot> bots) {
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
        ModServer server = ModServer.get();
        for (Bot b : bots) {
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(b.id(), profile(b.id(), b.profileName(), b.skin()), true,
                    latency.getOrDefault(b.id(), 40), GameType.SURVIVAL, server.vanilla(Component.text(b.visualName())), true, 0, null));
        }
        ClientboundPlayerInfoUpdatePacket p = new ClientboundPlayerInfoUpdatePacket(EnumSet.copyOf(actions), List.<ServerPlayer>of());
        ((PlayerInfoUpdatePacketAccessor) p).manhuntstar$setEntries(entries);
        return p;
    }

    private static GameProfile profile(UUID id, String name, SkinData skin) {
        ImmutableMultimap.Builder<String, Property> props = ImmutableMultimap.builder();
        if (skin != null && skin.valid()) {
            props.put("textures", new Property("textures", skin.value(), skin.signature()));
        }
        return new GameProfile(id, name, new PropertyMap(props.build()));
    }

    private static void broadcast(Packet<?> packet) {
        ModServer server = ModServer.get();
        if (server != null) {
            server.vanilla().getPlayerList().broadcastAll(packet);
        }
    }

    private static void send(ServerPlayer player, Packet<?> packet) {
        if (player != null && player.connection != null) {
            player.connection.send(packet);
        }
    }
}
