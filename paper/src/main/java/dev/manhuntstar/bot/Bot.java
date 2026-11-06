package dev.manhuntstar.bot;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.manhuntstar.bot.ability.PearlAbility;
import dev.manhuntstar.bot.ai.Brain;
import dev.manhuntstar.bot.ai.IdleBehavior;
import dev.manhuntstar.bot.combat.Combat;
import dev.manhuntstar.bot.motor.Motor;
import dev.manhuntstar.bot.survival.Survival;
import dev.manhuntstar.bot.world.Mining;
import dev.manhuntstar.config.Settings;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.paper.PaperWorld;
import dev.manhuntstar.platform.BlockTrait;
import dev.manhuntstar.platform.BlockType;
import dev.manhuntstar.platform.Blocks;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameBlock;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameHorse;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Platform;
import dev.manhuntstar.platform.Pose;
import dev.manhuntstar.platform.SoundGroup;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Box;
import dev.manhuntstar.util.ChunkTickets;
import dev.manhuntstar.util.Face;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Pos;
import dev.manhuntstar.util.Vec3;
import dev.manhuntstar.util.WorldUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mannequin;

/**
 * A hunter bot. Holds the identity and the persistent state; the {@link Mannequin} entity
 * only exists while the bot is alive and is recreated on every respawn.
 */
public final class Bot {

    private static final double EYE_HEIGHT = 1.62;
    /** Oak leaves placed by the bot disappear after 100 s. */
    private static final int LEAF_LIFETIME_TICKS = 2000;

    private final BotManager manager;
    private final UUID id;
    private final String name;
    private String visualName;
    private SkinData skin;
    private final BotDifficulty difficulty;
    private String groupName;
    private UUID targetPlayer;
    private int aggression;
    private boolean shouldRespawn = true;
    private long horseRideBlockedUntil;
    private boolean terminatorTotemAvailable = true;
    private boolean terminatorTotemIssued;
    private int kills;

    private BotBody entity;
    private final Progression progression;
    private final BotInventory inventory;
    private final Hunger hunger = new Hunger();
    private Motor motor;
    private Combat combat;
    private Mining mining;
    private Brain brain;
    private Survival survival;
    private PearlAbility ability;
    private IdleBehavior idle;

    private int aliveTicks;
    private Float targetYaw;
    private Float targetPitch;
    private boolean sneaking;
    private boolean swimmingPose;
    private Vec3 lastFireSourcePos;
    private int blocksPlacedInWindow;
    private long lastBlockWindowReset;
    private final Set<Long> chunkTickets = new HashSet<>();
    private World ticketWorld;
    private Vec3 lastKnownPos;
    private String lastKnownWorld;
    private Pos pendingPillar;
    private int pendingPillarTicks;
    private Runnable pendingPillarDone;

    Bot(BotManager manager, UUID id, String name, String visualName, SkinData skin, BotDifficulty difficulty) {
        this.manager = manager;
        this.id = id;
        this.name = name;
        this.visualName = visualName == null || visualName.isBlank() ? name : visualName;
        this.skin = skin;
        this.difficulty = difficulty;
        this.progression = new Progression(this, difficulty);
        this.inventory = new BotInventory(this);
        createRuntime();
    }

    private void createRuntime() {
        motor = new Motor(this);
        combat = new Combat(this);
        mining = new Mining(this);
        brain = new Brain(this);
        survival = new Survival(this);
        ability = new PearlAbility(this);
    }

    // ------------------------------------------------------------------ identity

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String visualName() {
        return visualName;
    }

    public void setVisualName(String visualName) {
        this.visualName = visualName;
        if (entity != null) {
            PaperEntities.bukkit(entity).customName(Component.text(visualName));
        }
    }

    public SkinData skin() {
        return skin;
    }

    public void setSkin(SkinData skin) {
        this.skin = skin;
        if (entity != null) {
            PaperEntities.bukkit(entity).setProfile(buildProfile());
        }
    }

    public BotDifficulty difficulty() {
        return difficulty;
    }

    public String groupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public BotGroup group() {
        return groupName == null ? null : manager.group(groupName);
    }

    public int aggression() {
        return aggression;
    }

    public void setAggression(int aggression) {
        this.aggression = Math.max(0, Math.min(10, aggression));
    }

    public boolean shouldRespawn() {
        return shouldRespawn;
    }

