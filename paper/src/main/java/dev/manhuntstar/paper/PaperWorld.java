package dev.manhuntstar.paper;

import dev.manhuntstar.platform.BlockSnapshot;
import dev.manhuntstar.platform.GameWorld;
import java.util.UUID;
import org.bukkit.World;

/** A Bukkit {@link World} as a core {@link GameWorld}: every call is the Bukkit one. */
public final class PaperWorld implements GameWorld {

    private final World world;

    private PaperWorld(World world) {
        this.world = world;
    }

    public static PaperWorld of(World world) {
        return world == null ? null : new PaperWorld(world);
    }

    public World bukkit() {
        return world;
    }

    @Override
    public UUID id() {
        return world.getUID();
    }

    @Override
    public String name() {
        return world.getName();
    }

    @Override
    public int minHeight() {
        return world.getMinHeight();
    }

    @Override
    public int maxHeight() {
        return world.getMaxHeight();
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public BlockSnapshot chunkSnapshot(int chunkX, int chunkZ) {
        return new PaperChunkSnapshot(world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PaperWorld other && other.world.equals(world);
    }

    @Override
    public int hashCode() {
        return world.hashCode();
    }
}
