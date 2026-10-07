package dev.manhuntstar.listener;

import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.Bot;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zoglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Mobs hostis atacam os bots como atacariam jogadores. No mod os bots eram jogadores; com o
 * Mannequin isso é recriado: cada mob hostil ganha um objetivo de alvo "bot mais próximo à vista"
 * (como o objetivo do jogo para jogadores); mobs com cérebro (piglin, hoglin, warden...) recebem o
 * alvo periodicamente; slimes e cubos de magma dão dano de contato (no jogo isso só vale para
 * jogadores). Regras do jogo respeitadas: piglin ignora quem usa ouro; enderman e piglin zumbi só
 * revidam; aranha só ataca no escuro.
 */
public final class MobTargeting implements Listener {

    private final ManhuntStarPlugin plugin;
    private final GoalKey<Mob> key;

    public MobTargeting(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "alvo_bots"));
    }

    private boolean enabledFor(Mob mob) {
        return plugin.settings().mobsAttackBots.on() && !plugin.settings().excludedMobs().contains(mob.getType());
    }

    private static boolean isHostile(Entity e) {
        return e instanceof Enemy && !(e instanceof EnderDragon) && !(e instanceof Enderman) && !(e instanceof PigZombie) && !(e instanceof Mannequin);
    }

    private static boolean isBrainMob(Mob mob) {
        return mob instanceof PiglinAbstract || mob instanceof Hoglin || mob instanceof Zoglin || mob instanceof Warden
                || mob instanceof Breeze || mob instanceof Creaking;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            install(mob);
        }
    }

    /** Instala o objetivo nos mobs já carregados (ao ligar o plugin). */
    public void installAll() {
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (Mob m : w.getEntitiesByClass(Mob.class)) {
                install(m);
            }
        }
    }

    private void install(Mob mob) {
        if (!isHostile(mob) || isBrainMob(mob)) {
            return;
        }
        var goals = plugin.getServer().getMobGoals();
        if (!goals.hasGoal(mob, key)) {
            goals.addGoal(mob, 2, new TargetBotGoal(mob));
        }
    }

    /** Alvo para mobs com cérebro e dano de contato de slimes (chamado a cada tick). */
    public void tick(int now) {
        if (!plugin.settings().mobsAttackBots.on()) {
            return;
        }
        for (Bot bot : plugin.botManager().aliveBots()) {
            Mannequin me = bot.entity();
            if (bot.tickDelay(20)) {
                for (Mob mob : me.getWorld().getNearbyEntitiesByType(Mob.class, me.getLocation(), 24.0)) {
                    if (!isHostile(mob) || !isBrainMob(mob) || !enabledFor(mob)) {
                        continue;
                    }
                    brainTarget(mob, bot);
                }
            }
            if ((now & 1) == 0) {
                slimeContact(bot, me);
            }
        }
    }

    private void brainTarget(Mob mob, Bot bot) {
        Mannequin e = bot.entity();
        if (mob instanceof Warden warden) {
            if (warden.getLocation().distanceSquared(e.getLocation()) < 16 * 16) {
                warden.increaseAnger(e, 20);
            }
            return;
        }
        if (mob instanceof Piglin && wearsGold(e)) {
            return;
        }
        LivingEntity current = mob.getTarget();
        if (current != null && !current.isDead() && current.getWorld().equals(mob.getWorld())
                && current.getLocation().distanceSquared(mob.getLocation()) < e.getLocation().distanceSquared(mob.getLocation())) {
            return;
        }
        if (mob.hasLineOfSight(e) && mob.getLocation().distanceSquared(e.getLocation()) <= followRange(mob) * followRange(mob)) {
            mob.setTarget(e);
        }
    }

    private void slimeContact(Bot bot, Mannequin me) {
        for (Slime slime : me.getWorld().getNearbyEntitiesByType(Slime.class, me.getLocation(), 3.0)) {
            if (slime.isDead() || !enabledFor(slime)) {
                continue;
            }
            boolean deals = slime instanceof MagmaCube || slime.getSize() > 1;
            if (!deals || !slime.getBoundingBox().expand(0.2).overlaps(me.getBoundingBox()) || !slime.hasLineOfSight(me)) {
                continue;
            }
            if (me.getNoDamageTicks() > me.getMaximumNoDamageTicks() / 2) {
                continue;
            }
            AttributeInstance atk = slime.getAttribute(Attribute.ATTACK_DAMAGE);
            double dmg = atk == null ? slime.getSize() : atk.getValue();
            me.damage(dmg, DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(slime).withDirectEntity(slime).build());
            slime.getWorld().playSound(slime.getLocation(), Sound.ENTITY_SLIME_ATTACK, SoundCategory.HOSTILE, 1f,
                    (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 0.2f + 1f);
        }
    }

    private static boolean wearsGold(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return false;
        }
        for (ItemStack s : eq.getArmorContents()) {
            if (s != null && (s.getType() == Material.GOLDEN_HELMET || s.getType() == Material.GOLDEN_CHESTPLATE
                    || s.getType() == Material.GOLDEN_LEGGINGS || s.getType() == Material.GOLDEN_BOOTS)) {
                return true;
            }
        }
        return false;
    }

    private static double followRange(Mob mob) {
        AttributeInstance a = mob.getAttribute(Attribute.FOLLOW_RANGE);
        return a == null ? 16.0 : a.getValue();
    }

    /** "Bot mais próximo à vista" (o NearestAttackableTargetGoal do jogo, para bots). */
    private final class TargetBotGoal implements Goal<Mob> {

        private final Mob mob;
        private Mannequin target;

        TargetBotGoal(Mob mob) {
            this.mob = mob;
        }

        @Override
        public boolean shouldActivate() {
            if (!enabledFor(mob) || ThreadLocalRandom.current().nextInt(10) != 0) {
                return false;
            }
            LivingEntity current = mob.getTarget();
            if (current != null && !current.isDead()) {
                return false;
            }
            if (mob instanceof Spider && mob.getLocation().getBlock().getLightLevel() > 7 && mob.getWorld().isDayTime()) {
                return false;
            }
            target = findNearestBot();
            return target != null;
        }

        @Override
        public boolean shouldStayActive() {
            if (target == null || !target.isValid() || target.isDead() || !enabledFor(mob)) {
                return false;
            }
            if (mob.getTarget() != target) {
                return false;
            }
            double range = followRange(mob);
            return target.getWorld().equals(mob.getWorld()) && target.getLocation().distanceSquared(mob.getLocation()) <= range * range;
        }

        @Override
        public void start() {
            mob.setTarget(target);
        }

        @Override
        public void stop() {
            if (mob.getTarget() == target) {
                mob.setTarget(null);
            }
            target = null;
        }

        private Mannequin findNearestBot() {
            double range = followRange(mob);
            Mannequin best = null;
            double bestDist = range * range;
            for (Mannequin m : mob.getWorld().getNearbyEntitiesByType(Mannequin.class, mob.getLocation(), range)) {
                Bot bot = plugin.botManager().byEntity(m);
                if (bot == null || m.isDead()) {
                    continue;
                }
                double d = m.getLocation().distanceSquared(mob.getLocation());
                if (d < bestDist && mob.hasLineOfSight(m)) {
                    bestDist = d;
                    best = m;
                }
            }
            return best;
        }

        @Override
        public GoalKey<Mob> getKey() {
            return key;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.TARGET);
        }
    }
}
