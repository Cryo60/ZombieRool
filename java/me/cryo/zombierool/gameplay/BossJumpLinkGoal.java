package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Refresh pursuit through the shared pathfinder; never choose an isolated, greedy transport hop. */
public final class BossJumpLinkGoal extends Goal {
    private final SpecialEnemyEntity boss;
    private int nextCheck;
    private java.util.UUID pursuedPlayer;
    private net.minecraft.world.phys.Vec3 pursuedPosition;
    public BossJumpLinkGoal(SpecialEnemyEntity boss){this.boss=boss;}
    @Override public boolean canUse(){
        if(boss.getPersistentData().getBoolean("zr_boss_machine")||!boss.canPerformBossAction())return false;
        // Map spawners own acquisition (radius, visibility, behavior and retaliation).
        // Traversal may follow their target, but must never wake an idle map boss.
        Player target=boss.getTarget() instanceof Player player&&valid(player)?player:
            NativeMapActors.owns(boss)?null:boss.level().players().stream().filter(this::valid).min(java.util.Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if(target==null)return false;
        var wanted=RouteNavigation.pursuitPosition(target);
        boolean moved=!target.getUUID().equals(pursuedPlayer)||pursuedPosition==null||wanted.distanceToSqr(pursuedPosition)>4;
        if(boss.tickCount<nextCheck&&!moved)return false;
        nextCheck=boss.tickCount+10;pursuedPlayer=target.getUUID();pursuedPosition=wanted;
        if(moved&&!RouteNavigation.reachesPlayer(boss.getNavigation().getPath(),target)){
            boss.getNavigation().stop();
            boss.getMoveControl().setWantedPosition(boss.getX(),boss.getY(),boss.getZ(),0);
            me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(boss);
        }
        BossTraversalHistory.objective(boss,target);
        if(boss.getNavigation() instanceof RouteNavigation navigation&&navigation.preferNearbyWalk(target))return false;
        if(RouteNavigation.reachesPlayer(boss.getNavigation().getPath(),target))return false;
        var pursuit=boss.getNavigation().createPath(target,0);
        if(RouteNavigation.reachesPlayer(pursuit,target)){
            boss.setTarget(target);boss.getNavigation().moveTo(pursuit,1);
        }
        return false;
    }
    private boolean valid(Player player){return player.isAlive()&&!player.isCreative()&&!player.isSpectator()&&!me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(player)&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID());}
}
