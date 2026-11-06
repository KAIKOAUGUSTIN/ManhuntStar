package dev.manhuntstar.tactics;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Ballistics;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.MotionTracker;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

/**
 * Special abilities (the mod's BotAggressive), which take control of the bot for a few seconds:
 * <ul>
 *   <li>TNT ambush: the bot shows up behind the player in the cave, sneaks in, places and lights
 *   a TNT near them and runs away;</li>
 *   <li>bow burst (phase 4+): stops, draws the bow as much as needed to reach the player and fires
 *   2–6 arrows;</li>
 *   <li>firework crossbow (phase 5+, experimental in the mod, off by default): a flight duration 3
 *   rocket, and it only fires if the rocket reaches the player before exploding.</li>
 * </ul>
 * In the mod the automatic activation was locked in the code; here it's the {@code tactics.auto-abilities} option.
 */
public final class TacticsManager {

    private static final int CHECK_INTERVAL = 40;
    private static final int STILL_TRIGGER_TICKS = 100;
    private static final int CRAFTING_WINDOW_TICKS = 60;
    private static final double TNT_TRIGGER_DISTANCE = 3.0;
    private static final int ESCAPE_TICKS = 24;
    /** Flight duration of the crossbow's firework rocket (as on the game's item, from 1 to 3). */
    private static final int FIREWORK_FLIGHT = 3;
    /** The rocket's shortest lifetime in the game: 10·(1 + duration) ticks, plus a random 0 to 11. */
    private static final int FIREWORK_MIN_LIFETIME = 10 * (1 + FIREWORK_FLIGHT);
    /** Speed of a rocket fired by the game's crossbow (CrossbowItem.getShootingPower). */
    private static final double FIREWORK_SPEED = 1.6;
    /** With the crossbow loaded, how long it waits for the player to come into range before giving up. */
    private static final int FIREWORK_HOLD_TICKS = 20;
    /** Bow burst draw, in ticks: from 10 (arrow at 1.25 blocks/tick) to a fully drawn bow (20, arrow at 3.0). */
    private static final int BOW_MIN_DRAW = 10;
    private static final int BOW_MAX_DRAW = 20;
    /** With the bow fully drawn, how long it waits for the player to come into range before giving up. */
    private static final int BOW_HOLD_TICKS = 20;

    private final BotManager manager;
    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private final Map<UUID, Long> playerCooldown = new HashMap<>();
    private final Map<UUID, Long> botCooldown = new HashMap<>();
    private final Map<UUID, TntAmbush> tnt = new HashMap<>();
    private final Map<UUID, BowBurst> bows = new HashMap<>();
    private final Map<UUID, FireworkShot> fireworks = new HashMap<>();
    private final Map<UUID, UUID> activeBotByPlayer = new HashMap<>();

    private static final class Tracker {
        Vec3 lastPos;
        long lastMovedTick;
        long lastCraftingTick = Long.MIN_VALUE / 2;
    }

    private static final class TntAmbush {
        final UUID bot;
        final UUID player;
        final Pos stand;
        final long started;
        Pos tntPos;
        boolean placed;
        boolean ignited;
        long igniteAt;
        long ignitedAt;

        TntAmbush(UUID bot, UUID player, Pos stand, long started) {
            this.bot = bot;
            this.player = player;
            this.stand = stand;
            this.started = started;
        }
    }

    private static final class BowBurst {
        final UUID bot;
        final UUID player;
        final ItemStack oldMain;
        final MotionTracker aim = new MotionTracker();
        int shots;
        int step;
        long nextAction;
        long drawStart;

        BowBurst(UUID bot, UUID player, ItemStack oldMain, int shots, long now) {
            this.bot = bot;
            this.player = player;
            this.oldMain = oldMain;
            this.shots = shots;
            this.nextAction = now;
        }
    }

    private static final class FireworkShot {
        final UUID bot;
        final UUID player;
        final ItemStack oldMain;
        final ItemStack oldOff;
        final MotionTracker aim = new MotionTracker();
        int step;
        long nextAction;

        FireworkShot(UUID bot, UUID player, ItemStack oldMain, ItemStack oldOff, long now) {
            this.bot = bot;
            this.player = player;
            this.oldMain = oldMain;
            this.oldOff = oldOff;
            this.nextAction = now;
        }
    }

