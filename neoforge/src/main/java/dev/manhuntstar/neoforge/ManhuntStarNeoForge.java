package dev.manhuntstar.neoforge;

import dev.faststats.neoforge.NeoForgeContext;
import dev.manhuntstar.mod.ModLogging;
import dev.manhuntstar.mod.ModPermissions;
import dev.manhuntstar.mod.ModPlatform;
import dev.manhuntstar.mod.ModStatistics;
import java.util.logging.Logger;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * The NeoForge mod's entry point: NeoForge's server events and commands for {@link ModPlatform}. The rest
 * (the game's events) comes through mod-common's mixins, the same as on Fabric.
 */
@Mod("manhuntstar")
public final class ManhuntStarNeoForge {

    /** The admin permission as a NeoForge permission node (operators by default; permission mods can change it). */
    private static final PermissionNode<Boolean> ADMIN = new PermissionNode<>("manhuntstar", "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));

    public ManhuntStarNeoForge(IEventBus modBus, ModContainer container) {
        Logger logger = ModLogging.logger("Manhunt*", false);
        String version = container.getModInfo().getVersion().toString();
        String neoforge = ModList.get().getModContainerById("neoforge").map(c -> "NeoForge " + c.getModInfo().getVersion()).orElse("NeoForge");
        ModPlatform platform = new ModPlatform(logger, FMLPaths.CONFIGDIR.get().resolve("manhuntstar").toFile(), version, neoforge);

        ModPermissions.use((source, permission) -> {
            if (source.getPlayer() != null && permission.equals(ADMIN.getNodeName())) {
                return PermissionAPI.getPermission(source.getPlayer(), ADMIN);
            }
            return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        });

        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(ADMIN));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ModPlatform.registerCommands(event.getDispatcher(), event.getBuildContext()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> platform.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> platform.serverStopping());
        startStatistics(platform);
    }

    /**
     * Anonymous statistics on FastStats ({@link ModStatistics}), on a dedicated server only. Made now,
     * before the server exists: the SDK starts sending when the server has started and stops with it.
     */
    private static void startStatistics(ModPlatform platform) {
        if (FMLEnvironment.getDist() != Dist.DEDICATED_SERVER) {
            return;
        }
        String token = ModStatistics.token(platform);
        if (!token.isEmpty()) {
            new NeoForgeContext.Factory("manhuntstar", token).metrics(ModStatistics.metrics(platform)).create();
        }
    }
}
