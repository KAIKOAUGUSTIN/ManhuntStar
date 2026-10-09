package dev.manhuntstar.mansave;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.util.ChunkTickets;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Protetor "Mansave" do mod (lá era inacessível: o comando não estava registrado). Invulnerável,
 * segue o jogador mais próximo e cuida dele do jeito exagerado do mod: cura e apaga fogo com poções,
 * remove cacto/fogo/lava em volta, segura quedas com teia ou pérola, luta contra mobs que o miram,
 * voa de elytra quando longe, e às vezes o "protege" demais (puxa com vara, prende em blocos,
 * joga lentidão).
 */
public final class MansaveBot {

    private final ManhuntStarPlugin plugin;
    private final Mannequin entity;
    private final Set<Long> tickets = new HashSet<>();
    private World ticketWorld;
    private final Queue<Pos> buildQueue = new LinkedList<>();
    private Material buildMaterial = Material.DIRT;
    private int healTimer;
    private int pearlCooldown;
    private int fireworkCooldown;
    private int potionTimer;
    private int trapTimer;
    private int rodTimer;
    private int stuckTimer;
    private int age;
    private Vec3 lastPos = Vec3.ZERO;
    private Vector velocity = new Vector();
    private ItemStack savedChest = ItemStack.empty();

    MansaveBot(ManhuntStarPlugin plugin, Location loc, String name, NamespacedKey key) {
        this.plugin = plugin;
        String finalName = name.length() > 16 ? name.substring(0, 16) : name;
        UUID profileId = UUID.nameUUIDFromBytes(("Mansave:" + finalName).getBytes());
        this.entity = loc.getWorld().spawn(loc, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setDescription(null);
            m.setProfile(ResolvableProfile.resolvableProfile().name(finalName.replaceAll("[^A-Za-z0-9_]", "")).uuid(profileId).build());
            m.customName(Component.text(finalName));
            m.setCustomNameVisible(true);
            m.setInvulnerable(true);
            m.setRemoveWhenFarAway(false);
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "mansave");
            m.getEquipment().setItem(EquipmentSlot.HEAD, new ItemStack(Material.GOLDEN_HELMET), true);
            m.getEquipment().setItem(EquipmentSlot.CHEST, new ItemStack(Material.GOLDEN_CHESTPLATE), true);
            m.getEquipment().setItem(EquipmentSlot.HAND, new ItemStack(Material.NETHERITE_SWORD), true);
            m.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 1, false, false));
        });
    }

    public Mannequin entity() {
        return entity;
    }

    public boolean isValid() {
        return entity.isValid() && !entity.isDead();
    }

    public void remove() {
        releaseTickets();
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private Vec3 pos() {
        return Vec3.of(entity.getLocation());
    }

    private World world() {
        return entity.getWorld();
    }

    private void setHand(ItemStack s) {
        entity.getEquipment().setItem(EquipmentSlot.HAND, s, true);
    }

    private Material hand() {
        return entity.getEquipment().getItemInMainHand().getType();
    }

    private void lookAt(Vec3 target) {
        Vec3 d = target.subtract(pos().add(0, entity.getEyeHeight(), 0));
        entity.setRotation(d.yaw(), d.pitch());
        entity.setBodyYaw(d.yaw());
    }

    // ------------------------------------------------------------------ tick

    /** Caçada pausada: parado (a pausa o segura), só continua ativo e com os chunks. */
    void tickPaused() {
        age++;
        if (age % 20 == 1) {
            updateTickets();
        }
        dev.manhuntstar.bot.Bot.keepActive(entity);
    }

    void tick() {
        age++;
        if (age % 20 == 1) {
            updateTickets();
        }
        dev.manhuntstar.bot.Bot.keepActive(entity);
        entity.setHealth(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        velocity = entity.getVelocity();
        if (healTimer > 0) {
            healTimer--;
        }
        if (pearlCooldown > 0) {
            pearlCooldown--;
        }
        if (fireworkCooldown > 0) {
            fireworkCooldown--;
        }
        if (potionTimer > 0) {
            potionTimer--;
        }
        if (trapTimer > 0) {
            trapTimer--;
        }
        if (rodTimer > 0) {
            rodTimer--;
        }
        Player target = nearestPlayer();
        if (target == null) {
            apply();
            return;
        }
        if (pos().distanceToSqr(lastPos) < 0.01 && velocity.lengthSquared() > 1.0E-4) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
        }
        lastPos = pos();
        if (stuckTimer > 20) {
            unstuck();
        }
        fallProtection(target);
        healing(target);
        fireProtection(target);
        hazardRemoval(target);
        potions(target);
        fishingRod(target);
        trapping(target);
        processBuildQueue();
        if (!combat(target)) {
            Material h = hand();
            if (buildQueue.isEmpty() && h != Material.NETHERITE_SWORD && h != Material.NETHERITE_PICKAXE && h != Material.NETHERITE_SHOVEL) {
                setHand(new ItemStack(Material.NETHERITE_SWORD));
            }
            elytra(target);
            if (entity.isGliding()) {
                if (buildQueue.isEmpty()) {
                    lookAt(Vec3.of(target.getEyeLocation()));
                }
            } else if (pos().distanceTo(Vec3.of(target.getLocation())) > 4.0) {
                moveToward(Vec3.of(target.getLocation()));
            } else {
                velocity.setX(0).setZ(0);
                if (buildQueue.isEmpty()) {
                    lookAt(Vec3.of(target.getEyeLocation()));
                }
            }
            separation();
        }
        apply();
    }

    private void apply() {
        if (!Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())) {
            velocity = new Vector();
        }
        entity.setVelocity(velocity);
    }

    private Player nearestPlayer() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double d = p.getLocation().distanceSquared(entity.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ movimento

    private void moveToward(Vec3 target) {
        Vec3 dir = target.subtract(pos()).horizontal().normalize();
        if (buildQueue.isEmpty()) {
            lookAt(target.add(0, 1.5, 0));
        }
        double accel = entity.isOnGround() ? 0.1 * 1.3 : 0.026;
        velocity.add(new Vector(dir.x() * accel, 0, dir.z() * accel));
        if (entity.isInWater() || entity.isInLava()) {
            velocity.setY(velocity.getY() + 0.04);
        }
        if (!entity.isOnGround()) {
            return;
        }
        Pos p = Pos.of(entity.getLocation());
        if (WorldUtil.blocksMotion(world(), p.above(2))) {
            return;
        }
        Pos ahead = p.relative(Pos.faceFromYaw(entity.getLocation().getYaw()));
        if (!WorldUtil.blocksMotion(world(), ahead.above()) || !WorldUtil.blocksMotion(world(), ahead)) {
            velocity.setY(0.42);
            velocity.add(new Vector(dir.x() * 0.2, 0, dir.z() * 0.2));
        }
    }

    private void separation() {
        Vector sep = new Vector();
        for (MansaveBot o : plugin.mansave().protectors()) {
            if (o == this || !o.isValid() || !o.world().equals(world())) {
                continue;
            }
            double d2 = o.entity.getLocation().distanceSquared(entity.getLocation());
            if (d2 < 4.0 && d2 > 1.0E-6) {
                Vector push = entity.getLocation().toVector().subtract(o.entity.getLocation().toVector()).normalize();
                sep.add(push.multiply((1.0 - Math.sqrt(d2) / 2.0) * 0.15));
            }
        }
        velocity.add(sep.setY(0));
    }

    private void unstuck() {
        Vec3 fwd = Vec3.fromRotation(entity.getLocation().getYaw(), 0);
        Pos eye = pos().add(0, entity.getEyeHeight(), 0).add(fwd).toPos();
        boolean mined = mineIfSolid(eye) | mineIfSolid(eye.below());
        if (mined) {
            entity.swingMainHand();
            stuckTimer = 0;
        }
    }

    private boolean mineIfSolid(Pos p) {
        Block b = p.block(world());
        if (b.getType().isAir() || b.getType().getHardness() < 0) {
            return false;
        }
        ItemStack tool = new ItemStack(Tag.MINEABLE_SHOVEL.isTagged(b.getType()) ? Material.NETHERITE_SHOVEL : Material.NETHERITE_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        setHand(tool);
        b.breakNaturally(tool, true);
        return true;
    }

    // ------------------------------------------------------------------ cuidados com o jogador

    private void fallProtection(Player p) {
        if (p.getVelocity().getY() >= -0.7 || ((org.bukkit.entity.Entity) p).isOnGround()) {
            return;
        }
        RayTraceResult hit = world().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), 50, org.bukkit.FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            return;
        }
        Pos landing = Pos.of(hit.getHitBlock()).above();
        if (pos().distanceToSqr(Vec3.center(landing)) < 25.0) {
            if (WorldUtil.canBeReplaced(world(), landing)) {
                landing.block(world()).setType(Material.COBWEB);
                entity.swingMainHand();
            }
        } else if (pearlCooldown <= 0) {
            Vec3 dir = Vec3.center(landing).subtract(pos().add(0, entity.getEyeHeight(), 0)).normalize();
            EnderPearl pearl = entity.launchProjectile(EnderPearl.class, new Vector(dir.x(), dir.y() + 0.2, dir.z()).normalize().multiply(1.5));
            pearl.setShooter(entity);
            world().playSound(entity.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.NEUTRAL, 1f, 1f);
            pearlCooldown = 20;
        }
    }

    private void throwPotion(Player p, PotionType type) {
        ItemStack splash = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) splash.getItemMeta();
        meta.setBasePotionType(type);
        splash.setItemMeta(meta);
        Vec3 dir = Vec3.of(p.getLocation()).subtract(pos()).normalize();
        ThrownPotion potion = entity.launchProjectile(ThrownPotion.class, new Vector(dir.x(), dir.y() + 0.2, dir.z()).normalize().multiply(1.0));
        potion.setItem(splash);
        potion.setShooter(entity);
        setHand(splash);
        entity.swingMainHand();
    }

    private void healing(Player p) {
        if (healTimer > 0 || p.getHealth() >= p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() || p.getFireTicks() > 0) {
            return;
        }
        throwPotion(p, PotionType.STRONG_HEALING);
        healTimer = 10;
    }

    private void fireProtection(Player p) {
        if (p.getFireTicks() <= 0 || healTimer > 0) {
            return;
        }
        throwPotion(p, PotionType.WATER);
        healTimer = 20;
    }

    private void hazardRemoval(Player p) {
        if (age % 5 != 0) {
            return;
        }
        boolean nether = world().getEnvironment() == World.Environment.NETHER;
        for (Pos center : new Pos[]{Pos.of(entity.getLocation()), Pos.of(p.getLocation())}) {
            for (int x = -3; x <= 3; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Pos q = center.offset(x, y, z);
                        Block b = q.block(world());
                        Material m = b.getType();
                        if (m == Material.CACTUS || m == Material.FIRE || m == Material.SOUL_FIRE) {
                            b.setType(Material.AIR);
                            entity.swingMainHand();
                        } else if (!nether && m == Material.LAVA && WorldUtil.canBeReplaced(world(), q.above())) {
                            q.above().block(world()).setType(Material.WATER);
                            entity.swingMainHand();
                        }
                    }
                }
            }
        }
    }

    private void potions(Player p) {
        if (potionTimer > 0) {
            return;
        }
        PotionType type = null;
        if (!p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            type = PotionType.SLOW_FALLING;
        } else if (!p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            type = PotionType.SLOWNESS;
        } else if (!p.hasPotionEffect(PotionEffectType.REGENERATION)) {
            type = PotionType.STRONG_REGENERATION;
        }
        if (type != null) {
            throwPotion(p, type);
            potionTimer = 60;
        }
    }

    private void fishingRod(Player p) {
        if (rodTimer > 0 || pos().distanceTo(Vec3.of(p.getLocation())) < 6.0) {
            return;
        }
        setHand(new ItemStack(Material.FISHING_ROD));
        entity.swingMainHand();
        world().playSound(entity.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.NEUTRAL, 1f, 1f);
        Vector pull = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).add(new Vector(0, 0.4, 0));
        p.setVelocity(p.getVelocity().add(pull));
        rodTimer = 100 + ThreadLocalRandom.current().nextInt(100);
    }

    private void trapping(Player p) {
        if (!buildQueue.isEmpty() || trapTimer > 0 || pos().distanceTo(Vec3.of(p.getLocation())) > 10.0) {
            return;
        }
        Pos pp = Pos.of(p.getLocation());
        Pos below = pp.below();
        if (pp.y() <= world().getMinHeight() || pp.y() >= world().getMaxHeight() - 3 || WorldUtil.isAir(world(), below)) {
            return;
        }
        switch (ThreadLocalRandom.current().nextInt(6)) {
            case 0 -> replaceBlock(below, Material.SOUL_SAND);
            case 1 -> placeBlock(pp, Material.COBWEB);
            case 2 -> placeBlock(pp, Material.WATER);
            case 3 -> {
                buildMaterial = Material.DIRT;
                buildQueue.add(pp.offset(0, 0, -1));
                buildQueue.add(pp.offset(0, 0, 1));
                buildQueue.add(pp.offset(1, 0, 0));
                buildQueue.add(pp.offset(-1, 0, 0));
                buildQueue.add(pp.offset(0, 1, -1));
                buildQueue.add(pp.offset(0, 1, 1));
                buildQueue.add(pp.offset(1, 1, 0));
                buildQueue.add(pp.offset(-1, 1, 0));
                buildQueue.add(pp.above(2));
            }
            case 4 -> replaceBlock(below, Material.SLIME_BLOCK);
            default -> replaceBlock(below, Material.HAY_BLOCK);
        }
        trapTimer = 60 + ThreadLocalRandom.current().nextInt(60);
    }

    private void processBuildQueue() {
        Pos next = buildQueue.poll();
        if (next == null) {
            return;
        }
        if (hand() != buildMaterial) {
            setHand(new ItemStack(buildMaterial));
        }
        lookAt(Vec3.center(next));
        placeBlock(next, buildMaterial);
    }

    private void replaceBlock(Pos p, Material m) {
        Block b = p.block(world());
        if (b.getType().getHardness() >= 0) {
            b.setType(m);
            entity.swingMainHand();
        }
    }

    private void placeBlock(Pos p, Material m) {
        if (WorldUtil.canBeReplaced(world(), p)) {
            p.block(world()).setType(m);
            entity.swingMainHand();
        }
    }

    private boolean combat(Player p) {
        Mob target = null;
        double closest = 400.0;
        for (Mob mob : world().getNearbyEntitiesByType(Mob.class, entity.getLocation(), 20.0)) {
            if (mob.isDead() || !(mob instanceof Enemy) || plugin.botManager().isBotEntity(mob)) {
                continue;
            }
            double d = mob.getLocation().distanceSquared(p.getLocation());
            if ((mob.getTarget() == p || d < 144.0) && d < closest) {
                target = mob;
                closest = d;
            }
        }
        if (target == null) {
            return false;
        }
        if (buildQueue.isEmpty() && hand() != Material.NETHERITE_SWORD) {
            setHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        if (entity.getLocation().distance(target.getLocation()) > 2.5) {
            moveToward(Vec3.of(target.getLocation()));
        } else {
            velocity.setX(0).setZ(0);
            if (buildQueue.isEmpty()) {
                lookAt(Vec3.of(target.getEyeLocation()));
            }
            if (age % 10 == 0) {
                entity.swingMainHand();
                target.damage(8.0, DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(entity).withDirectEntity(entity).build());
            }
        }
        separation();
        return true;
    }

    private void elytra(Player p) {
        double dist = entity.getLocation().distance(p.getLocation());
        boolean far = dist > 25.0;
        if (far && entity.isOnGround()) {
            velocity.setY(0.42);
        }
        boolean shouldFly = (!entity.isOnGround() && !entity.isInWater() && velocity.getY() < -0.3 && entity.getFallDistance() > 2.0)
                || (far && !entity.isOnGround() && !entity.isInWater());
        if (shouldFly && !entity.isGliding()) {
            savedChest = entity.getEquipment().getChestplate() == null ? ItemStack.empty() : entity.getEquipment().getChestplate().clone();
            entity.getEquipment().setItem(EquipmentSlot.CHEST, new ItemStack(Material.ELYTRA), true);
            entity.setGliding(true);
        } else if (entity.isOnGround() && entity.isGliding()) {
            entity.setGliding(false);
            entity.getEquipment().setItem(EquipmentSlot.CHEST, savedChest.isEmpty() ? new ItemStack(Material.GOLDEN_CHESTPLATE) : savedChest, true);
        }
        if (!entity.isGliding()) {
            return;
        }
        boolean needsAltitude = p.getY() >= entity.getY() - 5.0;
        boolean fallingFast = velocity.getY() < -0.6;
        boolean losingSpeed = Math.hypot(velocity.getX(), velocity.getZ()) < 0.4;
        if (fireworkCooldown <= 0 && far && (needsAltitude || fallingFast || losingSpeed)) {
            entity.swingMainHand();
            Vector look = entity.getLocation().getDirection();
            velocity.add(new Vector(look.getX() * 0.7, look.getY() * 0.5 + 0.3, look.getZ() * 0.7));
            world().playSound(entity.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 3f, 1f);
            fireworkCooldown = 60 + ThreadLocalRandom.current().nextInt(40);
        } else if (far) {
            Location l = entity.getLocation();
            if (!needsAltitude && velocity.getY() > -0.2) {
                entity.setRotation(l.getYaw(), Math.min(90f, l.getPitch() + 2f));
            } else if (velocity.getY() < -0.6) {
                entity.setRotation(l.getYaw(), Math.max(-90f, l.getPitch() - 2f));
            }
        }
    }

    // ------------------------------------------------------------------ chunks

    private void updateTickets() {
        World w = world();
        int cx = entity.getLocation().getBlockX() >> 4;
        int cz = entity.getLocation().getBlockZ() >> 4;
        if (ticketWorld != null && !ticketWorld.equals(w)) {
            releaseTickets();
        }
        ticketWorld = w;
        Set<Long> wanted = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                wanted.add(ChunkTickets.key(cx + dx, cz + dz));
            }
        }
        ChunkTickets shared = plugin.chunkTickets();
        tickets.removeIf(k -> {
            if (!wanted.contains(k)) {
                shared.release(w, k);
                return true;
            }
            return false;
        });
        for (long k : wanted) {
            if (tickets.add(k)) {
                shared.acquire(w, k);
            }
        }
    }

    private void releaseTickets() {
        if (ticketWorld != null) {
            ChunkTickets shared = plugin.chunkTickets();
            for (long k : tickets) {
                shared.release(ticketWorld, k);
            }
        }
        tickets.clear();
        ticketWorld = null;
    }
}
