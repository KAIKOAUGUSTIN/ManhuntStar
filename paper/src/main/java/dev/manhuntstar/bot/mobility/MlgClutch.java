package dev.manhuntstar.bot.mobility;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.ai.Navigator;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.util.Locations;
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
 * MLG: falling fast (≥ 0.6 blocks/tick) within 4.5 blocks of the ground, the bot places water (or a boat,
 * always in the Nether) under itself and picks it up afterwards. Also cleans up boats the bot itself placed.
 */
public final class MlgClutch {

    /** Margin over the planned fall height before it counts as a slip. */
    private static final float PLANNED_FALL_SLACK = 0.5f;

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
            // in another world the bot can't reach it: the temporary block registry picks it up
            if (w.equals(bot.world()) && Locations.block(waterPos, w).getType() == Material.WATER) {
                bot.manager().temporaryBlocks().remove(w, waterPos);
                Locations.block(waterPos, w).setType(Material.AIR);
                w.playSound(Locations.location(waterPos, w), Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1f, 1f);
                bot.manager().plugin().pathService().invalidate(PaperWorld.of(w), waterPos);
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

    /**
     * The bot died or left the world: gets rid of the boats it placed. The next life starts with a new
     * registry, so without this the boat stayed in the world forever.
     */
    public void discardBoats() {
        for (UUID id : spawnedBoats) {
            if (Bukkit.getEntity(id) instanceof Boat b && b.isValid()) {
                b.remove();
            }
        }
        spawnedBoats.clear();
    }

    /** Dismounts and breaks the boat like a player punching it (no drop). */
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
        // The path plans falls of up to 3 blocks (5 when digging the floor to drop into a gap). Falling more than the
        // planned fall means the bot slipped (edge of a ravine): while it falls the path doesn't advance and
        // kept saying "planned fall", and it died without a bucket.
        int plannedFall = nav.plannedFallHeight();
        boolean planned = plannedFall > 0 && bot.entity() != null && bot.entity().getFallDistance() <= plannedFall + PLANNED_FALL_SLACK;
        if (planned || bot.isInWater() || bot.isInLava()) {
            return;
        }
        if (bot.velocity().y() > -0.6) {
            return;
        }
        World w = bot.world();
        Vec3 p = bot.pos();
        RayTraceResult r = w.rayTraceBlocks(Locations.location(p, w), new Vector(0, -1, 0), 10, FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            return;
        }
        Pos ground = Locations.pos(r.getHitBlock());
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
            Locations.block(water, w).setType(Material.WATER);
            w.playSound(Locations.location(water, w), Sound.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 1f, 1f);
            bot.swing();
            bot.motor().resetFallTracking();
            bot.manager().plugin().pathService().invalidate(PaperWorld.of(w), water);
            // if the bot dies before picking it up, the water disappears all the same
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
