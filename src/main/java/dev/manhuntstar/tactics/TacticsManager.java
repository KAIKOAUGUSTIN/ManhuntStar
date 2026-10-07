package dev.manhuntstar.tactics;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.util.Ballistics;
import dev.manhuntstar.util.MotionTracker;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

/**
 * Capacidades especiais (o BotAggressive do mod), que tomam o controle do bot por alguns segundos:
 * <ul>
 *   <li>emboscada de TNT: o bot surge atrás do jogador na caverna, chega agachado, coloca e acende
 *   uma TNT perto dele e foge;</li>
 *   <li>rajada de arco (fase 4+): para, puxa o arco e dispara 2–6 flechas;</li>
 *   <li>besta com fogos (fase 5+, experimental no mod, desligada por padrão).</li>
 * </ul>
 * No mod a ativação automática estava travada no código; aqui é a opção "capacidades-automaticas".
 */
public final class TacticsManager {

    private static final int CHECK_INTERVAL = 40;
    private static final int STILL_TRIGGER_TICKS = 100;
    private static final int CRAFTING_WINDOW_TICKS = 60;
    private static final double TNT_TRIGGER_DISTANCE = 3.0;
    private static final int ESCAPE_TICKS = 24;

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

    /** Jogador abriu uma mesa de trabalho (fica vulnerável por 3 s para a emboscada automática). */
    public void recordCraftingTableUse(Player p) {
        trackers.computeIfAbsent(p.getUniqueId(), k -> new Tracker()).lastCraftingTick = Bukkit.getCurrentTick();
    }

    /** Executa a capacidade ativa deste bot (chamado pelo cérebro, dentro do tick do bot). */
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

