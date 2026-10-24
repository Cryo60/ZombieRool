package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.system.TraversalSystem;
import net.minecraft.world.level.pathfinder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A directed graph edge connects the entry and exit, even across a solid wall. */
@Mixin(WalkNodeEvaluator.class)
public abstract class TraversalWalkNodeMixin extends NodeEvaluator {
    @Inject(method="getNeighbors",at=@At("RETURN"),cancellable=true)
    private void zombierool_traversal(Node[] neighbors,Node current,CallbackInfoReturnable<Integer> callback) {
        TraversalSystem.Route route=TraversalSystem.edge(this.mob,current);
        int count=callback.getReturnValue();
        int retained=0;
        for(int i=0;i<count;i++)if(!(route!=null&&route.anchor().kind()==TraversalSystem.Kind.WALL&&current.y-neighbors[i].y>1)&&me.cryo.zombierool.block.system.OneWayGate.allowed(this.mob,current,neighbors[i]))neighbors[retained++]=neighbors[i];
        count=retained;callback.setReturnValue(count);
        if(route==null || count>=neighbors.length)return;
        Node destination=getNode(route.destination().getX(),route.destination().getY(),route.destination().getZ());
        if(destination.closed||!me.cryo.zombierool.block.system.OneWayGate.allowed(this.mob,current,destination))return;
        destination.type=BlockPathTypes.WALKABLE;destination.costMalus=0;
        neighbors[count]=destination;callback.setReturnValue(count+1);
    }
}
