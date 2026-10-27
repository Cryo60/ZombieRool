package me.cryo.zombierool.gameplay;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Scripted encounters use authored navigation columns and never fall back to a visible spawn. */
public final class HiddenActorSpawn {
    private static final Map<ServerLevel,SearchBudget> SEARCHES=new WeakHashMap<>();
    private static final class SearchBudget {long tick=Long.MIN_VALUE,nanos;}
    public static boolean visibleTo(ServerPlayer player,AABB box){
        if(!player.isAlive()||player.isSpectator())return false;
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        boolean close=box.distanceToSqr(eye)<36;
        // A deliberately wide cone also covers high FOV and third-person cameras.
        for(double x:new double[]{box.minX,(box.minX+box.maxX)/2,box.maxX})for(double y:new double[]{box.minY+.1,(box.minY+box.maxY)/2,box.maxY-.1})for(double z:new double[]{box.minZ,(box.minZ+box.maxZ)/2,box.maxZ}){
            var point=new Vec3(x,y,z);var direction=point.subtract(eye);
            if(!close&&direction.normalize().dot(look)<-.35)continue;
            if(player.level().clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS)return true;
        }
        return false;
    }
    public static net.minecraft.world.entity.EntityDimensions dimensions(net.minecraft.nbt.CompoundTag profile){
        if(!profile.getString("baseEntity").isEmpty()){var size=NativeMapActors.type(profile.getString("baseEntity")).getDimensions();return net.minecraft.world.entity.EntityDimensions.fixed(Math.max(.8f,size.width),Math.max(2,size.height));}
        float scale=(float)profile.getDouble("scale");return net.minecraft.world.entity.EntityDimensions.fixed(Math.max(.8f,(float)profile.getDouble("width")*scale),Math.max(2,(float)profile.getDouble("height")*scale));
    }
    public static String spawn(ServerPlayer player,String profile,String marker,double min,double max){
        return spawn(player,profile,marker,min,max,false);
    }
    public static String spawn(ServerPlayer player,String profile,String marker,double min,double max,boolean visible){
        var level=player.serverLevel();var budget=SEARCHES.computeIfAbsent(level,k->new SearchBudget());if(budget.tick!=level.getGameTime()){budget.tick=level.getGameTime();budget.nanos=0;}if(budget.nanos>=6_000_000)return "";long started=System.nanoTime();
        try{return attempt(player,profile,marker,min,max,started+4_000_000,visible);}finally{budget.nanos+=System.nanoTime()-started;}
    }
    private static String attempt(ServerPlayer player,String profile,String marker,double min,double max,long deadline,boolean visible){
        var level=player.serverLevel();var data=MapActors.get(level);var definition=data.profile(profile);
        if(!WaveManager.isGameRunning()||WaveManager.isPausedByPlayer()||!MapActorBehavior.eligible(player)||definition.isEmpty()||!Double.isFinite(min)||!Double.isFinite(max)||min<4||max<min||max>64)return "";
        var dimensions=dimensions(definition);double width=dimensions.width,height=dimensions.height;
        var columns=new ArrayList<BlockPos>();var seen=new HashSet<Long>();
        for(var path:me.cryo.zombierool.config.WorldConfig.get(level).getPathPositionsNear(player.blockPosition(),max)){
            double dx=path.getX()+.5-player.getX(),dz=path.getZ()+.5-player.getZ();double d=dx*dx+dz*dz;
            if(d<min*min||d>max*max||!level.hasChunkAt(path)||!(level.getBlockState(path).getBlock() instanceof me.cryo.zombierool.block.PathBlock))continue;
            long key=BlockPos.asLong(path.getX(),0,path.getZ());if(seen.add(key))columns.add(path);
        }
        Collections.shuffle(columns,new Random(level.random.nextLong()));
        net.minecraft.world.entity.PathfinderMob probe=null;
        int attempts=0,routeChecks=0;
        for(var path:columns){if(++attempts>128||System.nanoTime()>=deadline)break;
            int bottom=Math.max(level.getMinBuildHeight(),(int)player.getY()-12),top=Math.min(level.getMaxBuildHeight()-2,(int)player.getY()+12);
            for(int step=0;step<28;step++){if(System.nanoTime()>=deadline)return "";int y=(int)Math.floor(player.getY())-1+(step%2==0?step/2:-(step+1)/2);if(y<bottom||y>top)continue;var floor=new BlockPos(path.getX(),y,path.getZ());var shape=level.getBlockState(floor).getCollisionShape(level,floor);if(shape.isEmpty())continue;
                var at=new Vec3(path.getX()+.5,y+shape.max(net.minecraft.core.Direction.Axis.Y),path.getZ()+.5);
                if(!NavigationArea.allows(level,BlockPos.containing(at)))continue;
                var box=new AABB(at.x-width/2,at.y,at.z-width/2,at.x+width/2,at.y+height,at.z+width/2);
                if(!level.noCollision(box)||(visible?!visibleTo(player,box):level.players().stream().anyMatch(p->visibleTo(p,box))))continue;
                // Validate before registering the actor, firing callbacks or counting it for the wave.
                // A path marker authorizes a column; it does not connect sealed floors in it.
                if(++routeChecks>4)return "";
                if(probe==null){
                    if(definition.getString("baseEntity").isEmpty()){
                        var actor=me.cryo.zombierool.init.ZombieroolModEntities.MAP_ACTOR.get().create(level);
                        if(actor==null)return "";
                        actor.configure(definition,at,marker);probe=actor;
                    }else{
                        probe=(net.minecraft.world.entity.PathfinderMob)NativeMapActors.type(definition.getString("baseEntity")).create(level);
                        if(probe==null)return "";
                        probe.getPersistentData().put("MapNativeProfile",definition.copy());
                    }
                }
                probe.setPos(at.x,at.y,at.z);probe.setOnGround(true);
                var navigation=probe.getNavigation();
                var route=navigation instanceof RouteNavigation nav?nav.createEncounterPath(player):navigation.createPath(player,0);
                if(navigation instanceof RouteNavigation nav&&nav.planningDeferred())return "";
                if(!RouteNavigation.reachesPlayer(route,player))continue;
                String uuid=data.spawn(level,profile,at,marker);
                if(!uuid.isEmpty()){
                    if(me.cryo.zombierool.scripting.LuaScriptManager.ready(level))me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnHiddenEnemySpawned",player.getUUID().toString(),uuid,profile,at.x,at.y,at.z);
                    return uuid;
                }
            }
        }
        return "";
    }
    private HiddenActorSpawn(){}
}
