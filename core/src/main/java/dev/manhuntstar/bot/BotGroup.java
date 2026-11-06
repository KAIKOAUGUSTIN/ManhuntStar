package dev.manhuntstar.bot;

import dev.manhuntstar.bot.Modes.BotRole;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.util.Vec3;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Bots created together: role (hunt/escape), enemy groups, allowed mobs and respawn point. */
public final class BotGroup {

    private final String name;
    private BotRole role = BotRole.HUNTER;
    private boolean trackPlayers = true;
    private final Set<String> enemies = new LinkedHashSet<>();
    private Set<EntityKind> allowedMobTargets;
    private Vec3 spawnpoint;
    private String spawnWorld;

    public BotGroup(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public BotRole role() {
        return role;
    }

    public void setRole(BotRole role) {
        this.role = role == null ? BotRole.HUNTER : role;
    }

    public boolean trackPlayers() {
        return trackPlayers;
    }

    public void setTrackPlayers(boolean track) {
        this.trackPlayers = track;
    }

    public Set<String> enemies() {
        return Collections.unmodifiableSet(enemies);
    }

    public void addEnemy(String group) {
        enemies.add(group.toLowerCase(Locale.ROOT));
    }

    public void removeEnemy(String group) {
        enemies.remove(group.toLowerCase(Locale.ROOT));
    }

    public boolean isEnemy(String other) {
        return other != null && enemies.contains(other.toLowerCase(Locale.ROOT));
    }

    /** null = follows the global rule; a set = only attacks these mob types. */
    public Set<EntityKind> allowedMobTargets() {
        return allowedMobTargets;
    }

    public void setAllowedMobTargets(Set<EntityKind> types) {
        this.allowedMobTargets = types == null ? null : new LinkedHashSet<>(types);
    }

    public Vec3 spawnpoint() {
        return spawnpoint;
    }

    public String spawnWorld() {
        return spawnWorld;
    }

    public void setSpawnpoint(String world, Vec3 pos) {
        this.spawnWorld = world;
        this.spawnpoint = pos;
    }
}
