package dev.manhuntstar.mod;

import com.mojang.serialization.DynamicOps;
import dev.manhuntstar.platform.FireworkShape;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.ScheduledTask;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/** The Minecraft server as a core {@link GameServer}, with the mod's scheduler. */
public final class ModServer implements GameServer {

    private static volatile ModServer current;

    private final MinecraftServer server;
    private final MinecraftServerAudiences audiences;
    private final ModScheduler scheduler;

    ModServer(MinecraftServer server, Logger logger) {
        this.server = server;
        this.audiences = MinecraftServerAudiences.of(server);
        this.scheduler = new ModScheduler(logger);
        current = this;
        ModRegistry.reset();
    }

    /** The running server. */
    public static ModServer get() {
        return current;
    }

    public MinecraftServer vanilla() {
        return server;
    }

    public MinecraftServerAudiences audiences() {
        return audiences;
    }

    /** Text in the game's own format. */
    public net.minecraft.network.chat.Component vanilla(Component text) {
        return audiences.asNative(text);
    }

    public <T> RegistryOps<T> registryOps(DynamicOps<T> ops) {
        return server.registryAccess().createSerializationContext(ops);
    }

    /** Runs the tasks that are due; the platform calls it at the start of every server tick. */
    void heartbeat() {
        scheduler.heartbeat();
    }

    void shutdown() {
        scheduler.shutdown();
        if (current == this) {
            current = null;
        }
    }

    @Override
    public int currentTick() {
        return server.getTickCount();
    }

    @Override
    public GameWorld world(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            ModWorld w = ModWorld.of(level);
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    @Override
    public GameWorld world(String name) {
        for (ServerLevel level : server.getAllLevels()) {
            ModWorld w = ModWorld.of(level);
            if (w.name().equals(name)) {
                return w;
            }
        }
        return null;
    }

    @Override
    public int maxPlayers() {
        return server.getMaxPlayers();
    }

    @Override
    public GameWorld defaultWorld() {
        return ModWorld.of(server.overworld());
    }

    @Override
    public List<GameWorld> worlds() {
        List<GameWorld> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(ModWorld.of(level));
        }
        return out;
    }

    @Override
    public Collection<? extends GamePlayer> onlinePlayers() {
        List<GamePlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            out.add(ModEntities.of(p));
        }
        return out;
    }

    @Override
    public GamePlayer player(UUID id) {
        return ModEntities.of(server.getPlayerList().getPlayer(id));
    }

    @Override
    public GamePlayer player(String name) {
        return ModEntities.of(server.getPlayerList().getPlayerByName(name));
    }

    @Override
    public GameEntity entity(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) {
                return ModEntities.of(e);
            }
        }
        return null;
    }

    @Override
    public GameItem newItem(ItemType type, int amount) {
        return ModItem.of(new ItemStack(ModTypes.item(type), amount));
    }

    @Override
    public GameItem emptyItem() {
        return ModItem.of(null);
    }

    @Override
    public GameItem newFireworkRocket(int flight, FireworkShape shape, int... colors) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion.Shape s = switch (shape) {
            case BALL -> FireworkExplosion.Shape.SMALL_BALL;
            case BALL_LARGE -> FireworkExplosion.Shape.LARGE_BALL;
            case STAR -> FireworkExplosion.Shape.STAR;
            case BURST -> FireworkExplosion.Shape.BURST;
            case CREEPER -> FireworkExplosion.Shape.CREEPER;
        };
        FireworkExplosion explosion = new FireworkExplosion(s, new IntArrayList(colors), new IntArrayList(), false, false);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of(explosion)));
        return ModItem.of(rocket);
    }

    @Override
    public GameItem deserializeItem(byte[] data) {
        return ModItem.of(ModItem.deserialize(data));
    }

    @Override
    public void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(vanilla(message), false);
    }

    @Override
    public void runNextTick(Runnable task) {
        scheduler.schedule(task, 0, 0, false);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        scheduler.schedule(task, delayTicks, 0, false);
    }

    @Override
    public ScheduledTask runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.schedule(task, delayTicks, periodTicks, true);
    }

    /** A repeating task on the server thread (the mod's tick). */
    ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.schedule(task, delayTicks, periodTicks, false);
    }
}