    public void setShouldRespawn(boolean v) {
        this.shouldRespawn = v;
    }

    public int kills() {
        return kills;
    }

    public void incrementKills() {
        kills++;
    }

    public long horseRideBlockedUntil() {
        return horseRideBlockedUntil;
    }

    public void setHorseRideBlockedUntil(long tick) {
        horseRideBlockedUntil = Math.max(0, tick);
    }

    public boolean isHorseRidingBlocked() {
        return Bukkit.getCurrentTick() < horseRideBlockedUntil;
    }

    public void blockHorseRidingForSeconds(int seconds) {
        long until = Bukkit.getCurrentTick() + Math.max(1, seconds) * 20L;
        if (until > horseRideBlockedUntil) {
            horseRideBlockedUntil = until;
        }
    }

    public boolean terminatorTotemAvailable() {
        return terminatorTotemAvailable;
    }

    public void setTerminatorTotemAvailable(boolean v) {
        terminatorTotemAvailable = v;
    }

    void markTotemIssued() {
        terminatorTotemIssued = true;
    }

    public boolean shouldHoldTotemInOffhand() {
        return difficulty == BotDifficulty.TERMINATOR && terminatorTotemAvailable && getItem(EquipSlot.OFF_HAND).type() == Items.TOTEM_OF_UNDYING;
    }

    /** Called when the totem was used up: gives back the normal shield/offhand. */
    public void onTotemConsumed() {
        terminatorTotemAvailable = false;
        terminatorTotemIssued = false;
        if (combat.isBlocking()) {
            combat.stopBlock();
        }
        restoreDefaultOffhand();
    }

    public void restoreDefaultOffhand() {
        if (combat.isBlocking()) {
            combat.stopBlock();
        }
        if ((difficulty == BotDifficulty.TERMINATOR && terminatorTotemAvailable && !terminatorTotemIssued) || shouldHoldTotemInOffhand()) {
            terminatorTotemIssued = true;
            setItem(EquipSlot.OFF_HAND, GameItem.of(Items.TOTEM_OF_UNDYING));
            combat.setHasShield(false);
        } else if (progression.canUseShield()) {
            setItem(EquipSlot.OFF_HAND, GameItem.of(Items.SHIELD));
            combat.setHasShield(true);
        } else {
            setItem(EquipSlot.OFF_HAND, GameItem.empty());
            combat.setHasShield(false);
        }
    }

    // ------------------------------------------------------------------ subsystem access

    public BotManager manager() {
        return manager;
    }

    public Settings settings() {
        return manager.plugin().settings();
    }

    public Progression progression() {
        return progression;
    }

    public BotInventory inventory() {
        return inventory;
    }

    public Hunger hunger() {
        return hunger;
    }

    public Motor motor() {
        return motor;
    }

    public Combat combat() {
        return combat;
    }

    public Mining mining() {
        return mining;
    }

    public Brain brain() {
        return brain;
    }

    public Survival survival() {
        return survival;
    }

    public PearlAbility ability() {
        return ability;
    }

    public IdleBehavior idle() {
        return idle;
    }

    public void setIdle(IdleBehavior idle) {
        this.idle = idle;
    }

    /** A special ability (ambush, bow burst) is controlling the bot. */
    public boolean capacityLocked() {
        return manager.tactics().isControllingBot(this);
    }

    // ------------------------------------------------------------------ entity

    /** The Bukkit entity of the body, while the code still uses it. */
    public Mannequin mannequin() {
        return PaperEntities.bukkit(entity);
    }

    public BotBody entity() {
        return entity;
    }

    public boolean isAlive() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public GameWorld world() {
        return entity != null ? entity.world() : (lastKnownWorld == null ? null : Platform.server().world(lastKnownWorld));
    }

    /** The Bukkit world of {@link #world()}, while the code still uses it. */
    public World bukkitWorld() {
        return PaperWorld.bukkit(world());
    }

    public Vec3 pos() {
        return entity != null ? entity.position() : (lastKnownPos == null ? Vec3.ZERO : lastKnownPos);
    }

    public Location location() {
        return entity != null ? PaperEntities.bukkit(entity).getLocation() : (lastKnownPos == null || world() == null ? null : Locations.location(lastKnownPos, world()));
    }

    public Pos blockPos() {
        return pos().toPos();
    }