    /** Ticks globais: ativação automática e limpeza de capacidades de bots que sumiram. */
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
            if (now % CHECK_INTERVAL == 0 && !manager.countdown().isActive()) {
                autoTrigger(now);
            }
        }
    }

    // ------------------------------------------------------------------ automático

    private void refreshTrackers(long now) {
        List<UUID> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            Tracker t = trackers.computeIfAbsent(p.getUniqueId(), k -> new Tracker());
            if (now % 10 == 0) {
                Vec3 cur = Vec3.of(p.getLocation());
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!manager.isEligibleTarget(p) || activeBotByPlayer.containsKey(p.getUniqueId())) {
                continue;
            }
            if (playerCooldown.getOrDefault(p.getUniqueId(), Long.MIN_VALUE) > now) {
                continue;
            }
            if (CaveAmbush.isInCave(p.getWorld(), Pos.of(p.getLocation())) && isVulnerable(p, now)) {
                Bot bot = selectBot(p, now, false, 1);
                if (bot != null && r.nextDouble() < 0.015 * bot.aggression()) {
                    startTntAmbush(p, bot, now, false);
                    continue;
                }
            }
            if (manager.plugin().settings().fireworkCrossbow.on()) {
                Bot bot = selectBot(p, now, false, 5);
                if (bot != null) {
                    double chance = (0.2 + (bot.progression().phase() - 5) * 0.2 + (Math.max(1, bot.aggression()) - 1) * 0.1) * (CHECK_INTERVAL / 20.0);
                    if (bot.aggression() > 0 && r.nextDouble() < chance) {
                        startFirework(p, bot, now);
                    }
                }
            }
        }
    }

    private boolean isVulnerable(Player p, long now) {
        Tracker t = trackers.get(p.getUniqueId());
        if (t == null) {
            return false;
        }
        return now - t.lastMovedTick >= STILL_TRIGGER_TICKS || now - t.lastCraftingTick <= CRAFTING_WINDOW_TICKS;
    }

    private Bot selectBot(Player p, long now, boolean ignoreCooldown, int minPhase) {
        List<Bot> eligible = new ArrayList<>();
        for (Bot b : manager.aliveBots()) {
            if (!b.world().equals(p.getWorld()) || !p.getUniqueId().equals(b.targetPlayerId()) || b.progression().phase() < minPhase || isBusy(b)) {
                continue;
            }
            if (!ignoreCooldown && botCooldown.getOrDefault(b.id(), Long.MIN_VALUE) > now) {
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

    // ------------------------------------------------------------------ comando de teste (/bot execute capacity)

    /** @return mensagem de erro ou null se iniciou. */
    public String debugExecute(Player player, String capacity) {
        if (player.isDead()) {
            return "voce-precisa-estar-vivo";
        }
        if (activeBotByPlayer.containsKey(player.getUniqueId())) {
            return "capacidade-ja-ativa";
        }
        long now = Bukkit.getCurrentTick();
        switch (capacity.toLowerCase(java.util.Locale.ROOT)) {
            case "tnt-craft" -> {
                if (!CaveAmbush.isInCave(player.getWorld(), Pos.of(player.getLocation()))) {
                    return "fora-da-caverna";
                }
                Bot bot = selectBot(player, now, true, 1);
                if (bot == null) {
                    return "sem-bot-elegivel";
                }
                prepare(bot);
                return startTntAmbush(player, bot, now, true) ? null : "sem-posicao-emboscada";
            }
            case "bow-burst" -> {
                Bot bot = selectBot(player, now, true, 4);
                if (bot == null) {
                    return "sem-bot-elegivel";
                }
                startBowBurst(bot, player, now);
                return null;
            }
            case "firework-crossbow" -> {
                Bot bot = selectBot(player, now, true, 5);
                if (bot == null) {
                    return "sem-bot-elegivel";
                }
                startFirework(player, bot, now);
                return null;
            }
            default -> {
                return "capacidade-desconhecida";
            }
        }
    }

    // ------------------------------------------------------------------ rajada de arco

    /** Chamado pela IA quando o jogador fica muito tempo sem ser acertado (pressão de arco). */
    public boolean tryStartBowBurst(Bot bot, Player player, long now) {
        if (!bot.isAlive() || player.isDead() || !bot.world().equals(player.getWorld()) || bot.progression().phase() < 4) {
            return false;
        }
        if (!player.getUniqueId().equals(bot.targetPlayerId()) || activeBotByPlayer.containsKey(player.getUniqueId()) || isBusy(bot)) {
            return false;
        }
        startBowBurst(bot, player, now);
        return true;
    }

    private void startBowBurst(Bot bot, Player player, long now) {
        prepare(bot);
        int shots = 2 + Math.round((Math.max(1, Math.min(10, Math.max(1, bot.aggression()))) - 1) * 0.44444445f);
        activeBotByPlayer.put(player.getUniqueId(), bot.id());
        bows.put(bot.id(), new BowBurst(bot.id(), player.getUniqueId(), bot.getItem(EquipmentSlot.HAND).clone(), shots, now));
    }

    /** @return false quando a rajada terminou (e já foi limpa). */
    private boolean stepBow(BowBurst s, Bot bot, long now) {
        Player p = Bukkit.getPlayer(s.player);
        if (!validContext(bot, p)) {
            cleanupBow(s, bot);
            return false;
        }
        bot.lookAt(Vec3.of(p.getEyeLocation()));
        bot.motor().stopWalk();
        s.aim.observe(p);
        if (now < s.nextAction) {
            return true;
        }
        Mannequin e = bot.entity();
        switch (s.step) {
            case 0 -> {
                bot.stand();
                bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.BOW));
                e.startUsingItem(EquipmentSlot.HAND);
                s.step = 1;
                s.nextAction = now + 20;
            }
            case 1 -> {
                e.clearActiveItem();
                shootArrow(bot, p, s.aim.velocity());
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
            bot.entity().clearActiveItem();
            bot.setItem(EquipmentSlot.HAND, s.oldMain);
            bot.stand();
        }
        activeBotByPlayer.remove(s.player);
    }

    /**
     * Flecha do arco puxado. No mod a elevação era uma fórmula fixa ({@code 0,24·d + 0,0032·d²}) que
     * passava 1,5 a 2,5 blocos acima da cabeça; aqui o ângulo vem da física da flecha e o jogador é
     * antecipado pelo tempo de voo.
     */
    private static void shootArrow(Bot bot, Player player, Vec3 playerVelocity) {
        Mannequin e = bot.entity();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 from = new Vec3(e.getX(), e.getEyeLocation().getY() - 0.1, e.getZ());
        Vec3 point = new Vec3(player.getX(), player.getY() + player.getHeight() * 0.55, player.getZ());
        double dist = point.horizontalDistance(from);
        double spread = Math.min(0.22, 0.02 + dist * 0.005) * bot.settings().projInaccuracy.value();
        point = point.add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread * 0.35, (r.nextDouble() - 0.5) * spread);
        Vec3 aim = Ballistics.aimArrow(from, point, playerVelocity, 1.8);
        Vector vel = shootVector(aim.toVector(), 1.8f, 0.25f * (float) bot.settings().projInaccuracy.value());
        Arrow arrow = e.launchProjectile(Arrow.class, vel);
        dev.manhuntstar.debug.AimDebug.track(arrow, bot.name(), player);
        arrow.setShooter(e);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1f, 1f / (r.nextFloat() * 0.4f + 0.8f));
    }

    /** O {@code shoot} do jogo: normaliza, soma ruído gaussiano * 0,0075 * imprecisão e escala. */
    public static Vector shootVector(Vector dir, float speed, float inaccuracy) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector v = dir.clone().normalize();
        v.add(new Vector(r.nextGaussian() * 0.0075 * inaccuracy, r.nextGaussian() * 0.0075 * inaccuracy, r.nextGaussian() * 0.0075 * inaccuracy));
        return v.multiply(speed);
    }

    // ------------------------------------------------------------------ besta com fogos

    private void startFirework(Player player, Bot bot, long now) {
        prepare(bot);
        activeBotByPlayer.put(player.getUniqueId(), bot.id());
        fireworks.put(bot.id(), new FireworkShot(bot.id(), player.getUniqueId(), bot.getItem(EquipmentSlot.HAND).clone(),
                bot.getItem(EquipmentSlot.OFF_HAND).clone(), now));
    }

    private boolean stepFirework(FireworkShot s, Bot bot, long now) {
        Player p = Bukkit.getPlayer(s.player);
        if (!validContext(bot, p)) {
            cleanupFirework(s, bot);
            return false;
        }
        bot.lookAt(Vec3.of(p.getEyeLocation()));
        bot.motor().stopWalk();
        s.aim.observe(p);
        if (now < s.nextAction) {
            return true;
        }
        Mannequin e = bot.entity();
        switch (s.step) {
            case 0 -> {
                bot.stand();
                bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.CROSSBOW));
                bot.setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.FIREWORK_ROCKET));
                e.startUsingItem(EquipmentSlot.HAND);
                e.getWorld().playSound(e.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 1f, 1f);
                s.step = 1;
                s.nextAction = now + 25;
            }
            case 1 -> {
                e.clearActiveItem();
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
            bot.entity().clearActiveItem();
            bot.setItem(EquipmentSlot.HAND, s.oldMain);
            bot.setItem(EquipmentSlot.OFF_HAND, s.oldOff);
            bot.stand();
        }
        activeBotByPlayer.remove(s.player);
    }

    /** Fogo disparado de besta voa reto (sem gravidade), então a mira é direta, com antecipação. */
    private static void shootFirework(Bot bot, Player player, Vec3 playerVelocity) {
        Mannequin e = bot.entity();
        Vec3 from = bot.eyePos().add(0, -0.1, 0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 point = new Vec3(player.getX(), player.getY() + player.getHeight() * 0.55, player.getZ());
        double dist = point.horizontalDistance(from);
        double inacc = 0.04 + dist * 0.006;
        point = point.add((r.nextDouble() - 0.5) * inacc, (r.nextDouble() - 0.5) * inacc, (r.nextDouble() - 0.5) * inacc);
        Vec3 aim = Ballistics.aim(from, point, playerVelocity, 1.65, 0, 1);
        Vector vel = shootVector(aim.toVector(), 1.65f, 0.65f);
        Location at = from.toLocation(e.getWorld());
        e.getWorld().spawn(at, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.RED, Color.ORANGE).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
            fw.setShooter(e);
            fw.setShotAtAngle(true);
            fw.setTicksToDetonate(45);
            fw.setVelocity(vel);
        });
        e.getWorld().playSound(e.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1f, 1f / (r.nextFloat() * 0.4f + 0.8f));
    }

    // ------------------------------------------------------------------ emboscada de TNT

    private boolean startTntAmbush(Player player, Bot bot, long now, boolean ignoreCooldown) {
        World w = player.getWorld();
        Pos stand = findAmbushStart(w, player);
        if (stand == null) {
            return false;
        }
        if (!ignoreCooldown) {
            long until = now + 2400;
            playerCooldown.put(player.getUniqueId(), until);
            botCooldown.put(bot.id(), until);
        }
        prepare(bot);
        activeBotByPlayer.put(player.getUniqueId(), bot.id());
        manager.teleportBot(bot, new Location(w, stand.x() + 0.5, stand.y() + 0.1, stand.z() + 0.5));
        bot.lookAt(Vec3.of(player.getEyeLocation()));
        bot.setSneaking(true);
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.TNT));
        tnt.put(bot.id(), new TntAmbush(bot.id(), player.getUniqueId(), stand, now));
        return true;
    }

    /** @return false quando a emboscada terminou (e já foi limpa). */
    private boolean stepTnt(TntAmbush a, Bot bot, long now) {
        Player p = Bukkit.getPlayer(a.player);
        if (!tntStillValid(a, bot, p) || now - a.started > 600) {
            cancelTnt(a, bot);
            return false;
        }
        World w = p.getWorld();
        if (!a.placed) {
            bot.lookAt(Vec3.of(p.getEyeLocation()));
            bot.setSneaking(true);
            Vec3 dir = Vec3.of(p.getLocation()).subtract(bot.pos()).horizontal();
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
                tntPos.block(w).setType(Material.TNT);
                // se a emboscada acabar antes de acender (bot morreu, jogador fugiu), a TNT some
                manager.temporaryBlocks().add(w, tntPos, Material.TNT, 40);
                w.playSound(tntPos.toLocation(w), Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1f, 1f);
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
            if (a.tntPos == null || a.tntPos.block(w).getType() != Material.TNT) {
                cancelTnt(a, bot);
                return false;
            }
            manager.temporaryBlocks().remove(w, a.tntPos);
            a.tntPos.block(w).setType(Material.AIR);
            Location at = new Location(w, a.tntPos.x() + 0.5, a.tntPos.y(), a.tntPos.z() + 0.5);
            w.spawn(at, TNTPrimed.class, t -> {
                t.setFuseTicks(40);
                t.setSource(bot.entity());
            });
            w.playSound(at, Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1f, 1f);
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.FLINT_AND_STEEL));
            bot.swing();
            a.ignited = true;
            a.ignitedAt = now;
            return true;
        }
        Vec3 away = bot.pos().subtract(Vec3.of(p.getLocation())).horizontal();
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

    private boolean tntStillValid(TntAmbush a, Bot bot, Player p) {
        if (bot == null || p == null || !bot.isAlive() || p.isDead() || !bot.world().equals(p.getWorld())) {
            return false;
        }
        Pos pp = Pos.of(p.getLocation());
        if (!CaveAmbush.isInCave(p.getWorld(), pp) || !CaveAmbush.isSameCave(p.getWorld(), pp, a.stand)) {
            return false;
        }
        return a.tntPos == null || CaveAmbush.isSameCave(p.getWorld(), pp, a.tntPos);
    }

    private void cancelTnt(TntAmbush a, Bot bot) {
        if (bot != null && bot.isAlive()) {
            bot.motor().setSprinting(false);
            bot.stand();
            bot.inventory().restorePreferredMainHand();
        }
        activeBotByPlayer.remove(a.player);
    }

    private static Pos findAmbushStart(World w, Player player) {
        Vec3 look = Vec3.of(player.getLocation().getDirection()).horizontal();
        look = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z(), 0, look.x());
        Vec3 pp = Vec3.of(player.getLocation());
        Pos playerPos = Pos.of(player.getLocation());
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

    private static boolean validCaveStand(World w, Pos playerPos, Pos pos) {
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

    private static Pos findTntPos(World w, Player player, Pos botPos) {
        Vec3 flat = Vec3.of(player.getLocation()).subtract(Vec3.center(botPos)).horizontal();
        BlockFace forward = Pos.faceFromYaw(flat.yaw());
        Pos center = Pos.of(player.getLocation());
        Pos[] candidates = {center, center.relative(forward), center.relative(forward.getOppositeFace()),
                center.offset(0, 0, -1), center.offset(0, 0, 1), center.offset(1, 0, 0), center.offset(-1, 0, 0)};
        for (Pos c : candidates) {
            if (WorldUtil.canBeReplaced(w, c) && WorldUtil.blocksMotion(w, c.below()) && !WorldUtil.isLava(w, c) && !WorldUtil.isLava(w, c.below())
                    && CaveAmbush.isSameCave(w, center, c) && botPos.distSqr(c) <= 16.0) {
                return c;
            }
        }
        return null;
    }

    private static boolean validContext(Bot bot, Player p) {
        return bot != null && p != null && bot.isAlive() && !p.isDead() && bot.world().equals(p.getWorld());
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
