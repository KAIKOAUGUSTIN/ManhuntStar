package dev.manhuntstar.mod;

import java.util.function.BiPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * Who may do what. By default a permission is for operators (the game's "gamemaster" level, which runs
 * commands like /gamemode), as the plugin's permissions default to ops; a loader can plug in a permission
 * mod instead with {@link #use}.
 */
public final class ModPermissions {

    private static volatile BiPredicate<CommandSourceStack, String> check =
            (source, permission) -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    private ModPermissions() {
    }

    public static void use(BiPredicate<CommandSourceStack, String> checker) {
        check = checker;
    }

    public static boolean has(CommandSourceStack source, String permission) {
        return check.test(source, permission);
    }
}
