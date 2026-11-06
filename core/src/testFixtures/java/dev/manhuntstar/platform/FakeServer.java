package dev.manhuntstar.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A server for tests, without Minecraft: the worlds and the tick are set by the test. */
public class FakeServer implements GameServer {

    public int tick;
    public final List<GameWorld> worlds = new ArrayList<>();

    @Override
    public int currentTick() {
        return tick;
    }

    @Override
    public GameWorld world(UUID id) {
        for (GameWorld w : worlds) {
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    @Override
    public GameWorld world(String name) {
        for (GameWorld w : worlds) {
            if (w.name().equals(name)) {
                return w;
            }
        }
        return null;
    }

    @Override
    public List<GameWorld> worlds() {
        return worlds;
    }

    @Override
    public Collection<? extends GamePlayer> onlinePlayers() {
        List<GamePlayer> out = new ArrayList<>();
        for (GameWorld w : worlds) {
            out.addAll(w.players());
        }
        return out;
    }

    @Override
    public GamePlayer player(UUID id) {
        for (GamePlayer p : onlinePlayers()) {
            if (p.uuid().equals(id)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public GamePlayer player(String name) {
        for (GamePlayer p : onlinePlayers()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public GameEntity entity(UUID id) {
        for (GameWorld w : worlds) {
            if (w instanceof FakeWorld fw) {
                for (GameEntity e : fw.entities) {
                    if (e.uuid().equals(id)) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public GameItem newItem(ItemType type, int amount) {
        return new FakeItem(type, amount);
    }

    @Override
    public GameItem emptyItem() {
        return new FakeItem(ItemType.of("AIR"), 0);
    }

    @Override
    public GameItem deserializeItem(byte[] data) {
        throw new UnsupportedOperationException("the fake server doesn't store items");
    }
}