    public TacticsManager(BotManager manager) {
        this.manager = manager;
    }

    public boolean isControllingBot(Bot bot) {
        UUID id = bot.id();
        return tnt.containsKey(id) || bows.containsKey(id) || fireworks.containsKey(id);
    }

    private boolean isBusy(Bot bot) {
        return isControllingBot(bot) || manager.caveAmbush().isReserved(bot);
    }

    /** A player opened a crafting table (vulnerable for 3 s to the automatic ambush). */
    public void recordCraftingTableUse(GamePlayer p) {
        trackers.computeIfAbsent(p.uuid(), k -> new Tracker()).lastCraftingTick = Bukkit.getCurrentTick();
    }

    /** Runs this bot's active ability (called by the brain, inside the bot's tick). */
    public void tickControlled(Bot bot) {
        long now = Bukkit.getCurrentTick();
        TntAmbush a = tnt.get(bot.id());
        if (a != null && !stepTnt(a, bot, now)) {
            tnt.remove(bot.id());
        }
        BowBurst b = bows.get(bot.id());
        if (b != null && !stepBow(b, bot, now)) {
            bows.remove(bot.id());
        }
        FireworkShot f = fireworks.get(bot.id());
        if (f != null && !stepFirework(f, bot, now)) {
            fireworks.remove(bot.id());
        }
    }

    /** Global ticks: automatic activation and cleanup of abilities of bots that are gone. */
    public void tick() {
        long now = Bukkit.getCurrentTick();
        tnt.values().removeIf(a -> {
            Bot bot = manager.bot(a.bot);
            if (bot == null || !bot.isAlive()) {
                cancelTnt(a, bot);
                return true;
            }
            return false;
        });
        bows.values().removeIf(s -> {
            Bot bot = manager.bot(s.bot);
            if (bot == null || !bot.isAlive()) {
                cleanupBow(s, bot);
                return true;
            }
            return false;
        });
        fireworks.values().removeIf(s -> {
            Bot bot = manager.bot(s.bot);
            if (bot == null || !bot.isAlive()) {
                cleanupFirework(s, bot);
                return true;
            }
            return false;
        });
        if (manager.plugin().settings().autoCapacities.on()) {
            refreshTrackers(now);
            // ring bots are left out in selectBot; the ones already hunting keep their abilities
            if (now % CHECK_INTERVAL == 0) {
                autoTrigger(now);
            }
        }
    }

    // ------------------------------------------------------------------ automatic

