package dev.manhuntstar.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * World and player events: blocks placed by players (towers/skybases), block changes
 * for pathfinding, broken ambush bed, crafting table, TAB and the hunt rule for players who
 * join, count in the server list and in /list, and redistribution when the player respawns far
 * from the bots.
 */
public final class WorldPlayerListener implements Listener {

    private final ManhuntStarPlugin plugin;

    public WorldPlayerListener(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        plugin.botManager().blockTracker().markPlaced(PaperWorld.of(b.getWorld()), Locations.pos(b));
        plugin.pathService().invalidate(PaperWorld.of(b.getWorld()), b.getX(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Pos p = Locations.pos(b);
        plugin.botManager().blockTracker().unmark(PaperWorld.of(b.getWorld()), p);
        plugin.pathService().invalidate(PaperWorld.of(b.getWorld()), b.getX(), b.getZ());
        if (b.getType().name().endsWith("_BED")) {
            plugin.botManager().caveAmbush().onBlockBroken(PaperWorld.of(b.getWorld()), p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            plugin.pathService().invalidate(PaperWorld.of(b.getWorld()), b.getX(), b.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            plugin.pathService().invalidate(PaperWorld.of(b.getWorld()), b.getX(), b.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        plugin.pathService().invalidate(PaperWorld.of(to.getWorld()), to.getX(), to.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block b = event.getBlock();
        plugin.pathService().invalidate(PaperWorld.of(b.getWorld()), b.getX(), b.getZ());
    }

    // ------------------------------------------------------------------ crafting table

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.CRAFTING_TABLE) {
            plugin.botManager().tactics().recordCraftingTableUse(PaperEntities.of(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.WORKBENCH && event.getPlayer() instanceof Player p) {
            plugin.botManager().tactics().recordCraftingTableUse(PaperEntities.of(p));
        }
    }

    // ------------------------------------------------------------------ players

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // hunt in progress: hunted right away, protected or left out, depending on the option
        plugin.botManager().policy().admit(PaperEntities.of(p));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.tabList().sendAll(p);
            }
        }, 2L);
    }

    /** The player respawned far away: bots more than 150 blocks away reappear near them after a while. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Bot b : plugin.botManager().aliveBots()) {
                if (!PaperWorld.bukkit(b.world()).equals(p.getWorld())) {
                    continue;
                }
                double d = Math.hypot(b.pos().x() - p.getX(), b.pos().z() - p.getZ());
                if (d > 150.0) {
                    b.brain().forceRedistributeWithDelay((int) (d / 10.0 * 20.0));
                }
            }
        });
    }

    /** Bots count as online players in the server list (number and names). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(PaperServerListPingEvent event) {
        if (!plugin.settings().countInServerList.on()) {
            return;
        }
        List<Bot> bots = plugin.botManager().aliveBots();
        if (bots.isEmpty()) {
            return;
        }
        event.setNumPlayers(event.getNumPlayers() + bots.size());
        var listed = event.getListedPlayers();
        for (Bot b : bots) {
            if (listed.size() >= 12) {
                break;
            }
            listed.add(new PaperServerListPingEvent.ListedPlayerInfo(b.profileName(), b.id()));
        }
    }

    // ------------------------------------------------------------------ /list with the bots

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isListCommand(event.getMessage().substring(1)) && event.getPlayer().hasPermission("minecraft.command.list")) {
            event.setCancelled(true);
            sendList(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (isListCommand(event.getCommand())) {
            event.setCancelled(true);
            sendList(event.getSender());
        }
    }

    private boolean isListCommand(String raw) {
        if (!plugin.settings().replaceListCommand.on() || plugin.botManager().aliveCount() == 0) {
            return false;
        }
        String cmd = raw.trim().toLowerCase(Locale.ROOT);
        return cmd.equals("list") || cmd.equals("minecraft:list");
    }

    /** Same text as the game's /list (translated by the client), with the bots among the players. */
    private void sendList(CommandSender to) {
        List<Component> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.displayName());
        }
        for (Bot b : plugin.botManager().aliveBots()) {
            names.add(Component.text(b.profileName()));
        }
        int count = names.size();
        int max = Bukkit.getMaxPlayers();
        to.sendMessage(Component.translatable("commands.list.players", Component.text(count), Component.text(max),
                Component.join(JoinConfiguration.commas(true), names)));
    }
}
