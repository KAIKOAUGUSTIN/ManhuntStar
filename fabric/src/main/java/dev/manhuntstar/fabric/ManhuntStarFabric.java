package dev.manhuntstar.fabric;

import dev.manhuntstar.mod.ModLogging;
import dev.manhuntstar.mod.ModPermissions;
import dev.manhuntstar.mod.ModPlatform;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * The Fabric mod's entry point: Fabric's server events and commands for {@link ModPlatform}. The rest
 * (the game's events) comes through mod-common's mixins, the same as on NeoForge.
 */
public final class ManhuntStarFabric implements ModInitializer {

    /** The permission mod most Fabric servers use (LuckPerms works through it). */
    private static final String PERMISSIONS_API = "fabric-permissions-api-v0";

    @Override
    public void onInitialize() {
        Logger logger = ModLogging.logger("Manhunt*");
        FabricLoader fabric = FabricLoader.getInstance();
        String version = fabric.getModContainer("manhuntstar").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        String loader = fabric.getModContainer("fabricloader").map(ModContainer::getMetadata).map(m -> "Fabric " + m.getVersion().getFriendlyString())
                .orElse("Fabric");
        ModPlatform platform = new ModPlatform(logger, fabric.getConfigDir().resolve("manhuntstar").toFile(), version, loader);
        if (fabric.isModLoaded(PERMISSIONS_API)) {
            usePermissionsApi(logger);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ModPlatform.registerCommands(dispatcher, context));
        ServerLifecycleEvents.SERVER_STARTED.register(platform::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> platform.serverStopping());
    }

    /**
     * Permissions through fabric-permissions-api when a server has it, with operators as the fallback.
     * It is looked up at run time, so the mod doesn't need it.
     */
    private static void usePermissionsApi(Logger logger) {
        try {
            Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method check = null;
            for (Method m : api.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("check") && p.length == 3 && p[0].isAssignableFrom(CommandSourceStack.class) && p[1] == String.class
                        && p[2] == boolean.class) {
                    check = m;
                }
            }
            if (check == null) {
                return;
            }
            Method found = check;
            ModPermissions.use((source, permission) -> {
                boolean op = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                try {
                    return (boolean) found.invoke(null, source, permission, op);
                } catch (ReflectiveOperationException e) {
                    return op;
                }
            });
            logger.info("Permissions come from " + PERMISSIONS_API + ".");
        } catch (ClassNotFoundException e) {
            // not there after all
        }
    }
}
