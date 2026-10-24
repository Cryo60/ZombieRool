package me.cryo.zombierool.mixins;
import net.minecraft.world.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
/** Preserve vanilla queries, then include model surfaces outside their anchor block. */
@Mixin(Level.class)
public abstract class VehicleWorldPhysicsMixin implements LevelAccessor {
 @Override public Iterable<VoxelShape> getBlockCollisions(Entity entity,AABB area){Iterable<VoxelShape> original=()->new BlockCollisions<>((Level)(Object)this,entity,area,false,(pos,shape)->shape);var extra=new java.util.ArrayList<VoxelShape>(me.cryo.zombierool.gameplay.VehicleDecorPhysics.collisions((Level)(Object)this,area));return extra.isEmpty()?original:com.google.common.collect.Iterables.concat(original,extra);}
 @Override public BlockHitResult clip(ClipContext context){
    BlockHitResult original=BlockGetter.traverseBlocks(context.getFrom(),context.getTo(),context,(ctx,pos)->{
        var block=getBlockState(pos);var a=clipWithInteractionOverride(ctx.getFrom(),ctx.getTo(),pos,ctx.getBlockShape(block,this,pos),block);var b=ctx.getFluidShape(getFluidState(pos),this,pos).clip(ctx.getFrom(),ctx.getTo(),pos);double da=a==null?Double.MAX_VALUE:ctx.getFrom().distanceToSqr(a.getLocation()),db=b==null?Double.MAX_VALUE:ctx.getFrom().distanceToSqr(b.getLocation());return da<=db?a:b;
    },ctx->{var direction=ctx.getFrom().subtract(ctx.getTo());return BlockHitResult.miss(ctx.getTo(),net.minecraft.core.Direction.getNearest(direction.x,direction.y,direction.z),net.minecraft.core.BlockPos.containing(ctx.getTo()));});
    return me.cryo.zombierool.gameplay.VehicleDecorPhysics.pick((Level)(Object)this,context.getFrom(),context.getTo(),original);
 }
}