    private void refreshTrackers(long now) {
        List<UUID> online = new ArrayList<>();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            online.add(p.uuid());
            Tracker t = trackers.computeIfAbsent(p.uuid(), k -> new Tracker());
            if (now % 10 == 0) {
                Vec3 cur = p.position();
                if (t.lastPos == null || cur.distanceToSqr(t.lastPos) > 0.04) {
                    t.lastMovedTick = now;
                    t.lastPos = cur;
                }
            }
        }
        trackers.keySet().retainAll(online);
        playerCooldown.keySet().retainAll(online);
        activeBotByPlayer.keySet().retainAll(online);
    }

    private void autoTrigger(long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (GamePlayer p : Platform.server().onlinePlayers()) {
            if (!manager.isEligibleTarget(p) || activeBotByPlayer.containsKey(p.uuid())) {
                continue;
            }
            if (playerCooldown.getOrDefault(p.uuid(), Long.MIN_VALUE) > now) {
                continue;
            }
            if (CaveAmbush.isInCave(PaperWorld.of(PaperWorld.bukkit(p.world())), p.blockPos()) && isVulnerable(p, now)) {
                Bot bot = selectBot(p, now, false, 1);
                if (bot != null && r.nextDouble() < 0.015 * bot.aggression()) {
                    startTntAmbush(p, bot, now, false);
                    continue;
                }
            }
            if (manager.plugin().settings().fireworkCrossbow.on()) {
                Bot bot = selectBot(p, now, false, 5, b -> fireworkReaches(b, p, Vec3.ZERO));
                if (bot != null) {
                    double chance = (0.2 + (bot.progression().phase() - 5) * 0.2 + (Math.max(1, bot.aggression()) - 1) * 0.1) * (CHECK_INTERVAL / 20.0);
                    if (bot.aggression() > 0 && r.nextDouble() < chance) {
                        startFirework(p, bot, now);
                    }
                }
            }
        }
    }

    private boolean isVulnerable(GamePlayer p, long now) {
        Tracker t = trackers.get(p.uuid());
        if (t == null) {
            return false;
        }
        return now - t.lastMovedTick >= STILL_TRIGGER_TICKS || now - t.lastCraftingTick <= CRAFTING_WINDOW_TICKS;
    }

    private Bot selectBot(GamePlayer p, long now, boolean ignoreCooldown, int minPhase) {
        return selectBot(p, now, ignoreCooldown, minPhase, b -> true);
    }

    private Bot selectBot(GamePlayer p, long now, boolean ignoreCooldown, int minPhase, Predicate<Bot> able) {
        List<Bot> eligible = new ArrayList<>();
        for (Bot b : manager.aliveBots()) {
            // bots waiting in the ring don't leave it to ambush
            if (!b.world().equals(p.world()) || !p.uuid().equals(b.targetPlayerId()) || b.progression().phase() < minPhase || isBusy(b)
                    || b.idle() != null) {
                continue;
            }
            if (!ignoreCooldown && botCooldown.getOrDefault(b.id(), Long.MIN_VALUE) > now) {
                continue;
            }
            if (!able.test(b)) {
                continue;
            }
            eligible.add(b);
        }
        return eligible.isEmpty() ? null : eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
    }

    private static void prepare(Bot bot) {
        bot.motor().stopWalk();
        bot.motor().setSprinting(false);
        bot.combat().cancelWeaponUseForCapacity();
        bot.mining().cancel();
    }

    // ------------------------------------------------------------------ test command (/manhunt test)

    /** @return an error message or null if it started. */
    public String debugExecute(GamePlayer player, String capacity) {
        if (player.isDead()) {
            return "must-be-alive";
        }
        if (activeBotByPlayer.containsKey(player.uuid())) {
            return "already-active";
        }
        long now = Bukkit.getCurrentTick();
        switch (capacity.toLowerCase(java.util.Locale.ROOT)) {
            case "tnt-craft" -> {
                if (!CaveAmbush.isInCave(PaperWorld.of(PaperWorld.bukkit(player.world())), player.blockPos())) {
                    return "not-in-cave";
                }
                Bot bot = selectBot(player, now, true, 1);
                if (bot == null) {
                    return "no-eligible-bot";
                }
                prepare(bot);
                return startTntAmbush(player, bot, now, true) ? null : "no-ambush-spot";
            }
            case "bow-burst" -> {
                if (selectBot(player, now, true, 4) == null) {
                    return "no-eligible-bot";
                }
                Bot bot = selectBot(player, now, true, 4, b -> bowDraw(b, player, Vec3.ZERO) >= 0);
                if (bot == null) {
                    return "arrow-out-of-range";
                }
                startBowBurst(bot, player, now);
                return null;
            }
            case "firework-crossbow" -> {
                if (selectBot(player, now, true, 5) == null) {
                    return "no-eligible-bot";
                }
                Bot bot = selectBot(player, now, true, 5, b -> fireworkReaches(b, player, Vec3.ZERO));
                if (bot == null) {
                    return "firework-out-of-range";
                }
                startFirework(player, bot, now);
                return null;
            }
            default -> {
                return "unknown";
            }
        }
    }

    // ------------------------------------------------------------------ bow burst

    /** Called by the AI when the player goes too long without being hit (bow pressure). */
    public boolean tryStartBowBurst(Bot bot, GamePlayer player, long now) {
        if (!bot.isAlive() || player.isDead() || !bot.world().equals(player.world()) || bot.progression().phase() < 4) {
            return false;
        }
        if (!player.uuid().equals(bot.targetPlayerId()) || activeBotByPlayer.containsKey(player.uuid()) || isBusy(bot)) {
            return false;
        }
        // not even with the bow fully drawn does the arrow get there (too far for the height difference, or a block in the way)
        if (bowDraw(bot, player, Vec3.ZERO) < 0) {
            return false;
        }
        startBowBurst(bot, player, now);
        return true;
    }

    private void startBowBurst(Bot bot, GamePlayer player, long now) {
        prepare(bot);
        int shots = 2 + Math.round((Math.max(1, Math.min(10, Math.max(1, bot.aggression()))) - 1) * 0.44444445f);
        activeBotByPlayer.put(player.uuid(), bot.id());
        bows.put(bot.id(), new BowBurst(bot.id(), player.uuid(), bot.getItem(EquipmentSlot.HAND).clone(), shots, now));
    }

    /** @return false when the burst is over (and already cleaned up). */
    private boolean stepBow(BowBurst s, Bot bot, long now) {
        GamePlayer p = Platform.server().player(s.player);
        if (!validContext(bot, p)) {
            cleanupBow(s, bot);
            return false;
        }
        bot.lookAt(p.eyePosition());
        bot.motor().stopWalk();
        s.aim.observe(p);
        if (now < s.nextAction) {
            return true;
        }
        BotBody e = bot.entity();
        switch (s.step) {
            case 0 -> {
                int draw = bowDraw(bot, p, s.aim.velocity());
                if (draw < 0) {
                    cleanupBow(s, bot);
                    return false;
                }
                bot.stand();
                bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.BOW));
                PaperEntities.bukkit(e).startUsingItem(EquipmentSlot.HAND);
                s.drawStart = now;
                s.step = 1;
                s.nextAction = now + draw;
            }
            case 1 -> {
                // at release time the player may have moved out of this draw's range: draw more; with the bow
                // fully drawn wait a little and give up without shooting
                double speed = Ballistics.bowSpeed((int) (now - s.drawStart));
                if (!arrowReaches(bot, p, s.aim.velocity(), speed)) {
                    if (now - s.drawStart >= BOW_MAX_DRAW + BOW_HOLD_TICKS) {
                        cleanupBow(s, bot);
                        return false;
                    }
                    return true;
                }
                PaperEntities.bukkit(e).clearActiveItem();
                shootArrow(bot, p, s.aim.velocity(), speed);
                bot.swing();
                if (--s.shots > 0) {
                    s.step = 0;
                    s.nextAction = now + 4 + ThreadLocalRandom.current().nextInt(5);
                } else {
                    s.step = 2;
                    s.nextAction = now + 1;
                }
            }
            default -> {
                cleanupBow(s, bot);
                return false;
            }
        }
        return true;
    }

    private void cleanupBow(BowBurst s, Bot bot) {
        if (bot != null && bot.isAlive()) {
            bot.mannequin().clearActiveItem();
            bot.setItem(EquipmentSlot.HAND, s.oldMain);
            bot.stand();
        }
        activeBotByPlayer.remove(s.player);
    }

    /**
     * Arrow from the drawn bow. In the mod the elevation was a fixed formula ({@code 0.24·d + 0.0032·d²}) that
     * went 1.5 to 2.5 blocks over the head; here the speed comes from the draw, the angle from the arrow's
     * physics (with the height difference) and the player is led by the flight time.
     */
    private static void shootArrow(Bot bot, GamePlayer player, Vec3 playerVelocity, double speed) {
        BotBody e = bot.entity();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 from = arrowOrigin(bot);
        Vec3 point = arrowTarget(player);
        double dist = point.horizontalDistance(from);
        double spread = Math.min(0.22, 0.02 + dist * 0.005) * bot.settings().projInaccuracy.value();
        point = point.add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread * 0.35, (r.nextDouble() - 0.5) * spread);
        Vec3 aim = Ballistics.aimArrow(from, point, playerVelocity, speed);
        Vector vel = shootVector(Locations.vector(aim), (float) speed, 0.25f * (float) bot.settings().projInaccuracy.value());
        Arrow arrow = PaperEntities.bukkit(e).launchProjectile(Arrow.class, vel);
        dev.manhuntstar.debug.AimDebug.track(arrow, bot.name(), PaperEntities.bukkit(player));
        arrow.setShooter(PaperEntities.bukkit(e));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        // as in the game: an arrow from a fully drawn bow is critical
        arrow.setCritical(speed >= Ballistics.bowSpeed(BOW_MAX_DRAW));
        e.world().playSound(e.position(), Sounds.ENTITY_ARROW_SHOOT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1f, 1f / (r.nextFloat() * 0.4f + 0.8f));
    }

    private static Vec3 arrowOrigin(Bot bot) {
        BotBody e = bot.entity();
        return new Vec3(e.position().x(), PaperEntities.bukkit(e).getEyeLocation().getY() - 0.1, e.position().z());
    }

    private static Vec3 arrowTarget(GamePlayer player) {
        return new Vec3(player.position().x(), player.position().y() + player.height() * 0.55, player.position().z());
    }

    /**
     * How many ticks to draw the bow: among the draws that reach the player (the height difference counts, and
     * the trajectory can't hit a block), the one that hits soonest adding up draw time and flight time. Up close
     * it draws a little and shoots fast; far away it draws all the way. -1 if not even a fully drawn bow reaches.
     */
    private static int bowDraw(Bot bot, GamePlayer player, Vec3 playerVelocity) {
        Vec3 from = arrowOrigin(bot);
        Vec3 to = arrowTarget(player);
        List<double[]> options = new ArrayList<>();
        for (int draw = BOW_MIN_DRAW; draw <= BOW_MAX_DRAW; draw++) {
            Ballistics.Shot shot = Ballistics.shotArrow(from, to, playerVelocity, Ballistics.bowSpeed(draw));
            if (shot.reachable()) {
                options.add(new double[]{draw + shot.ticks(), draw});
            }
        }
        options.sort(Comparator.comparingDouble(o -> o[0]));
        for (double[] o : options) {
            int draw = (int) o[1];
            Ballistics.Shot shot = Ballistics.shotArrow(from, to, playerVelocity, Ballistics.bowSpeed(draw));
            if (arrowPathClear(bot.world(), from, shot.velocity(), shot.ticks())) {
                return draw;
            }
        }
        return -1;
    }

    /** An arrow with this speed reaches the player without hitting a block on the way. */
    private static boolean arrowReaches(Bot bot, GamePlayer player, Vec3 playerVelocity, double speed) {
        Vec3 from = arrowOrigin(bot);
        Ballistics.Shot shot = Ballistics.shotArrow(from, arrowTarget(player), playerVelocity, speed);
        return shot.reachable() && arrowPathClear(bot.world(), from, shot.velocity(), shot.ticks());
    }

    /** Follows the arrow's trajectory tick by tick (moves, loses 1% and drops 0.05) up to the flight time, without hitting a block. */
    private static boolean arrowPathClear(GameWorld w, Vec3 from, Vec3 velocity, double ticks) {
        Vec3 p = from;
        Vec3 v = velocity;
        for (int t = 0; t < ticks; t++) {
            Vec3 next = p.add(v.scale(Math.min(1.0, ticks - t)));
            if (!WorldUtil.clearLine(w, p, next)) {
                return false;
            }
            p = next;
            v = new Vec3(v.x() * Ballistics.ARROW_DRAG, v.y() * Ballistics.ARROW_DRAG - Ballistics.ARROW_GRAVITY, v.z() * Ballistics.ARROW_DRAG);
        }
        return true;
    }

    /** The game's {@code shoot}: normalizes, adds gaussian noise * 0.0075 * inaccuracy and scales. */
    public static Vector shootVector(Vector dir, float speed, float inaccuracy) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector v = dir.clone().normalize();
        v.add(new Vector(r.nextGaussian() * 0.0075 * inaccuracy, r.nextGaussian() * 0.0075 * inaccuracy, r.nextGaussian() * 0.0075 * inaccuracy));
        return v.multiply(speed);
    }

    // ------------------------------------------------------------------ firework crossbow

    private void startFirework(GamePlayer player, Bot bot, long now) {
        prepare(bot);
        activeBotByPlayer.put(player.uuid(), bot.id());
        fireworks.put(bot.id(), new FireworkShot(bot.id(), player.uuid(), bot.getItem(EquipmentSlot.HAND).clone(),
                bot.getItem(EquipmentSlot.OFF_HAND).clone(), now));
    }

    private boolean stepFirework(FireworkShot s, Bot bot, long now) {
        GamePlayer p = Platform.server().player(s.player);
        if (!validContext(bot, p)) {
            cleanupFirework(s, bot);
            return false;
        }
        bot.lookAt(p.eyePosition());
        bot.motor().stopWalk();
        s.aim.observe(p);
        if (now < s.nextAction) {
            return true;
        }
        BotBody e = bot.entity();
        switch (s.step) {
            case 0 -> {
                bot.stand();
                bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.CROSSBOW));
                bot.setItem(EquipmentSlot.OFF_HAND, fireworkRocket());
                PaperEntities.bukkit(e).startUsingItem(EquipmentSlot.HAND);
                e.world().playSound(e.position(), Sounds.ITEM_CROSSBOW_LOADING_START, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1f, 1f);
                s.step = 1;
                s.nextAction = now + 25;
            }
            case 1 -> {
                // only fires if the rocket reaches the player before exploding; otherwise keeps the crossbow loaded
                // (nextAction stays at the end of the charge) and gives up without firing if the player doesn't come into range
                if (!fireworkReaches(bot, p, s.aim.velocity())) {
                    if (now - s.nextAction >= FIREWORK_HOLD_TICKS) {
                        cleanupFirework(s, bot);
                        return false;
                    }
                    return true;
                }
                PaperEntities.bukkit(e).clearActiveItem();
                shootFirework(bot, p, s.aim.velocity());
                bot.swing();
                s.step = 2;
                s.nextAction = now + 4;
            }
            default -> {
                cleanupFirework(s, bot);
                return false;
            }
        }
        return true;
    }

    private void cleanupFirework(FireworkShot s, Bot bot) {
        if (bot != null && bot.isAlive()) {
            bot.mannequin().clearActiveItem();
            bot.setItem(EquipmentSlot.HAND, s.oldMain);
            bot.setItem(EquipmentSlot.OFF_HAND, s.oldOff);
            bot.stand();
        }
        activeBotByPlayer.remove(s.player);
    }

    /** A firework fired from a crossbow flies straight (no gravity), so the aim is direct, with lead. */
    private static void shootFirework(Bot bot, GamePlayer player, Vec3 playerVelocity) {
        BotBody e = bot.entity();
        Vec3 from = fireworkOrigin(bot);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 point = chest(player);
        double dist = point.horizontalDistance(from);
        double inacc = 0.04 + dist * 0.006;
        point = point.add((r.nextDouble() - 0.5) * inacc, (r.nextDouble() - 0.5) * inacc, (r.nextDouble() - 0.5) * inacc);
        Vec3 aim = Ballistics.aim(from, point, playerVelocity, FIREWORK_SPEED, 0, 1);
        Vector vel = shootVector(Locations.vector(aim), (float) FIREWORK_SPEED, 0.65f);
        Location at = Locations.location(from, e.world());
        PaperWorld.bukkit(e.world()).spawn(at, Firework.class, fw -> {
            // Paper rolls the lifetime from the flight duration, like the game
            fw.setFireworkMeta((FireworkMeta) fireworkRocket().getItemMeta());
            fw.setShooter(PaperEntities.bukkit(e));
            fw.setShotAtAngle(true);
            fw.setVelocity(vel);
        });
        e.world().playSound(e.position(), Sounds.ITEM_CROSSBOW_SHOOT, dev.manhuntstar.platform.SoundCategory.PLAYERS, 1f, 1f / (r.nextFloat() * 0.4f + 0.8f));
    }

    private static ItemStack fireworkRocket() {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        rocket.editMeta(FireworkMeta.class, meta -> {
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.RED, Color.ORANGE).build());
            meta.setPower(FIREWORK_FLIGHT);
        });
        return rocket;
    }

    private static Vec3 fireworkOrigin(Bot bot) {
        return bot.eyePos().add(0, -0.1, 0);
    }

    private static Vec3 chest(GamePlayer player) {
        return new Vec3(player.position().x(), player.position().y() + player.height() * 0.55, player.position().z());
    }

    /**
     * The rocket reaches the player before exploding: the straight shot gets to the led point within the
     * rocket's shortest lifetime, with no block in the way.
     */
    private static boolean fireworkReaches(Bot bot, GamePlayer player, Vec3 playerVelocity) {
        Vec3 from = fireworkOrigin(bot);
        Vec3 target = chest(player);
        double ticks = Ballistics.straightTicks(from, target, playerVelocity, FIREWORK_SPEED);
        return ticks <= FIREWORK_MIN_LIFETIME && WorldUtil.clearLine(bot.world(), from, target.add(playerVelocity.scale(ticks)));
    }

    // ------------------------------------------------------------------ TNT ambush

    private boolean startTntAmbush(GamePlayer player, Bot bot, long now, boolean ignoreCooldown) {
        GameWorld w = PaperWorld.of(PaperWorld.bukkit(player.world()));
        Pos stand = findAmbushStart(w, player);
        if (stand == null) {
            return false;
        }
        if (!ignoreCooldown) {
            long until = now + 2400;
            playerCooldown.put(player.uuid(), until);
            botCooldown.put(bot.id(), until);
        }
        prepare(bot);
        activeBotByPlayer.put(player.uuid(), bot.id());
        manager.teleportBot(bot, new Location(PaperWorld.bukkit(w), stand.x() + 0.5, stand.y() + 0.1, stand.z() + 0.5));
        bot.lookAt(player.eyePosition());
        bot.setSneaking(true);
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.TNT));
        tnt.put(bot.id(), new TntAmbush(bot.id(), player.uuid(), stand, now));
        return true;
    }

    /** @return false when the ambush is over (and already cleaned up). */
    private boolean stepTnt(TntAmbush a, Bot bot, long now) {
        GamePlayer p = Platform.server().player(a.player);
        if (!tntStillValid(a, bot, p) || now - a.started > 600) {
            cancelTnt(a, bot);
            return false;
        }
        GameWorld w = PaperWorld.of(PaperWorld.bukkit(p.world()));
        if (!a.placed) {
            bot.lookAt(p.eyePosition());
            bot.setSneaking(true);
            Vec3 dir = p.position().subtract(bot.pos()).horizontal();
            if (dir.lengthSqr() > 1.0E-6) {
                bot.motor().walk(dir.normalize());
                if (bot.horizontalCollision() && bot.onGround()) {
                    bot.motor().jump();
                }
            }
            if (bot.distanceTo(p) <= TNT_TRIGGER_DISTANCE) {
                Pos tntPos = findTntPos(w, p, bot.blockPos());
                if (tntPos == null) {
                    cancelTnt(a, bot);
                    return false;
                }
                w.block(tntPos).setType(Blocks.TNT);
                // if the ambush ends before lighting (the bot died, the player ran), the TNT disappears
                manager.temporaryBlocks().add(w, tntPos, Blocks.TNT, 40);
                w.playSound(Vec3.of(tntPos), Sounds.BLOCK_GRASS_PLACE, dev.manhuntstar.platform.SoundCategory.BLOCKS, 1f, 1f);
                bot.swing();
                a.tntPos = tntPos;
                a.placed = true;
                a.igniteAt = now + 2;
            }
            return true;
        }
        if (!a.ignited) {
            bot.motor().stopWalk();
            if (now < a.igniteAt) {
                return true;
            }
            if (a.tntPos == null || w.block(a.tntPos).type() != Blocks.TNT) {
                cancelTnt(a, bot);
                return false;
            }
            manager.temporaryBlocks().remove(w, a.tntPos);
            w.block(a.tntPos).setType(Blocks.AIR);
            Location at = new Location(PaperWorld.bukkit(w), a.tntPos.x() + 0.5, a.tntPos.y(), a.tntPos.z() + 0.5);
            PaperWorld.bukkit(w).spawn(at, TNTPrimed.class, t -> {
                t.setFuseTicks(40);
                t.setSource(bot.mannequin());
            });
            w.playSound(Locations.vec(at), Sounds.ITEM_FLINTANDSTEEL_USE, dev.manhuntstar.platform.SoundCategory.BLOCKS, 1f, 1f);
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.FLINT_AND_STEEL));
            bot.swing();
            a.ignited = true;
            a.ignitedAt = now;
            return true;
        }
        Vec3 away = bot.pos().subtract(p.position()).horizontal();
        away = away.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : away.normalize();
        bot.setSneaking(false);
        bot.motor().setSprinting(true);
        bot.lookAt(bot.eyePos().add(away.scale(2)));
        bot.motor().walk(away);
        if (bot.onGround() && bot.horizontalCollision()) {
            bot.motor().jump();
        }
        if (now - a.ignitedAt >= ESCAPE_TICKS) {
            bot.motor().setSprinting(false);
            bot.stand();
            bot.inventory().restorePreferredMainHand();
            activeBotByPlayer.remove(a.player);
            return false;
        }
        return true;
    }

    private boolean tntStillValid(TntAmbush a, Bot bot, GamePlayer p) {
        if (bot == null || p == null || !bot.isAlive() || p.isDead() || !bot.world().equals(p.world())) {
            return false;
        }
        Pos pp = p.blockPos();
        if (!CaveAmbush.isInCave(p.world(), pp) || !CaveAmbush.isSameCave(p.world(), pp, a.stand)) {
            return false;
        }
        return a.tntPos == null || CaveAmbush.isSameCave(PaperWorld.of(PaperWorld.bukkit(p.world())), pp, a.tntPos);
    }

    private void cancelTnt(TntAmbush a, Bot bot) {
        if (bot != null && bot.isAlive()) {
            bot.motor().setSprinting(false);
            bot.stand();
            bot.inventory().restorePreferredMainHand();
        }
        activeBotByPlayer.remove(a.player);
    }

    private static Pos findAmbushStart(GameWorld w, GamePlayer player) {
        Vec3 look = player.direction().horizontal();
        look = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z(), 0, look.x());
        Vec3 pp = player.position();
        Pos playerPos = player.blockPos();
        for (double dist : new double[]{8.0, 7.5, 8.5, 9.0}) {
            for (double s : new double[]{0, 1, -1, 2, -2}) {
                for (int yOff : new int[]{0, 1, -1}) {
                    Vec3 anchor = pp.subtract(look.scale(dist)).add(side.scale(s));
                    Pos pos = new Pos((int) Math.floor(anchor.x()), (int) Math.floor(pp.y()) + yOff, (int) Math.floor(anchor.z()));
                    if (!validCaveStand(w, playerPos, pos)) {
                        continue;
                    }
                    double d = pos.distSqr(playerPos);
                    if (d >= 36.0 && d <= 144.0) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean validCaveStand(GameWorld w, Pos playerPos, Pos pos) {
        if (!WorldUtil.isLoaded(w, pos) || WorldUtil.hasCollision(w, pos) || WorldUtil.hasCollision(w, pos.above())) {
            return false;
        }
        if (WorldUtil.hasFluid(w, pos) || WorldUtil.hasFluid(w, pos.above())) {
            return false;
        }
        if (!WorldUtil.blocksMotion(w, pos.below()) || WorldUtil.isLava(w, pos.below())) {
            return false;
        }
        return CaveAmbush.isSameCave(w, playerPos, pos);
    }

    private static Pos findTntPos(GameWorld w, GamePlayer player, Pos botPos) {
        Vec3 flat = player.position().subtract(Vec3.center(botPos)).horizontal();
        BlockFace forward = Locations.faceFromYaw(flat.yaw());
        Pos center = player.blockPos();
        Pos[] candidates = {center, Locations.relative(center, forward), Locations.relative(center, forward.getOppositeFace()),
                center.offset(0, 0, -1), center.offset(0, 0, 1), center.offset(1, 0, 0), center.offset(-1, 0, 0)};
        for (Pos c : candidates) {
            if (WorldUtil.canBeReplaced(w, c) && WorldUtil.blocksMotion(w, c.below()) && !WorldUtil.isLava(w, c) && !WorldUtil.isLava(w, c.below())
                    && CaveAmbush.isSameCave(w, center, c) && botPos.distSqr(c) <= 16.0) {
                return c;
            }
        }
        return null;
    }

    private static boolean validContext(Bot bot, GamePlayer p) {
        return bot != null && p != null && bot.isAlive() && !p.isDead() && bot.world().equals(p.world());
    }

    public void clear() {
        for (TntAmbush a : tnt.values()) {
            cancelTnt(a, manager.bot(a.bot));
        }
        for (BowBurst b : bows.values()) {
            cleanupBow(b, manager.bot(b.bot));
        }
        for (FireworkShot f : fireworks.values()) {
            cleanupFirework(f, manager.bot(f.bot));
        }
        tnt.clear();
        bows.clear();
        fireworks.clear();
        activeBotByPlayer.clear();
    }

    public void forget(Bot bot) {
        tnt.remove(bot.id());
        bows.remove(bot.id());
        fireworks.remove(bot.id());
        activeBotByPlayer.values().removeIf(id -> id.equals(bot.id()));
    }
}
