package dev.manhuntstar.fidelity;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.SkinData;
import dev.manhuntstar.host.BotTabList;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.GamePlayer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bots in the TAB list, like real players (with skin, ping and survival mode), through the player info
 * packets. The 26.x server uses Mojang's names, so the classes are found by reflection with no extra
 * dependency; if the structure changes in a future version, only this feature turns off (with a
 * warning in the log) and the rest of the plugin goes on.
 */
public final class TabListService implements BotTabList {

    private static final String PKT = "net.minecraft.network.protocol.game.";

    private final Logger logger;
    private final BooleanSupplier enabledSetting;
    private final Map<UUID, Bot> listed = new HashMap<>();
    private final Map<UUID, Integer> latency = new HashMap<>();
    private boolean available;

    private Class<?> actionClass;
    private Class<?> entryClass;
    private Constructor<?> updateCtorEntries;
    private Constructor<?> removeCtor;
    private Method asVanilla;
    private Object survival;
    private Method getHandle;
    private Field connectionField;
    private Method sendMethod;
    private Class<?> updatePacketClass;

    public TabListService(Logger logger, BooleanSupplier enabledSetting) {
        this.logger = logger;
        this.enabledSetting = enabledSetting;
    }

    /** Finds the classes and constructors (call once on enable). */
    public void init() {
        try {
            updatePacketClass = Class.forName(PKT + "ClientboundPlayerInfoUpdatePacket");
            actionClass = Class.forName(PKT + "ClientboundPlayerInfoUpdatePacket$Action");
            entryClass = Class.forName(PKT + "ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> removeClass = Class.forName(PKT + "ClientboundPlayerInfoRemovePacket");
            removeCtor = removeClass.getConstructor(List.class);
            for (Constructor<?> c : updatePacketClass.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0] == EnumSet.class && p[1] == List.class) {
                    c.setAccessible(true);
                    updateCtorEntries = c;
                }
            }
            Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
            survival = gameType.getField("SURVIVAL").get(null);
            Class<?> adventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
            asVanilla = adventure.getMethod("asVanilla", Component.class);
            Class<?> packetIface = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> craftPlayer = Bukkit.getOnlinePlayers().isEmpty()
                    ? Class.forName(Bukkit.getServer().getClass().getPackageName() + ".entity.CraftPlayer")
                    : Bukkit.getOnlinePlayers().iterator().next().getClass();
            getHandle = craftPlayer.getMethod("getHandle");
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            connectionField = serverPlayer.getField("connection");
            sendMethod = findMethod(connectionField.getType(), "send", packetIface);
            buildEntry(UUID.randomUUID(), "teste", null, Component.text("teste"), 0);
            available = true;
            logger.info("TAB list: bots show up as players.");
        } catch (Throwable t) {
            available = false;
            logger.log(Level.WARNING, "TAB list disabled: the packet structure changed in this version (" + t + ").");
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> param) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(param)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    public boolean isAvailable() {
        return available;
    }

    private boolean active() {
        return available && enabledSetting.getAsBoolean();
    }

    // ------------------------------------------------------------------ API

    @Override
    public void add(Bot bot) {
        listed.put(bot.id(), bot);
        latency.put(bot.id(), 20 + ThreadLocalRandom.current().nextInt(60));
        if (active()) {
            broadcast(addPacket(List.of(bot)));
        }
    }

    @Override
    public void remove(Bot bot) {
        listed.remove(bot.id());
        latency.remove(bot.id());
        if (available) {
            broadcast(removePacket(List.of(bot.id())));
        }
    }

    /** Updates name/skin (e.g. after /manhunt skin). */
    @Override
    public void refresh(Bot bot) {
        if (!active() || !listed.containsKey(bot.id())) {
            return;
        }
        broadcast(removePacket(List.of(bot.id())));
        broadcast(addPacket(List.of(bot)));
    }

    /** A player joined: they get all the bots. */
    @Override
    public void sendAll(GamePlayer player) {
        sendAll(PaperEntities.bukkit(player));
    }

    public void sendAll(Player player) {
        if (!active() || listed.isEmpty()) {
            return;
        }
        send(player, addPacket(new ArrayList<>(listed.values())));
    }

    /** Turns it on/off with the "tab-list" option. */
    @Override
    public void resync() {
        if (!available) {
            return;
        }
        if (enabledSetting.getAsBoolean()) {
            if (!listed.isEmpty()) {
                broadcast(addPacket(new ArrayList<>(listed.values())));
            }
        } else if (!listed.isEmpty()) {
            broadcast(removePacket(new ArrayList<>(listed.keySet())));
        }
    }

