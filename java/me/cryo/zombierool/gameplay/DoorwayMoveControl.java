package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;

/** An open door's leaf still occupies part of its cell. Aim inside the actual free opening. */
public final class DoorwayMoveControl extends MoveControl {
    public DoorwayMoveControl(Mob mob){super(mob);}
    @Override public void tick(){
        if(mob.isInWater()&&EnemyWaterMovement.swims(mob)){
            if(operation!=Operation.MOVE_TO&&operation!=Operation.JUMPING){mob.setYya(0);mob.setZza(0);return;}
            operation=Operation.WAIT;mob.setJumping(false);
            double dx=wantedX-mob.getX(),dy=wantedY-mob.getY(),dz=wantedZ-mob.getZ();
            var goal=BlockPos.containing(wantedX,wantedY,wantedZ);
            boolean exit=!mob.level().getFluidState(goal).is(net.minecraft.tags.FluidTags.WATER)&&mob.level().getFluidState(goal.below()).is(net.minecraft.tags.FluidTags.WATER)&&wantedY>mob.getY();
            // Carry the body over the rim before normal air gravity takes over.
            if(exit&&mob.getY()>wantedY-1.05){var velocity=mob.getDeltaMovement();mob.setDeltaMovement(velocity.x,Math.max(.3,velocity.y),velocity.z);}
            double horizontal=Math.sqrt(dx*dx+dz*dz),distance=Math.sqrt(horizontal*horizontal+dy*dy);
            float speed=(float)(speedModifier*mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            if(distance<.05){mob.setYya(0);mob.setZza(0);return;}
            if(horizontal>.01)mob.setYRot(rotlerp(mob.getYRot(),(float)(Math.atan2(dz,dx)*180/Math.PI)-90,25));
            mob.setSpeed(speed);mob.setZza((float)(speed*horizontal/distance));mob.setYya((float)(speed*dy/distance));
            return;
        }
        mob.setYya(0);
        if(mob.isInWater()&&(operation==Operation.MOVE_TO||operation==Operation.JUMPING)){
            operation=Operation.WAIT;
            double dx=wantedX-mob.getX(),dz=wantedZ-mob.getZ();
            mob.setJumping(false);
            if(dx*dx+dz*dz>.0025){
                float yaw=(float)(Math.atan2(dz,dx)*180/Math.PI)-90;
                mob.setYRot(rotlerp(mob.getYRot(),yaw,25));
                mob.setSpeed((float)(speedModifier*mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            }else mob.setZza(0);
        }else super.tick();
    }
    private boolean clear(Vec3 point){
        return !mob.level().getBlockCollisions(mob,mob.getBoundingBox().move(point.subtract(mob.position())).deflate(.0001)).iterator().hasNext();
    }
    private boolean clearAlong(Vec3 point){
        return clearBetween(mob.position(),point);
    }
    private boolean clearBetween(Vec3 start,Vec3 end){
        int steps=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.2));
        for(int i=1;i<=steps;i++)if(!clear(start.lerp(end,(double)i/steps)))return false;
        return true;
    }
    private boolean nearOpenDoor(Vec3 point){
        BlockPos feet=BlockPos.containing(point);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var state=mob.level().getBlockState(feet.offset(dx,0,dz));
            if(state.getBlock() instanceof DoorBlock&&state.getValue(DoorBlock.OPEN))return true;
        }
        return false;
    }
    @Override public void setWantedPosition(double x,double y,double z,double speed){
        Vec3 wanted=new Vec3(x,y,z);
        if((nearOpenDoor(wanted)||nearOpenDoor(mob.position()))&&!clearAlong(wanted)){
            Vec3 best=null;double shortest=Double.POSITIVE_INFINITY;
            for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++){
                Vec3 candidate=wanted.add(dx/16.0,0,dz/16.0);double distance=candidate.distanceToSqr(wanted);
                if(distance<shortest&&clearAlong(candidate)){shortest=distance;best=candidate;}
            }
            // Navigation may already have skipped the door node while the body touches
            // its leaf. Align sideways first; moving diagonally into it cannot recover.
            if(best==null)for(int step=1;step<=6&&best==null;step++)for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                Vec3 offset=new Vec3(direction.getStepX()*step/16.0,0,direction.getStepZ()*step/16.0);
                Vec3 aligned=mob.position().add(offset);
                if(clearAlong(aligned)&&clearBetween(aligned,wanted.add(offset))){best=aligned;break;}
            }
            if(best!=null)wanted=best;
        }
        super.setWantedPosition(wanted.x,wanted.y,wanted.z,speed);
    }
}
