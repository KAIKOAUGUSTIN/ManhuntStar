package dev.manhuntstar.listener;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.bot.MobTargeting;
import dev.manhuntstar.paper.Kinds;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.GameMob;
import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Puts core's "nearest visible bot" target goal ({@link MobTargeting}) on the hostile mobs, with Paper's mob goals. */
public final class MobTargetGoals implements Listener {

    private final ManhuntStarPlugin plugin;
    private final MobTargeting targeting;
    private final GoalKey<Mob> key;

    public MobTargetGoals(ManhuntStarPlugin plugin, MobTargeting targeting) {
        this.plugin = plugin;
        this.targeting = targeting;
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "alvo_bots"));
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            install(mob);
        }
    }

    /** Installs the goal on the mobs already loaded (when the plugin starts). */
    public void installAll() {
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (Mob m : w.getEntitiesByClass(Mob.class)) {
                install(m);
            }
        }
    }

    private void install(Mob mob) {
        if (!targeting.wantsGoal(Kinds.of(mob.getType()))) {
            return;
        }
        var goals = plugin.getServer().getMobGoals();
        if (!goals.hasGoal(mob, key)) {
            goals.addGoal(mob, 2, new TargetBotGoal(mob));
        }
    }

    /** Paper's goal around core's. */
    private final class TargetBotGoal implements Goal<Mob> {

        private final MobTargeting.TargetBotGoal goal;

        TargetBotGoal(Mob mob) {
            this.goal = targeting.goal(PaperEntities.of(mob));
        }

        @Override
        public boolean shouldActivate() {
            return goal.shouldActivate();
        }

        @Override
        public boolean shouldStayActive() {
            return goal.shouldStayActive();
        }

        @Override
        public void start() {
            goal.start();
        }

        @Override
        public void stop() {
            goal.stop();
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