    public Vec3 eyePos() {
        return pos().add(0, eyeHeight(), 0);
    }

    public double eyeHeight() {
        if (entity == null) {
            return EYE_HEIGHT;
        }
        return entity.eyeHeight();
    }

    public double height() {
        return entity == null ? 1.8 : entity.height();
    }

    public float yaw() {
        return entity == null ? 0 : entity.yaw();
    }

    public float pitch() {
        return entity == null ? 0 : entity.pitch();
    }

    public BlockFace facing() {
        return Locations.faceFromYaw(yaw());
    }

    public Vec3 lookVector() {
        return Vec3.fromRotation(yaw(), pitch());
    }

    public Vec3 forwardVector() {
        return Vec3.fromRotation(yaw(), 0).normalize();
    }

    public Box boundingBox() {
        return entity == null ? Box.EMPTY : entity.boundingBox();
    }

    public float health() {
        return entity == null ? 0 : (float) entity.health();
    }

    public float maxHealth() {
        if (entity == null) {
            return (float) settings().botHealth.value();
        }
        return (float) entity.maxHealth();
    }

    public void setHealth(double v) {
        if (entity != null && !entity.isDead()) {
            entity.setHealth(Math.max(0, Math.min(maxHealth(), v)));
        }
    }

    public boolean onGround() {
        return motor.onGround();
    }

    public boolean horizontalCollision() {
        return motor.horizontalCollision();
    }

    public Vec3 velocity() {
        return motor.velocity();
    }

    public boolean isInWater() {
        return entity != null && (entity.isInWater() || entity.isUnderWater());
    }

    public boolean isEyeInWater() {
        return entity != null && entity.isUnderWater();
    }

    public boolean isInLava() {
        return entity != null && entity.isInLava();
    }

    public boolean isOnFire() {
        return entity != null && entity.fireTicks() > 0;
    }

    public boolean isRiding() {
        return entity != null && entity.isInsideVehicle();
    }

    public GameEntity vehicle() {
        return entity == null ? null : entity.vehicle();
    }

    public int aliveTicks() {
        return aliveTicks;
    }

    /** True once every {@code i} ticks, with a per-bot phase offset to spread heavy work. */
    public boolean tickDelay(int i) {
        return ((aliveTicks + (id.hashCode() & 0xFF)) % i) == 0;
    }

    public double distanceTo(Vec3 p) {
        return pos().distanceTo(p);
    }

    public double distanceTo(GameEntity e) {
        return pos().distanceTo(e.position());
    }

    /** Line of sight from the eyes to the point, ignoring blocks without collision. */
    public boolean canSee(Vec3 target) {
        GameWorld w = world();
        return w != null && WorldUtil.clearLine(w, eyePos(), target);
    }

    public Vec3 lastFireSourcePos() {
        return lastFireSourcePos;
    }

    public void rememberFireSource(Vec3 p) {
        if (p != null) {
            lastFireSourcePos = p;
        }
    }

    public void clearFireSourceMemory() {
        lastFireSourcePos = null;
    }

    // ------------------------------------------------------------------ target

    public UUID targetPlayerId() {
        return targetPlayer;
    }

    public void setTargetPlayer(UUID id) {
        this.targetPlayer = id;
    }

    /** The target player, if the list and the late-joiner protection still allow hunting them. */
    public GamePlayer target() {
        GamePlayer p = targetPlayer == null ? null : Platform.server().player(targetPlayer);
        if (p == null || p.uuid().equals(manager.forcedTarget()) || manager.policy().canBeHunted(p)) {
            return p;
        }
        return null;
    }

    /** Closest living player in any world (fallback when the target is gone). */
    public GamePlayer fallbackTarget() {
        GamePlayer best = null;
        double bestDist = Double.MAX_VALUE;
        GameWorld w = world();
        Vec3 p = pos();
        for (GamePlayer pl : Platform.server().onlinePlayers()) {
            if (!manager.isEligibleTarget(pl)) {
                continue;
            }
            double d = pl.world().equals(w) ? p.distanceTo(pl.position()) : 1.0E7;
            if (d < bestDist) {
                bestDist = d;
                best = pl;
            }
        }
        return best;
    }

