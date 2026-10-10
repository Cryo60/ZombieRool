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
        TraversalSystem.Anchor route=TraversalSystem.edge(this.mob,current);
        int count=callback.getReturnValue();
        if(route==null || count>=neighbors.length)return;
        Node destination=getNode(route.exit.getX(),route.exit.getY(),route.exit.getZ());
        if(destination.closed)return;
        destination.type=BlockPathTypes.WALKABLE;destination.costMalus=0;
        neighbors[count]=destination;callback.setReturnValue(count+1);
    }
}
