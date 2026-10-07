package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * MLG: caindo rápido (≥ 0,6 bloco/tick) a até 4,5 blocos do chão, o bot coloca água (ou um barco,
 * sempre no Nether) embaixo de si e recolhe depois. Também limpa barcos que o próprio bot colocou.
 */
public final class MlgClutch {

    private final Bot bot;
    private int cooldown;
    private boolean boatClutching;
    private int boatTicks;
    private Pos waterPos;
    private World waterWorld;
    private int waterTicks;
    private final Set<UUID> spawnedBoats = new HashSet<>();

    public MlgClutch(Bot bot) {
        this.bot = bot;
    }

    public void trackSpawnedBoat(Boat boat) {
        spawnedBoats.add(boat.getUniqueId());
    }

    public boolean isOwnBoat(Entity e) {
        return e != null && spawnedBoats.contains(e.getUniqueId());
    }

    public void tick(Navigator nav) {
        tickBoatClutch();
        if (bot.tickDelay(20)) {
            cleanupBoats();
        }
        if (cooldown > 0) {
            cooldown--;
        }
        handleMlg(nav);
        if (waterPos != null && ++waterTicks > 15) {
            World w = waterWorld;
            // em outro mundo o bot não alcança: o registro de blocos temporários recolhe
            if (w.equals(bot.world()) && waterPos.block(w).getType() == Material.WATER) {
                bot.manager().temporaryBlocks().remove(w, waterPos);
                waterPos.block(w).setType(Material.AIR);
                w.playSound(waterPos.toLocation(w), Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1f, 1f);
                bot.manager().plugin().pathService().invalidate(w, waterPos);
            }
            waterPos = null;
            waterTicks = 0;
            bot.inventory().restorePreferredMainHand();
        }
    }

    private void tickBoatClutch() {
        if (!boatClutching) {
            return;
        }
        Entity vehicle = bot.vehicle();
        boatTicks++;
        if (boatTicks > 40 || vehicle == null || !vehicle.isValid() || !(vehicle instanceof Boat)) {
            if (vehicle instanceof Boat b && b.isValid() && boatTicks > 40) {
                breakBoat(b);
                cooldown = 20;
            }
            boatClutching = false;
            boatTicks = 0;
            bot.inventory().restorePreferredMainHand();
            return;
        }
        if ((bot.onGround() || Math.abs(vehicle.getVelocity().getY()) < 0.05) && boatTicks > 5) {
            breakBoat((Boat) vehicle);
            boatClutching = false;
            boatTicks = 0;
            bot.inventory().restorePreferredMainHand();
        }
    }

    private void cleanupBoats() {
        World w = bot.world();
        if (w == null || spawnedBoats.isEmpty()) {
            return;
        }
        Iterator<UUID> it = spawnedBoats.iterator();
        while (it.hasNext()) {
            Entity e = Bukkit.getEntity(it.next());
            if (!(e instanceof Boat b) || !b.isValid()) {
                it.remove();
                continue;
            }
            boolean riddenByMe = b.getPassengers().contains(bot.entity());
            if (riddenByMe && bot.brain().boat().isRiderBoat(b)) {
                continue;
            }
            if (b.getPassengers().isEmpty() || (b.isOnGround() && !riddenByMe) || b.getTicksLived() > 60 && !riddenByMe) {
                b.remove();
                it.remove();
            }
        }
    }

    /** Desmonta e quebra o barco como o jogador quebrando com socos (sem drop). */
    public void breakBoat(Boat b) {
        if (b.getPassengers().contains(bot.entity())) {
            b.removePassenger(bot.entity());
        }
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8f, 1f);
        spawnedBoats.remove(b.getUniqueId());
        b.remove();
    }

    private void handleMlg(Navigator nav) {
        if (bot.onGround() || boatClutching || cooldown > 0 || bot.isRiding() || waterPos != null) {
            return;
        }
        if (nav.isPlannedFall() || bot.isInWater() || bot.isInLava()) {
            return;
        }
        if (bot.velocity().y() > -0.6) {
            return;
        }
        World w = bot.world();
        Vec3 p = bot.pos();
        RayTraceResult r = w.rayTraceBlocks(p.toLocation(w), new Vector(0, -1, 0), 10, FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            return;
        }
        Pos ground = Pos.of(r.getHitBlock());
        double distToGround = p.y() - (ground.y() + 1.0);
        if (distToGround > 4.5 || distToGround < 0) {
            return;
        }
        doMlg(w, ground);
    }

    private void doMlg(World w, Pos ground) {
        cooldown = 40;
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
        boolean smallPatch = isSmallWaterPatch(w, ground);
        boolean useBoat = nether || (!smallPatch && ThreadLocalRandom.current().nextDouble() < 0.5);
        if (useBoat) {
            bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.OAK_BOAT));
            bot.setPitchNow(90f);
            Location loc = new Location(w, bot.pos().x(), ground.y() + 1.0, bot.pos().z(), bot.yaw(), 0);
            Entity spawned = w.spawnEntity(loc, EntityType.OAK_BOAT);
            if (spawned instanceof Boat boat) {
                trackSpawnedBoat(boat);
                bot.swing();
                boat.addPassenger(bot.entity());
                bot.motor().resetFallTracking();
                boatClutching = true;
                boatTicks = 0;
            }
            return;
        }
        bot.setItem(EquipmentSlot.HAND, new ItemStack(Material.WATER_BUCKET));
        bot.setPitchNow(90f);
        Pos water = ground.above();
        if (WorldUtil.isWater(w, water)) {
            bot.motor().resetFallTracking();
            return;
        }
        if (WorldUtil.canBeReplaced(w, water)) {
            water.block(w).setType(Material.WATER);
            w.playSound(water.toLocation(w), Sound.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 1f, 1f);
            bot.swing();
            bot.motor().resetFallTracking();
            bot.manager().plugin().pathService().invalidate(w, water);
            // se o bot morrer antes de recolher, a água some do mesmo jeito
            bot.manager().temporaryBlocks().add(w, water, Material.WATER, 15 + 40);
            waterPos = water;
            waterWorld = w;
            waterTicks = 0;
        }
    }

    private static boolean isSmallWaterPatch(World w, Pos ground) {
        Pos landing = ground.above();
        if (!WorldUtil.isWater(w, landing)) {
            return false;
        }
        return BoatRoutine.countSurfaceWater(w, landing, 2) >= 5;
    }
}