    public boolean isWithinToweringRange(GamePlayer target, double maxAxisDistance) {
        if (target == null || target.isDead() || !target.world().equals(world())) {
            return false;
        }
        Vec3 p = pos();
        return Math.hypot(target.position().x() - p.x(), target.position().z() - p.z()) <= maxAxisDistance;
    }

    // ------------------------------------------------------------------ aim and pose

    /** Sets where the head should turn (applied at the end of the tick, with optional smoothing). */
    public void lookAt(Vec3 target) {
        Vec3 d = target.subtract(eyePos());
        targetYaw = d.yaw();
        targetPitch = d.pitch();
    }

    public void lookAtBlock(Pos p) {
        lookAt(Vec3.center(p));
    }

    public void lookDirection(Vec3 dir) {
        lookAt(eyePos().add(dir));
    }

    public boolean isLookingAtTarget(float tolerance) {
        if (targetYaw == null || targetPitch == null) {
            return true;
        }
        float dy = Math.abs(wrapDegrees(yaw() - targetYaw));
        float dp = Math.abs(pitch() - targetPitch);
        return dy <= tolerance && dp <= tolerance;
    }

    /** Changes the pitch right away (e.g. looking down to place a block). */
    public void setPitchNow(float pitch) {
        targetPitch = pitch;
        if (targetYaw == null) {
            targetYaw = yaw();
        }
        if (entity != null) {
            entity.setRotation(yaw(), pitch);
        }
    }

    public void setYawNow(float yaw) {
        targetYaw = yaw;
        if (targetPitch == null) {
            targetPitch = pitch();
        }
        if (entity != null) {
            entity.setRotation(yaw, pitch());
            entity.setBodyYaw(yaw);
        }
    }

    public void addYaw(float delta) {
        setYawNow(yaw() + delta);
    }

    private void applyAim() {
        if (entity == null || targetYaw == null || targetPitch == null) {
            return;
        }
        int smoothness = settings().aimSmoothness.value();
        float yaw;
        float pitch;
        if (smoothness <= 0) {
            yaw = targetYaw;
            pitch = targetPitch;
        } else {
            float factor = 50f / Math.max(50f, smoothness);
            yaw = yaw() + wrapDegrees(targetYaw - yaw()) * factor;
            pitch = pitch() + (targetPitch - pitch()) * factor;
        }
        pitch = Math.max(-90f, Math.min(90f, pitch));
        entity.setRotation(yaw, pitch);
        entity.setBodyYaw(yaw);
    }

    public static float wrapDegrees(float v) {
        float f = v % 360.0f;
        if (f >= 180.0f) {
            f -= 360.0f;
        }
        if (f < -180.0f) {
            f += 360.0f;
        }
        return f;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneak) {
        if (sneaking == sneak) {
            return;
        }
        sneaking = sneak;
        refreshPose();
    }

    public void setSwimmingPose(boolean swim) {
        if (swimmingPose == swim) {
            return;
        }
        swimmingPose = swim;
        refreshPose();
    }

    public boolean isSwimmingPose() {
        return swimmingPose;
    }

    private void refreshPose() {
        if (entity == null) {
            return;
        }
        if (entity.isGliding()) {
            return;
        }
        if (swimmingPose) {
            entity.setPose(Pose.SWIMMING, true);
        } else if (sneaking) {
            entity.setPose(Pose.SNEAKING, true);
        } else {
            entity.setPose(Pose.STANDING, false);
        }
    }

    public void stand() {
        motor.stopWalk();
        setSneaking(false);
        setSwimmingPose(false);
        motor.setShouldBeSwimming(false);
    }

    public void swing() {
        if (entity != null) {
            entity.swingMainHand();
        }
    }

    // ------------------------------------------------------------------ equipment

    public GameItem getItem(EquipSlot slot) {
        if (entity == null) {
            return GameItem.empty();
        }
        return entity.item(slot);
    }

    public void setItem(EquipSlot slot, GameItem stack) {
        if (entity == null) {
            return;
        }
        GameItem s = stack == null ? GameItem.empty() : stack;
        entity.setItem(slot, s, true);
        inventory.onEquipmentChanged(slot, s);
    }

    public GameItem mainHand() {
        return getItem(EquipSlot.HAND);
    }

    // ------------------------------------------------------------------ blocks

