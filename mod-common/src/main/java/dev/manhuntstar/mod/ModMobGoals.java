package dev.manhuntstar.mod;

import dev.manhuntstar.bot.MobTargeting;
import dev.manhuntstar.mod.mixin.MobAccessor;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Puts core's "nearest visible bot" target goal ({@link MobTargeting}) on the hostile mobs, as a vanilla target goal. */
public final class ModMobGoals {

    /** The goal's priority among the mob's target goals (the plugin's too). */
    private static final int PRIORITY = 2;

    private final MobTargeting targeting;

    public ModMobGoals(MobTargeting targeting) {
        this.targeting = targeting;
    }

    /** Core's goal as a vanilla one. */
    private static final class TargetBotGoal extends Goal {
        private final MobTargeting.TargetBotGoal goal;

        TargetBotGoal(MobTargeting.TargetBotGoal goal) {
            this.goal = goal;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return goal.shouldActivate();
        }

        @Override
        public boolean canContinueToUse() {
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
    }

    /** A mob entered a world. */
    void added(Entity entity) {
        if (entity instanceof Mob mob) {
            install(mob);
        }
    }

    /** Installs the goal on the mobs already loaded (when the mod starts). */
    void installAll() {
        for (ServerLevel level : ModServer.get().vanilla().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                added(e);
            }
        }
    }

    private void install(Mob mob) {
        if (!targeting.wantsGoal(ModTypes.of(mob.getType()))) {
            return;
        }
        GoalSelector selector = ((MobAccessor) mob).manhuntstar$targetSelector();
        for (WrappedGoal g : selector.getAvailableGoals()) {
            if (g.getGoal() instanceof TargetBotGoal) {
                return;
            }
        }
        selector.addGoal(PRIORITY, new TargetBotGoal(targeting.goal((dev.manhuntstar.platform.GameMob) ModEntities.of(mob))));
    }
}
