package dev.manhuntstar.fabric;

import dev.faststats.fabric.FabricContext;
import dev.manhuntstar.mod.ModLogging;
import dev.manhuntstar.mod.ModPermissions;
import dev.manhuntstar.mod.ModPlatform;
import dev.manhuntstar.mod.ModStatistics;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.fabricmc.api.EnvType;
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
        Logger logger = ModLogging.logger("Manhunt*", true);
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
        startStatistics(fabric, platform);
    }

    /**
     * Anonymous statistics on FastStats ({@link ModStatistics}), on a dedicated server only. Made now,
     * before the server exists: the SDK starts sending when the server has started and stops with it.
     */
    private static void startStatistics(FabricLoader fabric, ModPlatform platform) {
        if (fabric.getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        String token = ModStatistics.token(platform);
        if (!token.isEmpty()) {
            new FabricContext.Factory("manhuntstar", token).metrics(ModStatistics.metrics(platform)).create();
        }
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
                logger.warning(PERMISSIONS_API + " has no permission check Manhunt* knows; operators can use /manhunt.");
                return;
            }
            Method found = check;
            AtomicBoolean warned = new AtomicBoolean();
            ModPermissions.use((source, permission) -> {
                boolean op = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                try {
                    return (boolean) found.invoke(null, source, permission, op);
                } catch (ReflectiveOperationException e) {
                    // logged once: the check runs on every command
                    if (warned.compareAndSet(false, true)) {
                        logger.log(Level.WARNING, "A permission check through " + PERMISSIONS_API + " failed; operators can use /manhunt.", e);
                    }
                    return op;
                }
            });
            logger.info("Permissions come from " + PERMISSIONS_API + ".");
        } catch (ClassNotFoundException e) {
            // not there after all
        }
    }
}