    /**
     * Places a block like a player: holds the block, looks at it, plays the sound and respects the limit
     * of 10 blocks every 10 seconds. Oak leaves are temporary and disappear after 100 s.
     */
    public boolean placeBlock(Pos pos, BlockType material, boolean allowSelfOverlap) {
        GameWorld w = world();
        if (w == null || entity == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastBlockWindowReset > 10_000L) {
            blocksPlacedInWindow = 0;
            lastBlockWindowReset = now;
        }
        if (blocksPlacedInWindow >= 10) {
            return false;
        }
        if (!WorldUtil.isLoaded(w, pos)) {
            return false;
        }
        if (material == Blocks.OAK_LEAVES) {
            if (!canPlaceLeafAt(pos)) {
                return false;
            }
        } else if (!WorldUtil.canBeReplaced(w, pos)) {
            return false;
        }
        if (wouldTrapLivingEntity(pos, allowSelfOverlap)) {
            return false;
        }
        if (getItem(EquipSlot.HAND).type() != ItemType.of(material)) {
            setItem(EquipSlot.HAND, GameItem.of(ItemType.of(material)));
        }
        lookAt(Vec3.center(pos));
        GameBlock b = w.block(pos);
        b.place(material);
        swing();
        SoundGroup sounds = b.soundGroup();
        w.playSound(Vec3.center(pos), sounds.placeSound(), dev.manhuntstar.platform.SoundCategory.BLOCKS,
                (sounds.volume() + 1.0f) / 2.0f, sounds.pitch() * 0.8f);
        blocksPlacedInWindow++;
        if (material == Blocks.OAK_LEAVES) {
            manager.temporaryBlocks().add(w, pos, Blocks.OAK_LEAVES, LEAF_LIFETIME_TICKS);
        }
        manager.plugin().pathService().invalidate(w, pos);
        return true;
    }

    public boolean placeBlock(Pos pos, BlockType material) {
        return placeBlock(pos, material, false);
    }

    public boolean canPlaceLeafAt(Pos pos) {
        GameWorld w = world();
        if (!WorldUtil.canBeReplaced(w, pos) || WorldUtil.hasFluid(w, pos)) {
            return false;
        }
        for (Pos check : new Pos[]{pos.below(), pos.relative(Face.NORTH), pos.relative(Face.SOUTH), pos.relative(Face.EAST), pos.relative(Face.WEST)}) {
            BlockType type = w.type(check.x(), check.y(), check.z());
            if (type.is(BlockTrait.AIR) || WorldUtil.hasFluid(w, check) || type.is(BlockTrait.LEAVES)) {
                continue;
            }
            if (type.is(BlockTrait.OCCLUDING)) {
                return true;
            }
        }
        return false;
    }

