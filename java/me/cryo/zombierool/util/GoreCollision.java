package me.cryo.zombierool.util;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Gore interacts with visible scenery, independently of map navigation barriers. */
public final class GoreCollision {
    public static boolean ignored(BlockState state) {
        return state.getBlock() instanceof me.cryo.zombierool.block.AbstractTechnicalBlock
            || state.getRenderShape()==RenderShape.INVISIBLE;
    }
    public static BlockHitResult clip(Level level,Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null) {
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos pos) {
                return ignored(state)?Shapes.empty():super.getBlockShape(state,world,pos);
            }
        });
    }
    /** Visible block collisions only: corpses, navigation markers and other debris do not trap gibs. */
    public static boolean clear(Entity entity,Vec3 at){
        var box=entity.getBoundingBox().move(at.subtract(entity.position())).deflate(1e-5);
        var volume=Shapes.create(box);
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            if(!entity.level().hasChunkAt(pos))return false;
            var state=entity.level().getBlockState(pos);if(ignored(state))continue;
            var shape=state.getCollisionShape(entity.level(),pos,CollisionContext.of(entity));
            if(!shape.isEmpty()&&Shapes.joinIsNotEmpty(volume,shape.move(pos.getX(),pos.getY(),pos.getZ()),BooleanOp.AND))return false;
        }
        return true;
    }
    /** Recover saved or newly spawned gibs inside a block without repeatedly accelerating into it. */
    public static boolean resolveEmbedded(Entity entity){
        Vec3 at=entity.position();if(clear(entity,at))return true;
        var box=entity.getBoundingBox();Vec3 best=null;double shortest=Double.POSITIVE_INFINITY;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            if(!entity.level().hasChunkAt(pos))continue;
            var state=entity.level().getBlockState(pos);if(ignored(state))continue;
            var shape=state.getCollisionShape(entity.level(),pos,CollisionContext.of(entity));
            for(var local:shape.toAabbs()){
                var wall=local.move(pos);if(!wall.intersects(box))continue;
                Vec3[] offsets={new Vec3(wall.minX-box.maxX-.002,0,0),new Vec3(wall.maxX-box.minX+.002,0,0),new Vec3(0,wall.minY-box.maxY-.002,0),new Vec3(0,wall.maxY-box.minY+.002,0),new Vec3(0,0,wall.minZ-box.maxZ-.002),new Vec3(0,0,wall.maxZ-box.minZ+.002)};
                for(var offset:offsets){double distance=offset.lengthSqr();if(distance<=2.25&&distance<shortest&&clear(entity,at.add(offset))){shortest=distance;best=at.add(offset);}}
            }
        }
        if(best==null)return false;entity.setPos(best);entity.setDeltaMovement(Vec3.ZERO);return true;
    }
    public static void move(Entity entity,Vec3 requested) {
        var level=entity.level();var box=entity.getBoundingBox();var area=box.expandTowards(requested).inflate(.001);
        var shapes=new ArrayList<VoxelShape>();
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(area.minX,area.minY,area.minZ),BlockPos.containing(area.maxX,area.maxY,area.maxZ))) {
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);if(ignored(state))continue;
            var shape=state.getCollisionShape(level,pos,CollisionContext.of(entity));
            if(!shape.isEmpty())shapes.add(shape.move(pos.getX(),pos.getY(),pos.getZ()));
        }
        double y=Shapes.collide(Direction.Axis.Y,box,shapes,requested.y);box=box.move(0,y,0);
        double x=requested.x,z=requested.z;
        if(Math.abs(x)<Math.abs(z)) {z=Shapes.collide(Direction.Axis.Z,box,shapes,z);box=box.move(0,0,z);x=Shapes.collide(Direction.Axis.X,box,shapes,x);}
        else {x=Shapes.collide(Direction.Axis.X,box,shapes,x);box=box.move(x,0,0);z=Shapes.collide(Direction.Axis.Z,box,shapes,z);}
        entity.setPos(entity.getX()+x,entity.getY()+y,entity.getZ()+z);
        entity.setOnGround(requested.y<0&&Math.abs(y-requested.y)>1e-7);
        entity.setDeltaMovement(Math.abs(x-requested.x)>1e-7?0:x,Math.abs(y-requested.y)>1e-7?0:y,Math.abs(z-requested.z)>1e-7?0:z);
    }
    private GoreCollision() {}
}
