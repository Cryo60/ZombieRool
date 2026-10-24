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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class ReachablePlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
    private static final java.util.Map<Mob,Integer> NEXT_CHECK=new java.util.WeakHashMap<>();
    private record Failure(net.minecraft.world.phys.Vec3 position,int retry,long terrain){}
    private static final java.util.Map<net.minecraft.world.level.Level,Long> TERRAIN=new java.util.WeakHashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void terrainChanged(net.minecraftforge.event.level.BlockEvent.NeighborNotifyEvent event){
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level){TERRAIN.merge(level,1L,Long::sum);me.cryo.zombierool.gameplay.NavigationArea.invalidate(level);}
    }
    private static final java.util.Map<Mob,java.util.Map<java.util.UUID,Failure>> FAILED=new java.util.WeakHashMap<>();
    private static final java.util.Map<net.minecraft.world.level.Level,SelectionBudget> BUDGETS=new java.util.WeakHashMap<>();
    private static final class SelectionBudget {long tick=Long.MIN_VALUE,nanos;int used;}
    private record Selection(long tick,long terrain,java.lang.ref.WeakReference<Player> target,Path path){}
    private static final java.util.Map<Mob,Selection> SELECTIONS=new java.util.WeakHashMap<>();
    private boolean selectionDeferred;
    private static boolean acquire(Mob mob){
        var budget=BUDGETS.computeIfAbsent(mob.level(),key->new SelectionBudget());long tick=mob.level().getGameTime();
        if(budget.tick!=tick){budget.tick=tick;budget.used=0;budget.nanos=0;}
        if(budget.used>=4||budget.nanos>=6_000_000)return false;
        budget.used++;return true;
    }
    private static int nextReview(Mob mob){return mob.tickCount+1+Math.floorMod(mob.getId()-(int)(mob.level().getGameTime()+1),20);}
    private int nextCheck;
    private Path selectedPath;
    public ReachablePlayerTargetGoal(Mob mob,Class<Player> type,boolean mustSee,boolean mustReach) {
        super(mob,type,mustSee,false);
    }
    private static boolean factionTarget(Mob mob){return mob.getTarget()!=null&&me.cryo.zombierool.gameplay.MapActorBehavior.enemies(mob,mob.getTarget())&&me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(mob,mob.getTarget());}
    @Override public boolean canUse(){return !factionTarget(mob)&&super.canUse();}
    @Override public void stop(){var current=factionTarget(mob)?mob.getTarget():null;super.stop();if(current!=null)mob.setTarget(current);}
    @Override protected void findTarget() {
        if(me.cryo.zombierool.gameplay.PortableEquipment.pursuingDevice(mob)||me.cryo.zombierool.gameplay.WonderWeaponEffects.lockedDecoy(mob)!=null||me.cryo.zombierool.gameplay.EquipmentEffects.active(mob)||me.cryo.zombierool.gameplay.WonderWeaponEffects.frozen(mob)){target=null;selectedPath=null;return;}
        selectionDeferred=false;
        var previousSelection=SELECTIONS.get(mob);
        if(previousSelection!=null&&previousSelection.tick==mob.level().getGameTime()&&previousSelection.terrain==TERRAIN.getOrDefault(mob.level(),0L)){
            target=previousSelection.target.get();selectedPath=previousSelection.path;return;
        }
        // A live route in a one-player match already answers the targeting question.
        if(mob.level().players().size()==1&&valid(mob.getTarget())&&mob.canAttack(mob.getTarget())){
            var current=(Player)mob.getTarget();var route=mob.getNavigation().getPath();
            if(route!=null&&!route.isDone()&&reachable(route,current)&&me.cryo.zombierool.gameplay.NavigationArea.allowsPursuit(current)){target=current;selectedPath=route;return;}
        }
        if(!acquire(mob)){
            selectionDeferred=true;target=valid(mob.getTarget())&&me.cryo.zombierool.gameplay.NavigationArea.allowsPursuit((Player)mob.getTarget())?(Player)mob.getTarget():null;selectedPath=null;return;
        }
        long started=System.nanoTime();
        try{
        double range=getFollowDistance(),best=Double.POSITIVE_INFINITY;
        target=null;selectedPath=null;
        // Ground navigation cannot recalculate during a small fall down a stair. Keep pursuing.
        if(!canRepath(mob)&&valid(mob.getTarget())){target=(Player)mob.getTarget();return;}
        for(Player player:mob.level().players()) {
            if(!player.isAlive()||player.isCreative()||player.isSpectator()||PlayerDownManager.isPlayerDown(player.getUUID())||BonusManager.isZombieBloodActive(player)||mob.distanceToSqr(player)>range*range||!mob.canAttack(player))continue;
            var failed=FAILED.computeIfAbsent(mob,key->new java.util.HashMap<>());
            if(!me.cryo.zombierool.gameplay.NavigationArea.allowsPursuit(player)){failed.remove(player.getUUID());continue;}
            var previous=failed.get(player.getUUID());
            if(previous!=null&&mob.tickCount<previous.retry&&previous.terrain==TERRAIN.getOrDefault(mob.level(),0L)&&player.position().distanceToSqr(previous.position)<4)continue;
            Path path=player==mob.getTarget()?mob.getNavigation().getPath():null;
            if(path==null||path.isDone()||!reachable(path,player))path=mob.getNavigation().createPath(player,0);
            if(mob.getNavigation() instanceof me.cryo.zombierool.gameplay.RouteNavigation nav&&nav.planningDeferred()){
                selectionDeferred=true;
                if(player==mob.getTarget()&&valid(player)){target=player;selectedPath=null;return;}
                continue;
            }
            if(me.cryo.zombierool.gameplay.SubmergedMelee.canReach(mob,player)){target=player;selectedPath=null;return;}
            if(!reachable(path,player)){failed.put(player.getUUID(),new Failure(player.position(),mob.tickCount+40+Math.floorMod(mob.getId(),20),TERRAIN.getOrDefault(mob.level(),0L)));continue;}
            failed.remove(player.getUUID());
            double cost=0;
            for(int i=1;i<path.getNodeCount();i++){
                var route=me.cryo.zombierool.block.system.TraversalSystem.edge(mob,path.getNode(i-1));
                cost+=route!=null&&route.destination().equals(path.getNode(i).asBlockPos())?me.cryo.zombierool.gameplay.RouteNavigation.traversalCost(mob,route):path.getNode(i-1).distanceTo(path.getNode(i));
            }
            if(player==mob.getTarget())cost*=.9; // Avoid switching for tiny route differences.
            if(cost<best){best=cost;target=player;selectedPath=path;}
        }
        }finally{
            BUDGETS.get(mob.level()).nanos+=System.nanoTime()-started;
            SELECTIONS.put(mob,new Selection(mob.level().getGameTime(),TERRAIN.getOrDefault(mob.level(),0L),new java.lang.ref.WeakReference<>(target instanceof Player p?p:null),selectedPath));
        }
    }
    private static boolean valid(LivingEntity entity){return entity instanceof Player p&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!PlayerDownManager.isPlayerDown(p.getUUID())&&!BonusManager.isZombieBloodActive(p);}
    private static boolean canRepath(Mob mob){return mob.onGround()||mob.isInWater()||mob.isInLava()||mob.isPassenger();}
    public static void invalidate(Mob mob){NEXT_CHECK.remove(mob);FAILED.remove(mob);SELECTIONS.remove(mob);}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getAmount()>0 && !event.getEntity().level().isClientSide
                && event.getEntity() instanceof AbstractZombieRoolEntity zombie
                && event.getSource().getEntity() instanceof Player) {
            // A shot can knock the mob onto a different step or column. Reassess failures
            // using its new position instead of keeping a pre-impact failed route.
            invalidate(zombie);
        }
    }
    private static boolean reachable(Path path,Player player) {
        return me.cryo.zombierool.gameplay.RouteNavigation.reachesPlayer(path,player);
    }
    /** Called by CustomMobMixin at the final target assignment, after monkey handling. */
    public static void chooseAndSet(Mob mob, LivingEntity original) {
        if(me.cryo.zombierool.gameplay.PortableEquipment.pursuingDevice(mob)||me.cryo.zombierool.gameplay.EquipmentEffects.active(mob)||me.cryo.zombierool.gameplay.WonderWeaponEffects.frozen(mob))return;
        if(factionTarget(mob))return;
        if (!(mob instanceof AbstractZombieRoolEntity) || original != null && !(original instanceof Player)) {
            mob.setTarget(original);
            return;
        }
        if(mob.getPersistentData().getBoolean("zr_boss_link")||mob.getPersistentData().getBoolean("zr_boss_machine"))return;
        if(mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity special&&special.encounter.isFleeing())return;
        LivingEntity current = mob.getTarget();
        boolean valid = valid(current);
        if(valid&&!canRepath(mob))return;
        int next = NEXT_CHECK.getOrDefault(mob,Integer.MIN_VALUE);
        if (mob.tickCount < next && (valid || current == null)) {
            boolean moved=current==null&&mob.level().players().stream().anyMatch(player->{
                var failure=FAILED.getOrDefault(mob,java.util.Map.of()).get(player.getUUID());
                return failure!=null&&(player.position().distanceToSqr(failure.position)>=4||failure.terrain!=TERRAIN.getOrDefault(mob.level(),0L));
            });
            if(!moved)return;
        }
        var selection = new ReachablePlayerTargetGoal(mob, Player.class, false, false);
        selection.findTarget();
        if(selection.selectionDeferred){if(!valid&&current instanceof Player)mob.setTarget(null);return;}
        NEXT_CHECK.put(mob,nextReview(mob));
        mob.setTarget(selection.target);
        if(selection.selectedPath!=null&&selection.selectedPath!=mob.getNavigation().getPath()) mob.getNavigation().moveTo(selection.selectedPath,1.2);
        else if(selection.target==null){
            mob.getNavigation().stop();mob.setZza(0);mob.setXxa(0);mob.setSpeed(0);
            var velocity=mob.getDeltaMovement();mob.setDeltaMovement(0,velocity.y,0);
        }
    }
    @Override public void start(){super.start();nextCheck=nextReview(mob);if(selectedPath!=null)mob.getNavigation().moveTo(selectedPath,1.2);}
    @Override public boolean canContinueToUse() {
        if(factionTarget(mob))return false;
        if(me.cryo.zombierool.gameplay.EquipmentEffects.active(mob)||me.cryo.zombierool.gameplay.WonderWeaponEffects.frozen(mob))return false;
        if(mob.getTarget()!=null&&!mob.canAttack(mob.getTarget()))return false;
        if(!super.canContinueToUse())return false;
        if(mob.tickCount<nextCheck)return true;
        if(valid(mob.getTarget())&&!canRepath(mob))return true;
        nextCheck=nextReview(mob);findTarget();
        if(target==null)return false;
        if(target!=mob.getTarget()){mob.setTarget(target);if(selectedPath!=null)mob.getNavigation().moveTo(selectedPath,1.2);}
        return true;
    }
}
