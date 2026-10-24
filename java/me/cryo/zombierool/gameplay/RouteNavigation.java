package me.cryo.zombierool.gameplay;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;

/** A nearby player can require a much longer route through stairs and navigation links. */
public final class RouteNavigation extends GroundPathNavigation {
    private boolean walkingOnly;
    private int nextExtendedSearch;
    private static final java.util.Map<Level, SearchBudget> EXTENDED_BUDGET=new java.util.WeakHashMap<>();
    private static final class SearchBudget { long tick=Long.MIN_VALUE; int used; }
    private boolean acquireExtendedSearch(){
        var budget=EXTENDED_BUDGET.computeIfAbsent(level,key->new SearchBudget());
        long now=level.getGameTime();if(budget.tick!=now){budget.tick=now;budget.used=0;}
        return budget.used++<2;
    }
    private static final java.util.Map<Level,PlanningBudget> PLANNING=new java.util.WeakHashMap<>();
    private static final java.util.Map<Level,PlanningBudget> ENCOUNTER_PLANNING=new java.util.WeakHashMap<>();
    private static final java.util.Map<Level,PlanningBudget> PATROL_PLANNING=new java.util.WeakHashMap<>();
    private static final class PlanningBudget {long tick=Long.MIN_VALUE,nanos;int used;}
    private boolean planningDeferred;
    private boolean encounterPlanning;
    private boolean patrolPlanning;
    private float patrolRange;
    private PlanningBudget planningBudget(){return (patrolPlanning?PATROL_PLANNING:encounterPlanning?ENCOUNTER_PLANNING:PLANNING).computeIfAbsent(level,key->new PlanningBudget());}
    public Path createPatrolPath(Vec3 point){patrolPlanning=true;patrolRange=(float)Math.max(12,Math.min(32,mob.position().distanceTo(point)+8));try{return createPath(point.x,point.y,point.z,0);}finally{patrolPlanning=false;}}
    /** Hidden appearances have a small independent allowance so active hordes cannot starve them. */
    public Path createEncounterPath(Player player){
        encounterPlanning=true;
        try{return createPath(player,0);}finally{encounterPlanning=false;}
    }
    public boolean planningDeferred(){return planningDeferred;}
    private boolean acquirePlanning(){
        var budget=planningBudget();long tick=level.getGameTime();
        if(budget.tick!=tick){budget.tick=tick;budget.nanos=0;budget.used=0;}
        if(budget.used>=(encounterPlanning||patrolPlanning?2:4)||budget.nanos>=(patrolPlanning?2_000_000:6_000_000))return false;
        budget.used++;return true;
    }
    private RouteNavigation walkProbe;
    private float lastPlanningRange;
    private float visitBudgetMultiplier=1;
    @Override public void setMaxVisitedNodesMultiplier(float multiplier){visitBudgetMultiplier=multiplier;super.setMaxVisitedNodesMultiplier(multiplier);}
    @Override public void resetMaxVisitedNodesMultiplier(){visitBudgetMultiplier=1;super.resetMaxVisitedNodesMultiplier();}
    public float lastPlanningRange(){return lastPlanningRange;}
    public boolean walkingOnly(){return walkingOnly;}
    /** Test actual supported ground, not visibility: looking across a gap is not a walking route. */
    public boolean preferNearbyWalk(Player player){
        if(!(mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss)||!boss.isBoss()
            ||TraversalMotion.active(player)||mob.distanceToSqr(player)>144||Math.abs(mob.getY()-player.getY())>3)return false;
        if(walkProbe==null)walkProbe=new RouteNavigation(mob,level);
        Path walk;
        // An independent planner must not overwrite the active route's cached target or stuck timers.
        walkingOnly=true;
        try{walk=walkProbe.createPath(player,0);}finally{walkingOnly=false;}
        if(!reachesPlayer(walk,player))return false;
        double length=0;for(int i=1;i<walk.getNodeCount();i++)length+=walk.getNode(i-1).distanceTo(walk.getNode(i));
        if(length>12)return false;
        mob.setTarget(player);moveTo(walk,1);return true;
    }
    public RouteNavigation(Mob mob,Level level){super(mob,level);}
    @Override protected PathFinder createPathFinder(int budget){
        nodeEvaluator=new RouteNodeEvaluator();nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator,budget);
    }
    @Override protected Vec3 getTempMobPos(){return mob.isInWater()||EnemyWaterMovement.swims(mob)?mob.position():super.getTempMobPos();}
    @Override protected void followThePath(){
        boolean boss=mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity special&&special.isBoss()||mob instanceof me.cryo.zombierool.entity.MapActorEntity actor&&actor.profile().getBoolean("boss");
        if(!mob.isInWater()&&!boss){super.followThePath();return;}
        if(boss&&path.getNextNodeIndex()==0&&path.getNodeCount()>1){
            Vec3 first=path.getNextEntityPos(mob),current=getTempMobPos();
            var transport=me.cryo.zombierool.block.system.TraversalSystem.edge(mob,path.getNode(0));
            boolean boarding=transport!=null&&transport.destination().equals(path.getNode(1).asBlockPos());
            if(!boarding&&Math.abs(current.y-first.y)<.7&&current.subtract(first).multiply(1,0,1).lengthSqr()<.64)path.advance();
        }
        // Vanilla's Forge waypoint check uses (width+1)/2, while Path's movement
        // position rounds that offset. With wide crawlers those centers disagree.
        Vec3 current=getTempMobPos(),next=path.getNextEntityPos(mob);
        // Wide bosses hit the same mismatch on land: Forge compares against .975,
        // but a .95-wide mob is actually steered to the node's .5 centre.
        maxDistanceToWaypoint=boss?.35f:.6f;
        var nextBlock=BlockPos.containing(next);boolean surfacing=EnemyWaterMovement.swims(mob)&&!level.getFluidState(nextBlock).is(net.minecraft.tags.FluidTags.WATER)&&level.getFluidState(nextBlock.below()).is(net.minecraft.tags.FluidTags.WATER);
        if(Math.abs(current.x-next.x)<=maxDistanceToWaypoint&&Math.abs(current.z-next.z)<=maxDistanceToWaypoint&&(surfacing?current.y>=next.y+.02:Math.abs(current.y-next.y)<.7))path.advance();
        doStuckDetection(current);
    }
    @Override protected double getGroundY(Vec3 point){
        var pos=BlockPos.containing(point);if(level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))return point.y;
        if(EnemyWaterMovement.swims(mob)&&level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER))return point.y+.15;
        return super.getGroundY(point);
    }
    public static float traversalCost(Mob mob,me.cryo.zombierool.block.system.TraversalSystem.Route route){
        return mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&boss.isBoss()?(float)route.start().distanceTo(route.end())+4:1;
    }
    public static Vec3 pursuitPosition(Player player){
        var flight=TraversalMotion.flight(player);
        if(flight!=null)return flight.end;
        if(player.isInWater()&&!EnemyWaterMovement.enabled(player.level())){
            BlockPos bed=player.blockPosition();
            while(bed.getY()>player.level().getMinBuildHeight()&&player.level().getFluidState(bed.below()).is(net.minecraft.tags.FluidTags.WATER)
                    &&player.level().getBlockState(bed.below()).getCollisionShape(player.level(),bed.below()).isEmpty())bed=bed.below();
            return new Vec3(player.getX(),bed.getY(),player.getZ());
        }
        if(!player.onGround()&&!player.isInWater()&&!player.isInLava()){
            Vec3 feet=player.position();
            var floor=player.level().clip(new ClipContext(feet,feet.add(0,-12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(floor.getType()==HitResult.Type.BLOCK&&floor.getDirection()==net.minecraft.core.Direction.UP)
                return new Vec3(feet.x,floor.getLocation().y,feet.z);
        }
        return player.position();
    }
    public static boolean reachesPlayer(Path path,Player player){
        if(path==null||!path.canReach()||path.getEndNode()==null)return false;
        Vec3 wanted=pursuitPosition(player);var end=path.getEndNode();
        double dx=end.x+.5-wanted.x,dz=end.z+.5-wanted.z;
        return Math.abs(end.y-wanted.y)<=1.25&&dx*dx+dz*dz<=2.25;
    }
    @Override public Path createPath(Entity target,int accuracy){
        planningDeferred=false;
        if(!(target instanceof Player player))return super.createPath(target,accuracy);
        if(!NavigationArea.allowsPursuit(player))return null;
        Vec3 wanted=pursuitPosition(player);
        var goals=new java.util.HashSet<BlockPos>();goals.add(BlockPos.containing(wanted));
        // Include supported edge cells in the same search, rather than restarting A* around the player.
        for(int x=Mth.floor(wanted.x-1);x<=Mth.floor(wanted.x+1);x++)for(int z=Mth.floor(wanted.z-1);z<=Mth.floor(wanted.z+1);z++){
            if((x+.5-wanted.x)*(x+.5-wanted.x)+(z+.5-wanted.z)*(z+.5-wanted.z)>2.25)continue;
            var support=new BlockPos(x,Mth.floor(wanted.y-.01),z);
            var shape=level.getBlockState(support).getCollisionShape(level,support);
            if(shape.isEmpty()||Math.abs(support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y)-wanted.y)>.6)continue;
            Vec3 point=new Vec3(x+.5,support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y),z+.5);
            if(level.getBlockCollisions(mob,mob.getBoundingBox().move(point.subtract(mob.position())).deflate(.001)).iterator().hasNext())continue;
            if(level.clip(new ClipContext(point.add(0,.8,0),wanted.add(0,.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()!=HitResult.Type.MISS)continue;
            goals.add(BlockPos.containing(point));
        }
        return createPath(goals,accuracy);
    }
    @Override protected Path createPath(Set<BlockPos> targets,int margin,boolean above,int accuracy,float followRange){
        planningDeferred=false;
        if(!acquirePlanning()){planningDeferred=true;return path;}
        long started=System.nanoTime();
        try{
        // Most building/stair detours fit here; do not start each chase with a 512-block-wide region.
        float nearby=patrolPlanning?patrolRange:encounterPlanning?48:80;lastPlanningRange=nearby;
        Path local;
        super.setMaxVisitedNodesMultiplier(Math.min(visitBudgetMultiplier,patrolPlanning?.25f:encounterPlanning?.5f:1));
        try{local=super.createPath(targets,margin,above,accuracy,nearby);}
        finally{super.setMaxVisitedNodesMultiplier(visitBudgetMultiplier);}
        if(local!=null&&local.canReach())return local;
        if(patrolPlanning||encounterPlanning)return local; // Idle/hidden probes retry other nearby positions instead of a 256-block detour.
        if(mob.tickCount<nextExtendedSearch||!acquireExtendedSearch())return local;
        nextExtendedSearch=mob.tickCount+20+Math.floorMod(mob.getId(),10);
        Path current=path;path=null;
        try{lastPlanningRange=256;return super.createPath(targets,margin,above,accuracy,256);}
        finally{path=current;}
        }finally{planningBudget().nanos+=System.nanoTime()-started;}
    }
}
