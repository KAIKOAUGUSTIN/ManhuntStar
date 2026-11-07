package dev.manhuntstar.bot;

import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.platform.Attr;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.DamageKind;
import dev.manhuntstar.platform.EntityKind;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameLiving;
import dev.manhuntstar.platform.GameMob;
import dev.manhuntstar.platform.GameSlime;
import dev.manhuntstar.platform.GameWarden;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.Sounds;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hostile mobs attack the bots as they would attack players. In the mod the bots were players; with the
 * Mannequin this is recreated: each hostile mob gets a "nearest visible bot" target goal (like the
 * game's goal for players); mobs with a brain (piglin, hoglin, warden...) get the target
 * periodically; slimes and magma cubes deal contact damage (in the game that only applies to
 * players). The game's rules are respected: piglins ignore whoever wears gold; endermen and zombified
 * piglins only fight back; spiders only attack in the dark.
 *
 * <p>The platform puts a {@link TargetBotGoal} among the target goals of each mob {@link #wantsGoal} accepts
 * and calls {@link #tick} every tick.
 */
public final class MobTargeting {

    private static final EquipSlot[] ARMOR = {EquipSlot.FEET, EquipSlot.LEGS, EquipSlot.CHEST, EquipSlot.HEAD};

    private final ManhuntHost host;

    public MobTargeting(ManhuntHost host) {
        this.host = host;
    }

    private boolean enabledFor(GameMob mob) {
        return host.settings().mobsAttackBots.on() && !host.settings().excludedMobs().contains(mob.kind());
    }

    private static boolean isHostile(EntityKind e) {
        return e.is(EntityTrait.ENEMY) && !e.is(EntityTrait.ENDER_DRAGON) && !e.is(EntityTrait.ENDERMAN)
                && !e.is(EntityTrait.ZOMBIFIED_PIGLIN) && !e.is(EntityTrait.MANNEQUIN);
    }

    private static boolean isBrainMob(EntityKind mob) {
        return mob.is(EntityTrait.PIGLIN_FAMILY) || mob.is(EntityTrait.HOGLIN) || mob.is(EntityTrait.ZOGLIN)
                || mob.is(EntityTrait.WARDEN) || mob.is(EntityTrait.BREEZE) || mob.is(EntityTrait.CREAKING);
    }

    /** Whether mobs of this type get the target goal (hostile mobs whose AI isn't a brain). */
    public boolean wantsGoal(EntityKind mob) {
        return isHostile(mob) && !isBrainMob(mob);
    }

    /** The target goal for one mob. */
    public TargetBotGoal goal(GameMob mob) {
        return new TargetBotGoal(mob);
    }

    /** Target for mobs with a brain and contact damage from slimes (called every tick). */
    public void tick(int now) {
        if (!host.settings().mobsAttackBots.on()) {
            return;
        }
        for (Bot bot : host.botManager().aliveBots()) {
            BotBody me = bot.entity();
            if (bot.tickDelay(20)) {
                for (GameEntity e : me.world().nearbyEntities(me.position(), 24.0, 24.0, 24.0)) {
                    if (!(e instanceof GameMob mob) || !isHostile(mob.kind()) || !isBrainMob(mob.kind()) || !enabledFor(mob)) {
                        continue;
                    }
                    brainTarget(mob, bot);
                }
            }
            if ((now & 1) == 0) {
                slimeContact(me);
            }
        }
    }

    private void brainTarget(GameMob mob, Bot bot) {
        BotBody e = bot.entity();
        if (mob instanceof GameWarden warden) {
            if (warden.position().distanceToSqr(e.position()) < 16 * 16) {
                warden.increaseAnger(e, 20);
            }
            return;
        }
        if (mob.is(EntityTrait.PIGLIN) && wearsGold(e)) {
            return;
        }
        GameLiving current = mob.target();
        if (current != null && !current.isDead() && current.world() == mob.world()
                && current.position().distanceToSqr(mob.position()) < e.position().distanceToSqr(mob.position())) {
            return;
        }
        if (mob.hasLineOfSight(e) && mob.position().distanceToSqr(e.position()) <= followRange(mob) * followRange(mob)) {
            mob.setTarget(e);
        }
    }

    private void slimeContact(BotBody me) {
        for (GameEntity e : me.world().nearbyEntities(me.position(), 3.0, 3.0, 3.0)) {
            if (!(e instanceof GameSlime slime) || slime.isDead() || !enabledFor(slime)) {
                continue;
            }
            boolean deals = slime.is(EntityTrait.MAGMA_CUBE) || slime.size() > 1;
            if (!deals || !slime.boundingBox().expand(0.2).overlaps(me.boundingBox()) || !slime.hasLineOfSight(me)) {
                continue;
            }
            if (me.noDamageTicks() > me.maximumNoDamageTicks() / 2) {
                continue;
            }
            double dmg = slime.attribute(Attr.ATTACK_DAMAGE, slime.size());
            me.damage(dmg, DamageKind.MOB_ATTACK, slime, slime);
            slime.world().playSound(slime.position(), Sounds.ENTITY_SLIME_ATTACK, SoundCategory.HOSTILE, 1f,
                    (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.2f + 1f);
        }
    }

    private static boolean wearsGold(GameLiving e) {
        for (EquipSlot slot : ARMOR) {
            GameItem s = e.item(slot);
            if (s == null) {
                continue;
            }
            ItemType t = s.type();
            if (t == Items.GOLDEN_HELMET || t == Items.GOLDEN_CHESTPLATE || t == Items.GOLDEN_LEGGINGS || t == Items.GOLDEN_BOOTS) {
                return true;
            }
        }
        return false;
    }

    private static double followRange(GameMob mob) {
        return mob.attribute(Attr.FOLLOW_RANGE, 16.0);
    }

    /**
     * "Nearest visible bot" (the game's NearestAttackableTargetGoal, for bots). The platform's goal calls
     * these methods with the same names as the game's.
     */
    public final class TargetBotGoal {

        private final GameMob mob;
        private BotBody target;

        TargetBotGoal(GameMob mob) {
            this.mob = mob;
        }

        public boolean shouldActivate() {
            if (!enabledFor(mob) || ThreadLocalRandom.current().nextInt(10) != 0) {
                return false;
            }
            GameLiving current = mob.target();
            if (current != null && !current.isDead()) {
                return false;
            }
            if (mob.is(EntityTrait.SPIDER) && mob.world().block(mob.position().toPos()).lightLevel() > 7 && mob.world().isDayTime()) {
                return false;
            }
            target = findNearestBot();
            return target != null;
        }

        public boolean shouldStayActive() {
            if (target == null || !target.isValid() || target.isDead() || !enabledFor(mob)) {
                return false;
            }
            if (mob.target() != target) {
                return false;
            }
            double range = followRange(mob);
            return target.world() == mob.world() && target.position().distanceToSqr(mob.position()) <= range * range;
        }

        public void start() {
            mob.setTarget(target);
        }

        public void stop() {
            if (mob.target() == target) {
                mob.setTarget(null);
            }
            target = null;
        }

        private BotBody findNearestBot() {
            double range = followRange(mob);
            BotBody best = null;
            double bestDist = range * range;
            for (GameEntity e : mob.world().nearbyEntities(mob.position(), range, range, range)) {
                if (!(e instanceof BotBody m)) {
                    continue;
                }
                Bot bot = host.botManager().byEntity(m);
                if (bot == null || m.isDead()) {
                    continue;
                }
                double d = m.position().distanceToSqr(mob.position());
                if (d < bestDist && mob.hasLineOfSight(m)) {
                    bestDist = d;
                    best = m;
                }
            }
            return best;
        }
    }
}
