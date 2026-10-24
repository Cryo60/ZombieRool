package me.cryo.zombierool.mixins;

import java.util.*;
import me.cryo.zombierool.block.system.TraversalSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boss links include travel distance and boarding cost so ordinary walking remains competitive. */
@Mixin(PathFinder.class)
public class TraversalPathCostMixin {
    @Unique private Mob zr$mob;
    @Unique private List<TraversalSystem.Route> zr$routes=List.of();
    @Unique private long zr$revision=-1;
    @Unique private net.minecraft.world.phys.Vec3[] zr$starts,zr$ends;
    @Unique private double[] zr$remaining;
    @Inject(method="findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;",at=@At("HEAD"))
    private void zr$begin(PathNavigationRegion region,Mob mob,Set<BlockPos> goals,float distance,int accuracy,float multiplier,CallbackInfoReturnable<Path> ci){
        zr$mob=mob;zr$routes=List.of();zr$revision=-1;zr$starts=null;zr$ends=null;zr$remaining=null;
    }
    @Unique private List<TraversalSystem.Route> zr$routes(){
        if(zr$mob==null)return List.of();
        // Entries may be unpacked by the node evaluator during this same search.
        long revision=TraversalSystem.revision(zr$mob.level());
        if(zr$revision!=revision){
            zr$revision=revision;
            var loaded=TraversalSystem.loaded(zr$mob.level());
            zr$remaining=null;
            var routes=new ArrayList<TraversalSystem.Route>();
            for(var a:loaded)if(a.ready()){
                if(a.accepts(zr$mob))routes.add(new TraversalSystem.Route(a,false));
                if(a.bidirectional&&a.kind()!=TraversalSystem.Kind.WALL&&a.accepts(zr$mob,true))routes.add(new TraversalSystem.Route(a,true));
            }
            zr$routes=routes;
            zr$starts=new net.minecraft.world.phys.Vec3[routes.size()];zr$ends=new net.minecraft.world.phys.Vec3[routes.size()];
            for(int i=0;i<routes.size();i++){zr$starts[i]=routes.get(i).start();zr$ends[i]=routes.get(i).end();}
        }
        return zr$routes;
    }
    @Inject(method="distance",at=@At("HEAD"),cancellable=true)
    private void zr$linkCost(Node from,Node to,CallbackInfoReturnable<Float> ci){
        if(zr$mob==null)return;
        var route=TraversalSystem.edge(zr$mob,from);
        if(route!=null&&route.destination().equals(to.asBlockPos()))ci.setReturnValue(me.cryo.zombierool.gameplay.RouteNavigation.traversalCost(zr$mob,route));
    }
    @Inject(method="getBestH",at=@At("RETURN"),cancellable=true)
    private void zr$linkHeuristic(Node node,Set<Target> goals,CallbackInfoReturnable<Float> ci){
        var routes=zr$routes();if(routes.isEmpty())return;
        // Euclidean distance overestimates after a cheap link. Relax the small link graph
        // backwards from the goals, including chains of different navigation methods.
        if(zr$remaining==null){
        double[] remaining=new double[routes.size()];
        for(int i=0;i<routes.size();i++){
            remaining[i]=Double.POSITIVE_INFINITY;
            for(Node target:goals)remaining[i]=Math.min(remaining[i],zr$ends[i].distanceTo(new net.minecraft.world.phys.Vec3(target.x+.5,target.y,target.z+.5)));
        }
        // Reverse Dijkstra: nonnegative travel costs, O(M^2), one pass per route.
        boolean[] settled=new boolean[routes.size()];
        for(int pass=0;pass<routes.size();pass++){
            int nearest=-1;
            for(int i=0;i<routes.size();i++)if(!settled[i]&&(nearest<0||remaining[i]<remaining[nearest]))nearest=i;
            if(nearest<0)break;settled[nearest]=true;
            for(int i=0;i<routes.size();i++)if(!settled[i])
                remaining[i]=Math.min(remaining[i],zr$ends[i].distanceTo(zr$starts[nearest])+me.cryo.zombierool.gameplay.RouteNavigation.traversalCost(zr$mob,routes.get(nearest))+remaining[nearest]);
        }
        zr$remaining=remaining;
        }
        double best=ci.getReturnValue();
        for(int i=0;i<routes.size();i++){
            double dx=node.x+.5-zr$starts[i].x,dy=node.y-zr$starts[i].y,dz=node.z+.5-zr$starts[i].z;
            best=Math.min(best,Math.sqrt(dx*dx+dy*dy+dz*dz)+me.cryo.zombierool.gameplay.RouteNavigation.traversalCost(zr$mob,routes.get(i))+zr$remaining[i]);
        }
        ci.setReturnValue((float)best);
    }
    @ModifyConstant(method="findPath(Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/world/level/pathfinder/Node;Ljava/util/Map;FIF)Lnet/minecraft/world/level/pathfinder/Path;",constant=@Constant(floatValue=1.5f))
    private float zr$accurateLinkSearch(float weight){return zr$routes().isEmpty()?weight:1f;}
}
