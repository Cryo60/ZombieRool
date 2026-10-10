package me.cryo.zombierool.hotfix;

import me.cryo.zombierool.player.PlayerDownManager;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/** Compare navigable routes rather than selecting a player through a ceiling. */
public final class ReachablePlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
    private int nextCheck;
    private Path selectedPath;
    public ReachablePlayerTargetGoal(Mob mob,Class<Player> type,boolean mustSee,boolean mustReach) {
        super(mob,type,mustSee,false);
    }
    @Override protected void findTarget() {
        double range=getFollowDistance(),best=Double.POSITIVE_INFINITY;
        target=null;selectedPath=null;
        for(Player player:mob.level().players()) {
            if(!player.isAlive()||player.isCreative()||player.isSpectator()||PlayerDownManager.isPlayerDown(player.getUUID())||BonusManager.isZombieBloodActive(player)||mob.distanceToSqr(player)>range*range||!mob.canAttack(player))continue;
            Path path=mob.getNavigation().createPath(player,0);
            if(!reachable(path,player))continue;
            double cost=0;
            for(int i=1;i<path.getNodeCount();i++)cost+=path.getNode(i-1).distanceTo(path.getNode(i));
            if(player==mob.getTarget())cost*=.9; // Avoid switching for tiny route differences.
            if(cost<best){best=cost;target=player;selectedPath=path;}
        }
    }
    private static boolean reachable(Path path,Player player) {
        if(path==null||!path.canReach()||path.getEndNode()==null)return false;
        var end=path.getEndNode();double dx=end.x+.5-player.getX(),dz=end.z+.5-player.getZ();
        return Math.abs(end.y-player.getY())<=1.25 && dx*dx+dz*dz<=2.25;
    }
    /** Called by CustomMobMixin at the final target assignment, after monkey handling. */
    public static void chooseAndSet(Mob mob, LivingEntity original) {
        if (!(mob instanceof AbstractZombieRoolEntity) || original != null && !(original instanceof Player)) {
            mob.setTarget(original);
            return;
        }
        LivingEntity current = mob.getTarget();
        boolean valid = current instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator()
            && !PlayerDownManager.isPlayerDown(p.getUUID()) && !BonusManager.isZombieBloodActive(p);
        long next = mob.getPersistentData().getLong("zr_reachable_next_check");
        if (mob.tickCount < next && (valid || current == null)) return;
        mob.getPersistentData().putLong("zr_reachable_next_check", mob.tickCount + 20);
        var selection = new ReachablePlayerTargetGoal(mob, Player.class, false, false);
        selection.findTarget();
        mob.setTarget(selection.target);
        if(selection.selectedPath!=null) mob.getNavigation().moveTo(selection.selectedPath,1.2);
        else mob.getNavigation().stop();
    }
    @Override public void start(){super.start();nextCheck=mob.tickCount+20;}
    @Override public boolean canContinueToUse() {
        if(!super.canContinueToUse())return false;
        if(mob.tickCount<nextCheck)return true;
        nextCheck=mob.tickCount+20;findTarget();
        if(target==null)return false;
        if(target!=mob.getTarget()){mob.setTarget(target);mob.getNavigation().stop();}
        return true;
    }
}
