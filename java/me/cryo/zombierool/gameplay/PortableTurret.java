package me.cryo.zombierool.gameplay;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Fixed damage, no round scaling, no player targets, and no power output. */
public final class PortableTurret {
    public static final float DAMAGE=6;public static final int RANGE=12;
    public static boolean visible(ServerLevel level,BlockPos pos,Vec3 start,Vec3 end){
        return level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null){@Override public VoxelShape getBlockShape(BlockState s,BlockGetter world,BlockPos at){return at.equals(pos)?Shapes.empty():super.getBlockShape(s,world,at);}}).getType()==HitResult.Type.MISS;
    }
    public static void fire(ServerLevel level,PortableEquipment.Device device,UUID owner){
        if(device.dying())return;var pos=device.getBlockPos();var origin=Vec3.atLowerCornerOf(pos).add(.5,1.25,.5);
        var targets=level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(RANGE),e->EquipmentEffects.enemy(e)&&e.distanceToSqr(origin)<=RANGE*RANGE);
        targets.sort(java.util.Comparator.comparingDouble(e->e.distanceToSqr(origin)));
        for(var target:targets){var aim=target.getBoundingBox().getCenter();if(!visible(level,pos,origin,aim))continue;
            var direction=aim.subtract(origin);device.aimYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));device.aimPitch=(float)Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));
            device.setChanged();level.sendBlockUpdated(pos,device.getBlockState(),device.getBlockState(),3);
            var player=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
            me.cryo.zombierool.block.system.MapDeviceSystem.portableTurretHit(target,player,DAMAGE);
            for(int i=1;i<=6;i++){var v=origin.lerp(aim,i/7.0);level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,v.x,v.y,v.z,1,0,0,0,0);}
            level.playSound(null,pos,net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation("zombierool:mg42_fire")),net.minecraft.sounds.SoundSource.BLOCKS,.65f,1.15f);
            me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnPortableTurretShot",pos.getX(),pos.getY(),pos.getZ(),target.getUUID().toString(),owner==null?"":owner.toString(),DAMAGE);return;
        }
    }
    private PortableTurret(){}
}
