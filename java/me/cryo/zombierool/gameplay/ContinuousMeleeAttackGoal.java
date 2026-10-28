package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** A deferred path search must not put a valid chase on vanilla's 20-tick start cooldown. */
public class ContinuousMeleeAttackGoal extends MeleeAttackGoal {
    private final double chaseSpeed;

    public ContinuousMeleeAttackGoal(PathfinderMob mob, double speed, boolean remember) {
        super(mob, speed, remember);
        chaseSpeed = speed;
    }

    @Override public boolean canUse() {
        var target = mob.getTarget();
        return target != null && target.isAlive() && mob.canAttack(target)
                && mob.isWithinRestriction(target.blockPosition())
                && !EquipmentEffects.active(mob) && !WonderWeaponEffects.frozen(mob)
                && !TraversalMotion.active(mob);
    }

    @Override public void start() {
        super.start();
        var target = mob.getTarget();
        if (target != null) mob.getNavigation().moveTo(target, chaseSpeed);
    }

    @Override public void tick() {
        // Retain vanilla attack timing, reach overrides, look control and animation.
        super.tick();
        var target = mob.getTarget();
        if (target != null && mob.getNavigation().isDone()
                && mob.distanceToSqr(target) > getAttackReachSqr(target)
                && !(mob.getNavigation() instanceof RouteNavigation route && route.directPursuit())) {
            mob.getNavigation().moveTo(target, chaseSpeed);
        }
    }
}