    /** A ping that varies a little, like a real player's (every 5 s). */
    @Override
    public void tick(int now) {
        if (!active() || listed.isEmpty() || now % 100 != 0) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (UUID id : listed.keySet()) {
            int cur = latency.getOrDefault(id, 40);
            latency.put(id, Math.max(8, Math.min(180, cur + r.nextInt(-6, 7))));
        }
        broadcast(packet(actionSet("UPDATE_LATENCY"), new ArrayList<>(listed.values())));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private EnumSet<?> actionSet(String... names) {
        EnumSet set = EnumSet.noneOf((Class) actionClass);
        for (String n : names) {
            try {
                set.add(action(n));
            } catch (IllegalArgumentException ignored) {
                // action that doesn't exist in this version
            }
        }
        return set;
    }

    @Override
    public void clear() {
        if (available && !listed.isEmpty()) {
            broadcast(removePacket(new ArrayList<>(listed.keySet())));
        }
        listed.clear();
    }

    // ------------------------------------------------------------------ packets

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object action(String name) {
        return Enum.valueOf((Class) actionClass, name);
    }

    private Object addPacket(Collection<Bot> bots) {
        return packet(actionSet("ADD_PLAYER", "UPDATE_GAME_MODE", "UPDATE_LISTED", "UPDATE_LATENCY", "UPDATE_DISPLAY_NAME", "UPDATE_HAT", "UPDATE_LIST_ORDER"), bots);
    }

    private Object packet(EnumSet<?> actions, Collection<Bot> bots) {
        try {
            List<Object> entries = new ArrayList<>();
            for (Bot b : bots) {
                entries.add(buildEntry(b.id(), b.profileName(), b.skin(), Component.text(b.visualName()), latency.getOrDefault(b.id(), 40)));
            }
            if (updateCtorEntries != null) {
                return updateCtorEntries.newInstance(actions, entries);
            }
            Object pkt = allocate(updatePacketClass);
            setField(pkt, EnumSet.class, actions);
            setField(pkt, List.class, entries);
            return pkt;
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private Object removePacket(List<UUID> ids) {
        try {
            return removeCtor.newInstance(new ArrayList<>(ids));
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private Object buildEntry(UUID id, String name, SkinData skin, Component display, int ping) throws Exception {
        Object profile = gameProfile(id, name, skin);
        Object vanillaName = asVanilla.invoke(null, display);
        RecordComponent[] comps = entryClass.getRecordComponents();
        Class<?>[] types = new Class<?>[comps.length];
        Object[] args = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            args[i] = switch (comps[i].getName()) {
                case "profileId" -> id;
                case "profile" -> profile;
                case "listed", "showHat" -> true;
                case "latency" -> ping;
                case "gameMode" -> survival;
                case "displayName" -> vanillaName;
                case "listOrder" -> 0;
                default -> defaultValue(types[i]);
            };
        }
        Constructor<?> c = entryClass.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    private static Object defaultValue(Class<?> t) {
        if (t == boolean.class) {
            return false;
        }
        if (t == int.class) {
            return 0;
        }
        if (t == long.class) {
            return 0L;
        }
        return null;
    }

    /** GameProfile from Paper's profile (works with the old GameProfile and with the new record one). */
    private static Object gameProfile(UUID id, String name, SkinData skin) throws Exception {
        PlayerProfile pp = Bukkit.createProfile(id, name);
        if (skin != null && skin.valid()) {
            pp.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
        }
        for (String m : new String[]{"buildGameProfile", "getGameProfile"}) {
            try {
                Method method = pp.getClass().getMethod(m);
                return method.invoke(pp);
            } catch (NoSuchMethodException ignored) {
                // try the next one
            }
        }
        throw new NoSuchMethodException("CraftPlayerProfile#buildGameProfile");
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        return unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    private static void setField(Object target, Class<?> type, Object value) throws Exception {
        for (Field f : target.getClass().getDeclaredFields()) {
            if (type.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                f.set(target, value);
                return;
            }
        }
        throw new NoSuchFieldException(type.getName());
    }

    private void broadcast(Object packet) {
        if (packet == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, packet);
        }
    }

    private void send(Player p, Object packet) {
        if (packet == null || !available) {
            return;
        }
        try {
            Object handle = getHandle.invoke(p);
            Object conn = connectionField.get(handle);
            if (conn != null) {
                sendMethod.invoke(conn, packet);
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    private void disable(Throwable t) {
        if (available) {
            available = false;
            logger.log(Level.WARNING, "TAB list disabled after an error: " + t);
        }
    }
}