    private boolean wouldTrapLivingEntity(Pos pos, boolean allowSelfOverlap) {
        Box box = new Box(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1);
        if (!allowSelfOverlap && boundingBox().overlaps(box)) {
            return true;
        }
        for (GameEntity e : world().nearbyEntities(box)) {
            if (e != entity && e instanceof GameLiving le && !le.isDead()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pillar: after jumping, places the block where the feet were as soon as they pass the top of the block
     * (as the player does). The Mannequin isn't pushed out of blocks, so placing it earlier
     * would trap the bot inside it.
     */
    public void queuePillarBlock(Pos feetBlock, Runnable onDone) {
        pendingPillar = feetBlock;
        pendingPillarTicks = 0;
        pendingPillarDone = onDone;
    }

    public boolean hasPendingPillar() {
        return pendingPillar != null;
    }

    private void tickPendingPillar() {
        if (pendingPillar == null) {
            return;
        }
        pendingPillarTicks++;
        Pos target = pendingPillar;
        boolean placed = false;
        if (pos().y() >= target.y() + 1.0 && WorldUtil.canBeReplaced(world(), target)) {
            placed = placeBlock(target, Blocks.COBBLESTONE, false);
        }
        if (placed || pendingPillarTicks > 12 || (pendingPillarTicks > 2 && onGround() && pos().y() < target.y() + 0.5)) {
            Runnable done = pendingPillarDone;
            pendingPillar = null;
            pendingPillarDone = null;
            if (done != null) {
                done.run();
            }
        }
    }

    /** Opens closed doors, gates and trapdoors like a player right-clicking. */
    public boolean tryOpen(Pos pos) {
        GameWorld w = world();
        GameBlock b = w.block(pos);
        BlockType m = b.type();
        boolean openable = m.is(BlockTrait.DOOR) || m.is(BlockTrait.FENCE_GATE) || m.is(BlockTrait.TRAPDOOR);
        if (!openable || m == Blocks.IRON_DOOR || m == Blocks.IRON_TRAPDOOR) {
            return false;
        }
        if (b.isOpenable() && !b.isOpen()) {
            motor.stopWalk();
            lookAt(Vec3.center(pos));
            swing();
            b.setOpen(true);
            GameSound s = m.is(BlockTrait.DOOR) ? Sounds.BLOCK_WOODEN_DOOR_OPEN : m.is(BlockTrait.FENCE_GATE) ? Sounds.BLOCK_FENCE_GATE_OPEN : Sounds.BLOCK_WOODEN_TRAPDOOR_OPEN;
            w.playSound(Vec3.center(pos), s, dev.manhuntstar.platform.SoundCategory.BLOCKS, 1.0f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.1f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ entity life cycle

    ResolvableProfile buildProfile() {
        ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(profileName()).uuid(id);
        if (skin != null && skin.valid()) {
            b.addProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
        }
        return b.build();
    }

    /** Profile name (what shows in TAB and /list): the internal name, valid for the game. */
    public String profileName() {
        String n = name.replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) {
            n = "Bot";
        }
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    /** Creates the entity at the given location (first spawn, respawn or restore). */
    BotBody spawnEntity(Location loc) {
        Location safe = adjustOutOfLava(loc);
        Settings s = settings();
        Mannequin spawned = safe.getWorld().spawn(safe, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setDescription(null);
            m.setProfile(buildProfile());
            m.customName(Component.text(visualName));
            m.setCustomNameVisible(s.showNameTag.on());
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.setImmovable(false);
            AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(s.botHealth.value());
            }
            m.setHealth(s.botHealth.value());
            m.getPersistentDataContainer().set(manager.botKey(), org.bukkit.persistence.PersistentDataType.STRING, id.toString());
        });
        entity = PaperEntities.of(spawned);
        aliveTicks = 0;
        targetYaw = null;
        targetPitch = null;
        sneaking = false;
        swimmingPose = false;
        lastKnownWorld = entity.world().name();
        lastKnownPos = entity.position();
        motor.onRespawn();
        updateChunkTickets(true);
        manager.onEntitySpawned(this);
        return entity;
    }

    private static Location adjustOutOfLava(Location loc) {
        World w = loc.getWorld();
        GameWorld gw = PaperWorld.of(w);
        Pos p = Locations.pos(loc);
        if (WorldUtil.isLava(gw, p) || WorldUtil.isLava(gw, p.below())) {
            for (int i = 0; i < 20; i++) {
                Pos check = p.above(i);
                if (WorldUtil.isAir(gw, check) && WorldUtil.isAir(gw, check.above())) {
                    return new Location(w, loc.getX(), check.y(), loc.getZ(), loc.getYaw(), loc.getPitch());
                }
            }
            int surface = WorldUtil.highestY(gw, p.x(), p.z()) + 1;
            return new Location(w, loc.getX(), surface, loc.getZ(), loc.getYaw(), loc.getPitch());
        }
        return loc;
    }

    /** Recreates the AI subsystems (same as the "new" bot the mod created on every respawn). */
    void resetRuntime() {
        brain.navigator().reset();
        createRuntime();
        idle = null;
    }

    /** Removes the entity without a death effect. */
    void despawnEntity() {
        if (entity != null) {
            lastKnownPos = entity.position();
            lastKnownWorld = entity.world().name();
            mining.cancel();
            if (entity.isValid()) {
                entity.remove();
            }
        }
        entity = null;
        brain.mlg().discardBoats();
        releaseChunkTickets();
        manager.plugin().debugRenderer().clear(id);
    }

    void markDead() {
        if (entity != null) {
            lastKnownPos = entity.position();
            lastKnownWorld = entity.world().name();
        }
        mining.cancel();
        entity = null;
        brain.mlg().discardBoats();
        releaseChunkTickets();
        manager.plugin().debugRenderer().clear(id);
    }

    public void onEntityTeleported() {
        if (entity != null) {
            lastKnownWorld = entity.world().name();
            lastKnownPos = entity.position();
        }
        updateChunkTickets(true);
        brain.navigator().reset();
    }

    /**
     * Takes the bot to another world by recreating the entity at the destination (as the mod recreated the
     * fake player), keeping health, fire, air, effects and equipment.
     */
    void relocate(Location dest) {
        if (entity == null || !entity.isValid()) {
            spawnEntity(dest);
            return;
        }
        if (PaperWorld.bukkit(entity.world()).equals(dest.getWorld())) {
            PaperEntities.bukkit(entity).teleport(dest);
            onEntityTeleported();
            return;
        }
        double hp = entity.health();
        int fire = entity.fireTicks();
        int air = entity.remainingAir();
        var effects = new java.util.ArrayList<>(PaperEntities.bukkit(entity).getActivePotionEffects());
        Map<EquipSlot, GameItem> equipment = new java.util.EnumMap<>(EquipSlot.class);
        for (EquipSlot slot : new EquipSlot[]{EquipSlot.HAND, EquipSlot.OFF_HAND, EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET}) {
            equipment.put(slot, getItem(slot).copy());
        }
        mining.cancel();
        entity.remove();
        entity = null;
        releaseChunkTickets();
        spawnEntity(dest);
        for (Map.Entry<EquipSlot, GameItem> e : equipment.entrySet()) {
            entity.setItem(e.getKey(), e.getValue(), true);
        }
        entity.setHealth(Math.max(0.5, Math.min(maxHealth(), hp)));
        entity.setFireTicks(fire);
        entity.setRemainingAir(air);
        PaperEntities.bukkit(entity).addPotionEffects(effects);
        brain.navigator().reset();
    }

    /** Snapshot of the current state (to save to disk or to respawn). */
    BotSnapshot snapshot() {
        boolean alive = isAlive();
        Vec3 p = pos();
        java.util.List<GameItem> inv = new java.util.ArrayList<>(BotInventory.SIZE);
        for (int i = 0; i < BotInventory.SIZE; i++) {
            inv.add(inventory.get(i).copy());
        }
        long horseLeft = Math.max(0, horseRideBlockedUntil - Bukkit.getCurrentTick());
        return new BotSnapshot(id, name, visualName, skin, lastKnownWorld == null && world() != null ? world().name() : lastKnownWorld,
                p.x(), p.y(), p.z(), yaw(), pitch(), alive ? health() : maxHealth(), hunger.foodLevel(), hunger.saturation(),
                difficulty, progression.phase(), progression.progressionEnabled(), inv,
                getItem(EquipSlot.HAND).copy(), getItem(EquipSlot.OFF_HAND).copy(), getItem(EquipSlot.HEAD).copy(),
                getItem(EquipSlot.CHEST).copy(), getItem(EquipSlot.LEGS).copy(), getItem(EquipSlot.FEET).copy(),
                inventory.snapshotPersistentGear(), groupName, targetPlayer, shouldRespawn, horseLeft, terminatorTotemAvailable,
                aggression, kills, alive);
    }

    /** Applies the saved state that doesn't depend on the entity (call before {@link #spawnEntity}). */
    void applySnapshotState(BotSnapshot s) {
        groupName = s.group();
        targetPlayer = s.target();
        shouldRespawn = s.shouldRespawn();
        horseRideBlockedUntil = Bukkit.getCurrentTick() + s.horseBlockedTicksLeft();
        terminatorTotemAvailable = s.totemAvailable();
        aggression = s.aggression();
        kills = s.kills();
        hunger.set(s.food(), s.saturation());
        progression.setProgressionEnabled(s.progressionEnabled());
        progression.setPhaseSilently(s.phase());
        for (int i = 0; i < BotInventory.SIZE && i < s.inventory().size(); i++) {
            inventory.set(i, s.inventory().get(i));
        }
        inventory.applyPersistentGear(s.gear());
        lastKnownWorld = s.world();
        lastKnownPos = new Vec3(s.x(), s.y(), s.z());
    }

    /** Applies the saved equipment and health to the newly created entity. */
    void applySnapshotEquipment(BotSnapshot s) {
        if (entity == null) {
            return;
        }
        setItem(EquipSlot.HAND, s.mainHand());
        setItem(EquipSlot.OFF_HAND, s.offHand());
        setItem(EquipSlot.HEAD, s.head());
        setItem(EquipSlot.CHEST, s.chest());
        setItem(EquipSlot.LEGS, s.legs());
        setItem(EquipSlot.FEET, s.feet());
        setHealth(s.health() <= 0 ? maxHealth() : s.health());
        entity.setRotation(s.yaw(), s.pitch());
        if (s.offHand().type() == Items.SHIELD) {
            combat.setHasShield(true);
        }
    }

    public Vec3 lastKnownPos() {
        return lastKnownPos;
    }

    public String lastKnownWorld() {
        return lastKnownWorld;
    }

    /** Keeps the 3x3 chunks around the bot loaded (so it keeps acting far from players). */
    private void updateChunkTickets(boolean force) {
        if (entity == null) {
            return;
        }
        if (!force && !tickDelay(20)) {
            return;
        }
        World w = PaperWorld.bukkit(entity.world());
        int cx = PaperEntities.bukkit(entity).getLocation().getBlockX() >> 4;
        int cz = PaperEntities.bukkit(entity).getLocation().getBlockZ() >> 4;
        Set<Long> wanted = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                wanted.add(ChunkTickets.key(cx + dx, cz + dz));
            }
        }
        if (ticketWorld != null && !ticketWorld.equals(w)) {
            releaseChunkTickets();
        }
        ticketWorld = w;
        ChunkTickets tickets = manager.plugin().chunkTickets();
        Iterator<Long> it = chunkTickets.iterator();
        while (it.hasNext()) {
            long k = it.next();
            if (!wanted.contains(k)) {
                tickets.release(w, k);
                it.remove();
            }
        }
        for (long k : wanted) {
            if (chunkTickets.add(k)) {
                tickets.acquire(w, k);
            }
        }
    }

    void releaseChunkTickets() {
        if (ticketWorld != null) {
            ChunkTickets tickets = manager.plugin().chunkTickets();
            for (long k : chunkTickets) {
                tickets.release(ticketWorld, k);
            }
        }
        chunkTickets.clear();
        ticketWorld = null;
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        if (entity == null) {
            return;
        }
        aliveTicks++;
        lastKnownPos = entity.position();
        lastKnownWorld = entity.world().name();
        keepActive(entity);
        updateChunkTickets(false);
        progression.tick();
        motor.beginTick();
        tickPendingPillar();
        if (isRiding() && vehicle() instanceof GameHorse) {
            motor.stopWalk();
        }
        inventory.tick();
        survival.tick();
        if (entity == null) {
            return;
        }
        brain.tick();
        mining.tick();
        ability.tick();
        if (entity == null) {
            return;
        }
        applyAim();
        tickRegeneration();
        motor.endTick();
    }

    /** Hunt paused: the bot neither thinks nor moves (the pause holds it); it only stays active and keeps its chunks. */
    void tickPaused() {
        if (entity == null) {
            return;
        }
        keepActive(entity);
        updateChunkTickets(false);
    }

    /**
     * Paper stops updating ordinary entities more than 16 blocks from a player
     * (entity-activation-range): the bot stopped walking and stayed frozen. Players are never
     * inactive, and Paper keeps every entity with a portal cooldown active, so the bot keeps
     * a permanent cooldown (it doesn't use portals on its own; dimension changes are done by the plugin).
     */
    public static void keepActive(GameEntity e) {
        if (e.portalCooldown() < 40) {
            e.setPortalCooldown(100);
        }
    }

    private void tickRegeneration() {
        if (entity == null || entity.isDead()) {
            return;
        }
        double hp = entity.health();
        double max = maxHealth();
        // The mod's extra passive regeneration (0.5 health per second out of fire/lava and with full air).
        if (hp < max && !isInLava() && !isOnFire() && entity.remainingAir() >= entity.maximumAir()) {
            entity.setHealth(Math.min(max, hp + 0.025));
        }
        boolean naturalRegen = !Boolean.FALSE.equals(entity.world().gameRule(dev.manhuntstar.platform.GameRule.NATURAL_HEALTH_REGENERATION));
        float delta = hunger.tick((float) entity.health(), (float) max, naturalRegen, entity.world().difficulty().ordinal());
        if (delta > 0) {
            entity.setHealth(Math.min(max, entity.health() + delta));
        } else if (delta < 0) {
            PaperEntities.bukkit(entity).damage(-delta, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.STARVE).build());
        }
    }

    @Override
    public String toString() {
        return "Bot[" + name + "]";
    }
}
