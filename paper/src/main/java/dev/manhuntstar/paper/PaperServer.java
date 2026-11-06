package dev.manhuntstar.paper;

import dev.manhuntstar.platform.FireworkShape;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameServer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.ItemType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;

/** The Bukkit server as a core {@link GameServer}: every call is the Bukkit one. */
public final class PaperServer implements GameServer {

    private static volatile Plugin current;

    private final Plugin plugin;

    /** The scheduled tasks, chunk tickets and entity marks belong to {@code plugin}. */
    public PaperServer(Plugin plugin) {
        this.plugin = plugin;
        current = plugin;
    }

    /** The plugin that owns the scheduled tasks, chunk tickets and entity marks. */
    static Plugin plugin() {
        return current;
    }

    @Override
    public int currentTick() {
        return Bukkit.getCurrentTick();
    }

    @Override
    public GameWorld world(UUID id) {
        return PaperWorld.of(Bukkit.getWorld(id));
    }

    @Override
    public GameWorld world(String name) {
        return PaperWorld.of(Bukkit.getWorld(name));
    }

    @Override
    public GameWorld defaultWorld() {
        return PaperWorld.of(Bukkit.getWorlds().getFirst());
    }

    @Override
    public List<GameWorld> worlds() {
        List<GameWorld> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            out.add(PaperWorld.of(w));
        }
        return out;
    }

    @Override
    public Collection<? extends GamePlayer> onlinePlayers() {
        List<GamePlayer> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(PaperEntities.of(p));
        }
        return out;
    }

    @Override
    public GamePlayer player(UUID id) {
        return PaperEntities.of(Bukkit.getPlayer(id));
    }

    @Override
    public GamePlayer player(String name) {
        return PaperEntities.of(Bukkit.getPlayerExact(name));
    }

    @Override
    public GameEntity entity(UUID id) {
        return PaperEntities.of(Bukkit.getEntity(id));
    }

    @Override
    public GameItem newItem(ItemType type, int amount) {
        return PaperItems.of(new ItemStack(PaperItems.material(type), amount));
    }

    @Override
    public GameItem emptyItem() {
        return PaperItems.of(ItemStack.empty());
    }

    @Override
    public GameItem newFireworkRocket(int flight, FireworkShape shape, int... colors) {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        Color[] bukkitColors = new Color[colors.length];
        for (int i = 0; i < colors.length; i++) {
            bukkitColors[i] = Color.fromRGB(colors[i]);
        }
        rocket.editMeta(FireworkMeta.class, meta -> {
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.valueOf(shape.name())).withColor(bukkitColors).build());
            meta.setPower(flight);
        });
        return PaperItems.of(rocket);
    }

    @Override
    public GameItem deserializeItem(byte[] data) {
        return PaperItems.of(ItemStack.deserializeBytes(data));
    }

    @Override
    public void broadcast(Component message) {
        Bukkit.broadcast(message);
    }

    @Override
    public void runNextTick(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
