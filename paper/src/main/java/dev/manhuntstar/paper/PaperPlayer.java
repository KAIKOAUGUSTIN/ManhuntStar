package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameMode;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** A Bukkit {@link Player} as a core {@link GamePlayer}: every call is the Bukkit one. */
public final class PaperPlayer extends PaperLiving implements GamePlayer {

    PaperPlayer(Player player) {
        super(player);
    }

    @Override
    public Player bukkit() {
        return (Player) entity;
    }

    @Override
    public Audience audience() {
        return (Player) entity;
    }

    @Override
    public String name() {
        return bukkit().getName();
    }

    @Override
    public Component displayName() {
        return bukkit().displayName();
    }

    @Override
    public boolean isOnline() {
        return bukkit().isOnline();
    }

    @Override
    public GameMode gameMode() {
        return GameMode.valueOf(bukkit().getGameMode().name());
    }

    @Override
    public boolean hasPermission(String permission) {
        return bukkit().hasPermission(permission);
    }

    @Override
    public void playSound(Vec3 at, GameSound sound, SoundCategory category, float volume, float pitch) {
        bukkit().playSound(new Location(bukkit().getWorld(), at.x(), at.y(), at.z()), PaperSounds.sound(sound), PaperSounds.category(category), volume, pitch);
    }

    @Override
    public void sendBlockDamage(Pos block, float progress, int sourceEntityId) {
        bukkit().sendBlockDamage(new Location(bukkit().getWorld(), block.x(), block.y(), block.z()), progress, sourceEntityId);
    }
}
