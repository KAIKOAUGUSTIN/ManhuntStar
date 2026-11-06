package dev.manhuntstar.paper;

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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The Bukkit server as a core {@link GameServer}: every call is the Bukkit one. */
public final class PaperServer implements GameServer {

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
    public GameItem deserializeItem(byte[] data) {
        return PaperItems.of(ItemStack.deserializeBytes(data));
    }
}
